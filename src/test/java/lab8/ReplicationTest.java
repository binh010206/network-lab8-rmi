package lab8;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;
import common.*;
import java.nio.file.*;
import java.rmi.registry.LocateRegistry;
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
    assertThrows(
        IllegalArgumentException.class,
        () -> new StudentRecord("x", "Tên", "bad", "IT", 2).validate());
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

  private static Config config(Path dir, int n, String suffix) throws Exception {
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
            + "\nmongo.uri=mongodb://127.0.0.1:"
            + (n == 1 ? 27017 : 27018)
            + "/?serverSelectionTimeoutMS=2000\nmongo.database=lab8_test_"
            + suffix
            + "_"
            + n
            + "\n");
    return new Config(f.toString());
  }

  @Test
  void realMongoAndRmiRecovery() throws Exception {
    Assumptions.assumeTrue(
        Boolean.getBoolean("lab8.integration"),
        "Enable with -Dlab8.integration=true; requires two MongoDB instances on 27017/27018");
    String suffix = UUID.randomUUID().toString().replace("-", "");
    Path dir = Files.createTempDirectory("lab8-test");
    Config c1 = config(dir, 1, suffix), c2 = config(dir, 2, suffix);
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
      try (var mongo = MongoClients.create(c1.get("mongo.uri"))) {
        assertEquals(
            2,
            mongo.getDatabase(c1.get("mongo.database")).getCollection("students").countDocuments());
      }
      // Simulate receiver persisted but sender lost ACK: replay same event after restart.
      Change latest = restartedB.history().stream().max(Change::compareTo).orElseThrow();
      int count = restartedA.history().size();
      restartedA.receive(latest);
      assertEquals(count, restartedA.history().size());
    } finally {
      if (n1 != null) n1.close();
      if (n2 != null) n2.close();
      for (Config c : List.of(c1, c2)) {
        try (var mongo = MongoClients.create(c.get("mongo.uri"))) {
          mongo.getDatabase(c.get("mongo.database")).drop();
        }
      }
      Files.deleteIfExists(dir.resolve("server1.properties"));
      Files.deleteIfExists(dir.resolve("server2.properties"));
      Files.deleteIfExists(dir);
    }
  }
}
