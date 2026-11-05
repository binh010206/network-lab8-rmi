package dao;

import common.Config;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import model.*;
import org.postgresql.ds.PGSimpleDataSource;

/** Each event and its student state are committed together in one PostgreSQL transaction. */
public final class StudentDAO implements AutoCloseable {
  private static final String COLUMNS =
      "event_id, version_no, origin, created_at, deleted, student_id, full_name, email, major, gpa";
  private final PGSimpleDataSource source;

  public StudentDAO(Config config) {
    try {
      source = new PGSimpleDataSource();
      source.setURL(config.get("db.url"));
      source.setUser(config.get("db.user"));
      String password = System.getenv("LAB8_DB_PASSWORD");
      if (password == null || password.isBlank()) password = config.getOrDefault("db.password", "");
      if (password.isBlank())
        throw new IllegalArgumentException(
            "Chưa cấu hình PostgreSQL. Chạy scripts/setup-postgres.ps1 cho máy này.");
      source.setPassword(password);
      source.setConnectTimeout(3);
      source.setSocketTimeout(5);
      try (Connection connection = open();
          InputStream script = StudentDAO.class.getResourceAsStream("/schema.sql")) {
        if (script == null) throw new IOException("Thiếu schema.sql");
        String sql = new String(script.readAllBytes(), StandardCharsets.UTF_8);
        for (String ddl : sql.split("(?m)^-- statement\\s*$")) {
          try (Statement statement = connection.createStatement()) {
            statement.execute(ddl.trim());
          }
        }
      }
    } catch (SQLException | IOException e) {
      throw new IllegalStateException(
          "Không kết nối/khởi tạo được PostgreSQL: " + e.getMessage(), e);
    }
  }

  private Connection open() throws SQLException {
    return source.getConnection();
  }

  public synchronized void save(Change event, boolean acked) {
    // Retry only a uniqueness race between two connections inserting the same student.
    for (int attempt = 0; attempt < 3; attempt++) {
      try (Connection connection = open()) {
        connection.setAutoCommit(false);
        try {
          try (PreparedStatement exists =
              connection.prepareStatement("SELECT event_id FROM sync_events WHERE event_id=?")) {
            exists.setString(1, event.eventId());
            try (ResultSet result = exists.executeQuery()) {
              if (result.next()) {
                connection.rollback();
                return;
              }
            }
          }
          try (PreparedStatement insert =
              connection.prepareStatement(
                  "INSERT INTO sync_events ("
                      + COLUMNS
                      + ", acked) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            bind(insert, event);
            insert.setInt(11, acked ? 1 : 0);
            insert.executeUpdate();
          }
          Change previous = null;
          try (PreparedStatement select =
              connection.prepareStatement(
                  "SELECT " + COLUMNS + " FROM students WHERE student_id=? FOR UPDATE")) {
            select.setString(1, event.student().id());
            try (ResultSet result = select.executeQuery()) {
              if (result.next()) previous = change(result);
            }
          }
          if (previous == null) {
            try (PreparedStatement insert =
                connection.prepareStatement(
                    "INSERT INTO students (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)")) {
              bind(insert, event);
              insert.executeUpdate();
            }
          } else if (event.compareTo(previous) > 0) {
            try (PreparedStatement update =
                connection.prepareStatement(
                    "UPDATE students SET event_id=?, version_no=?, origin=?, created_at=?,"
                        + " deleted=?, student_id=?, full_name=?, email=?, major=?, gpa=? WHERE"
                        + " student_id=?")) {
              bind(update, event);
              update.setString(11, event.student().id());
              update.executeUpdate();
            }
          }
          connection.commit();
          return;
        } catch (SQLException e) {
          connection.rollback();
          if (!"23505".equals(e.getSQLState()) || attempt == 2) throw e;
        } catch (RuntimeException e) {
          connection.rollback();
          throw e;
        }
      } catch (SQLException e) {
        throw failure(e);
      }
    }
  }

  private static void bind(PreparedStatement statement, Change event) throws SQLException {
    var student = event.student();
    statement.setString(1, event.eventId());
    statement.setLong(2, event.version());
    statement.setString(3, event.origin());
    statement.setLong(4, event.createdAt());
    statement.setInt(5, event.deleted() ? 1 : 0);
    statement.setString(6, student.id());
    statement.setString(7, student.name());
    statement.setString(8, student.email());
    statement.setString(9, student.major());
    statement.setBigDecimal(10, BigDecimal.valueOf(student.gpa()));
  }

  public synchronized void acknowledge(String id) {
    try (Connection connection = open();
        PreparedStatement statement =
            connection.prepareStatement("UPDATE sync_events SET acked=1 WHERE event_id=?")) {
      statement.setString(1, id);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  public synchronized List<Change> all() {
    return read("SELECT " + COLUMNS + " FROM sync_events ORDER BY version_no, event_id", null);
  }

  public synchronized List<Change> students() {
    return read("SELECT " + COLUMNS + " FROM students WHERE deleted=0 ORDER BY student_id", null);
  }

  public synchronized List<Change> pending(String origin) {
    return read(
        "SELECT "
            + COLUMNS
            + " FROM sync_events WHERE origin=? AND acked=0 ORDER BY version_no, event_id",
        origin);
  }

  private List<Change> read(String sql, String origin) {
    try (Connection connection = open();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      if (origin != null) statement.setString(1, origin);
      try (ResultSet result = statement.executeQuery()) {
        List<Change> rows = new ArrayList<>();
        while (result.next()) rows.add(change(result));
        return rows;
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  private static Change change(ResultSet row) throws SQLException {
    return new Change(
        row.getString("event_id"),
        row.getLong("version_no"),
        row.getString("origin"),
        row.getLong("created_at"),
        row.getInt("deleted") == 1,
        new StudentRecord(
            row.getString("student_id"),
            row.getString("full_name"),
            row.getString("email"),
            row.getString("major"),
            row.getDouble("gpa")));
  }

  public synchronized long countPending(String origin) {
    try (Connection connection = open();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM sync_events WHERE origin=? AND acked=0")) {
      statement.setString(1, origin);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  private static IllegalStateException failure(SQLException e) {
    return new IllegalStateException("PostgreSQL: " + e.getMessage(), e);
  }

  public void close() {
    /* Every operation closes its own JDBC connection. */
  }
}
