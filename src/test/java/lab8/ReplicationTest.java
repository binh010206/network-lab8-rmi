package lab8;

import static org.junit.jupiter.api.Assertions.*;

import common.*;
import dao.StudentDAO;
import java.nio.file.*;
import java.rmi.registry.LocateRegistry;
import java.sql.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import model.*;
import org.junit.jupiter.api.*;
import server.*;

public class ReplicationTest {
  private static StudentRecord student(double gpa) {
    return new StudentRecord("24IT027", "Trần Quang Bình", "binh@example.com", "CNTT", gpa);
  }

  @Test
  void validatesRealInput() {
    student(3.8).validate();
    assertThrows(IllegalArgumentException.class, () -> student(Double.NaN).validate());
    var invalidEmail =
        assertThrows(
            IllegalArgumentException.class,
            () -> new StudentRecord("1412313", "binh", "124@", "HGF", 2).validate());
    assertTrue(invalidEmail.getMessage().startsWith("Email"));
  }

  @Test
  void deterministicConflictAndTombstone() {
    var a = new Change("A:1", 4, "A", 999, false, student(1));
    var b = new Change("B:1", 4, "B", 1, true, student(2));
    assertEquals(b, DatabaseSyncImpl.materialize(List.of(a, b, a)).get("24IT027"));
    assertEquals(b, DatabaseSyncImpl.materialize(List.of(b, a)).get("24IT027"));
  }

  private static void await(BooleanSupplier condition) throws Exception {
    long until = System.nanoTime() + 15_000_000_000L;
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > until) fail("Replication did not converge in 15 seconds");
      Thread.sleep(100);
    }
  }

  private static Snapshot snap(IDatabaseSync s) {
    try {
      return s.snapshot();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Config config(Path dir, int n) throws Exception {
    Path f = dir.resolve("server" + n + ".properties");
    int registry = n == 1 ? 12099 : 12098;
    Files.writeString(
        f,
        "server.id=SERVER_"
            + n
            + "\nadvertise.host=127.0.0.1\nregistry.port="
            + registry
            + "\nobject.port="
            + (n == 1 ? 12101 : 12102)
            + "\npeer.host=127.0.0.1\npeer.registry.port="
            + (n == 1 ? 12098 : 12099)
            + "\ndb.url="
            + System.getenv()
                .getOrDefault("LAB8_TEST_BASE_URL", "jdbc:postgresql://127.0.0.1:5432/")
            + "lab8_test"
            + n
            + "\ndb.user=lab8_test"
            + n
            + "\ndb.password="
            + System.getenv("LAB8_TEST_PASSWORD")
            + "\n");
    return new Config(f.toString());
  }

  @Test
  void realPostgresAndRmiRecovery() throws Exception {
    Assumptions.assumeTrue(
        Boolean.getBoolean("lab8.integration"),
        "Enable with -Dlab8.integration=true; requires isolated lab8_test1/lab8_test2 PostgreSQL"
            + " databases and LAB8_TEST_PASSWORD");
    Path dir = Files.createTempDirectory("lab8-test");
    Config c1 = config(dir, 1), c2 = config(dir, 2);
    for (Config c : List.of(c1, c2)) {
      try (var dao = new StudentDAO(c)) {
        if (!dao.all().isEmpty())
          throw new IllegalStateException("Test schema must be empty: " + c.get("db.user"));
      }
    }
    ServerNode n1 = null, n2 = null;
    try {
      n1 = new ServerNode(c1);
      n2 = new ServerNode(c2);
      IDatabaseSync a =
          (IDatabaseSync) LocateRegistry.getRegistry("127.0.0.1", 12099).lookup("SyncService");
      IDatabaseSync b =
          (IDatabaseSync) LocateRegistry.getRegistry("127.0.0.1", 12098).lookup("SyncService");
      a.mutate("INSERT", student(3));
      await(() -> snap(b).rows().size() == 1 && snap(a).pending() == 0);
      b.mutate("UPDATE", student(4));
      await(() -> snap(a).rows().getFirst().student().gpa() == 4 && snap(b).pending() == 0);
      var event = b.history().getLast();
      a.receive(event);
      a.receive(event);
      assertEquals(2, a.history().size());
      n2.close();
      n2 = null;
      a.mutate("UPDATE", student(3.5));
      a.mutate(
          "INSERT", new StudentRecord("24IT999", "Demo Offline", "offline@example.com", "IT", 3));
      a.mutate("DELETE", student(4));
      assertEquals(3, snap(a).pending());
      n1.close();
      n1 = null;
      n1 = new ServerNode(c1);
      n2 = new ServerNode(c2);
      IDatabaseSync restartedA =
          (IDatabaseSync) LocateRegistry.getRegistry("127.0.0.1", 12099).lookup("SyncService");
      IDatabaseSync restartedB =
          (IDatabaseSync) LocateRegistry.getRegistry("127.0.0.1", 12098).lookup("SyncService");
      await(
          () ->
              snap(restartedB).rows().size() == 1
                  && snap(restartedB).rows().getFirst().student().id().equals("24IT999")
                  && snap(restartedA).pending() == 0);
      restartedB.mutate("INSERT", student(2.5));
      await(() -> snap(restartedA).rows().size() == 2 && snap(restartedB).pending() == 0);
      try (var dao = new StudentDAO(c1)) {
        assertEquals(2, dao.students().size());
        assertEquals(
            DatabaseSyncImpl.materialize(dao.all()).values().stream()
                .filter(e -> !e.deleted())
                .sorted(java.util.Comparator.comparing(e -> e.student().id()))
                .toList(),
            dao.students());
      }
      verifyConstraints(c1);
      // Simulate receiver persisted but sender lost ACK: replay same event after restart.
      Change latest = restartedB.history().stream().max(Change::compareTo).orElseThrow();
      int count = restartedA.history().size();
      restartedA.receive(latest);
      assertEquals(count, restartedA.history().size());

      // Re-export after an address change must advertise the new host and keep stored data.
      n1.close();
      n1 = null;
      n1 = new ServerNode(c1.with(Map.of("advertise.host", "127.0.0.2")));
      assertThrows(java.rmi.RemoteException.class, restartedA::snapshot);
      IDatabaseSync moved =
          (IDatabaseSync) LocateRegistry.getRegistry("127.0.0.2", 12099).lookup("SyncService");
      assertTrue(moved.toString().contains("127.0.0.2:12101"), moved.toString());
      assertEquals(count, moved.history().size());
      await(() -> snap(moved).peerOnline() && snap(restartedB).peerOnline());
      n1.close();
      n1 = null;
      n2.close();
      n2 = null;
      verifyConflictOrder(c1, c2);
    } finally {
      if (n1 != null) n1.close();
      if (n2 != null) n2.close();
      for (Config c : List.of(c1, c2)) {
        try (Connection connection =
                DriverManager.getConnection(
                    c.get("db.url"), c.get("db.user"), c.get("db.password"));
            Statement statement = connection.createStatement()) {
          statement.executeUpdate("DELETE FROM students");
          statement.executeUpdate("DELETE FROM sync_events");
        }
      }
      Files.deleteIfExists(dir.resolve("server1.properties"));
      Files.deleteIfExists(dir.resolve("server2.properties"));
      Files.deleteIfExists(dir);
    }
  }

  private static void verifyConflictOrder(Config c1, Config c2) {
    var student = new StudentRecord("CONFLICT", "Xung đột", "conflict@example.com", "IT", 3);
    var older = new Change("SERVER_1:conflict", 100, "SERVER_1", 999, false, student);
    var winner = new Change("SERVER_2:conflict", 100, "SERVER_2", 1, true, student);
    try (var first = new StudentDAO(c1);
        var second = new StudentDAO(c2)) {
      first.save(older, true);
      first.save(winner, true);
      second.save(winner, true);
      second.save(older, true);
      second.save(winner, true);
      assertEquals(first.students(), second.students());
      assertTrue(first.students().stream().noneMatch(e -> e.student().id().equals("CONFLICT")));
      assertEquals(
          1, second.all().stream().filter(e -> e.eventId().equals(winner.eventId())).count());
    }
  }

  private static void verifyConstraints(Config c) throws Exception {
    try (Connection connection =
            DriverManager.getConnection(c.get("db.url"), c.get("db.user"), c.get("db.password"));
        Statement statement = connection.createStatement()) {
      assertEquals(
          "23514",
          assertThrows(
                  SQLException.class, () -> statement.executeUpdate("UPDATE students SET gpa=9"))
              .getSQLState());
      assertEquals(
          "23502",
          assertThrows(
                  SQLException.class,
                  () -> statement.executeUpdate("UPDATE students SET full_name=NULL"))
              .getSQLState());
      assertEquals(
          "23514",
          assertThrows(
                  SQLException.class,
                  () -> statement.executeUpdate("UPDATE students SET email='invalid'"))
              .getSQLState());
      assertEquals(
          "23503",
          assertThrows(
                  SQLException.class,
                  () ->
                      statement.executeUpdate(
                          "UPDATE students SET event_id='MISSING:ID' WHERE student_id='24IT027'"))
              .getSQLState());
      assertEquals(
          "23505",
          assertThrows(
                  SQLException.class,
                  () ->
                      statement.executeUpdate(
                          "INSERT INTO sync_events SELECT * FROM sync_events LIMIT 1"))
              .getSQLState());
    }
    // Fail after inserting the event: a student-table failure must roll back the event as well.
    try (Connection connection =
            DriverManager.getConnection(c.get("db.url"), c.get("db.user"), c.get("db.password"));
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE OR REPLACE FUNCTION lab8_test_reject() RETURNS trigger LANGUAGE plpgsql AS $$"
              + " BEGIN IF NEW.student_id='FAIL_TX' THEN RAISE EXCEPTION 'Rollback test'; END IF;"
              + " RETURN NEW; END $$");
      statement.execute(
          "CREATE TRIGGER lab8_test_reject BEFORE INSERT ON students FOR EACH ROW EXECUTE FUNCTION"
              + " lab8_test_reject()");
      try (var dao = new StudentDAO(c)) {
        int before = dao.all().size();
        var event =
            new Change(
                "SERVER_1:rollback-test",
                99,
                "SERVER_1",
                1,
                false,
                new StudentRecord("FAIL_TX", "Rollback", "rollback@example.com", "IT", 3));
        assertThrows(IllegalStateException.class, () -> dao.save(event, false));
        assertEquals(before, dao.all().size());
      } finally {
        statement.execute("DROP TRIGGER lab8_test_reject ON students");
        statement.execute("DROP FUNCTION lab8_test_reject()");
      }
    }
  }
}
