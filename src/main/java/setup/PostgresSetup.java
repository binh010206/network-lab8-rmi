package setup;

import common.Config;
import dao.StudentDAO;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import org.postgresql.ds.PGSimpleDataSource;

/** One-time provisioning: each server owns its local database and uses a non-admin login. */
public final class PostgresSetup {
  public static void main(String[] args) throws Exception {
    String node = args.length == 0 ? "1" : args[0];
    if (!Set.of("1", "2", "test").contains(node))
      throw new IllegalArgumentException("Node: 1, 2 or test");
    String adminUrl = args.length > 1 ? args[1] : "jdbc:postgresql://127.0.0.1:5432/postgres";
    String adminPassword = System.getenv("LAB8_PG_ADMIN_PASSWORD");
    if (adminPassword == null || adminPassword.isBlank()) {
      if (System.console() == null)
        throw new IllegalArgumentException(
            "Run scripts/setup-postgres.ps1 to enter the postgres password privately.");
      adminPassword = new String(System.console().readPassword("PostgreSQL postgres password: "));
    }
    var source = new PGSimpleDataSource();
    source.setURL(adminUrl);
    source.setUser(System.getenv().getOrDefault("LAB8_PG_ADMIN_USER", "postgres"));
    source.setPassword(adminPassword);
    source.setConnectTimeout(3);
    source.setSocketTimeout(30);
    try (Connection admin = source.getConnection()) {
      if (node.equals("test")) {
        String password = System.getenv("LAB8_TEST_PASSWORD");
        validatePassword(password);
        for (int n : List.of(1, 2)) {
          String name = "lab8_test" + n;
          createRole(admin, name, password);
          createDatabase(admin, name);
          try (var ignored = new StudentDAO(config(adminUrl, name, password))) {}
        }
        System.out.println("PostgreSQL test databases ready.");
        return;
      }
      Path local = Path.of("config/server" + node + ".local.properties");
      Config previous =
          new Config(
              Files.exists(local) ? local.toString() : "config/server" + node + ".properties");
      String name = "lab8_server" + node;
      boolean exists = roleExists(admin, name);
      String password = previous.getOrDefault("db.password", "");
      if (exists
          && (password.isBlank()
              || !previous.getOrDefault("db.url", "").startsWith("jdbc:postgresql:")
              || !previous.getOrDefault("db.user", "").equals(name)))
        throw new IllegalStateException(
            "Role "
                + name
                + " already exists. Restore its matching local configuration; no password was"
                + " changed.");
      if (!exists) {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        password = "Lab8_" + HexFormat.of().formatHex(random);
      }
      Config database = config(adminUrl, name, password);
      Config saved =
          previous
              .without("mongo.uri", "mongo.database")
              .with(
                  Map.of(
                      "db.url", database.get("db.url"), "db.user", name, "db.password", password));
      // Persist generated credentials first, so setup can safely retry after partial failure.
      saved.save(local);
      createRole(admin, name, password);
      createDatabase(admin, name);
      try (var dao = new StudentDAO(saved)) {
        System.out.println("PostgreSQL ready: " + name + " | students=" + dao.students().size());
      }
      System.out.println("Saved local configuration: " + local + " (excluded from Git)");
    }
  }

  private static Config config(String adminUrl, String name, String password) throws Exception {
    var source = new PGSimpleDataSource();
    source.setURL(adminUrl);
    source.setDatabaseName(name);
    return new Config("config/server1.properties")
        .with(Map.of("db.url", source.getURL(), "db.user", name, "db.password", password));
  }

  private static void validateName(String name) {
    if (!name.matches("lab8_(server|test)[12]"))
      throw new IllegalArgumentException("Invalid database name");
  }

  private static void validatePassword(String password) {
    if (password == null || !password.matches("[A-Za-z0-9_!]{12,100}"))
      throw new IllegalArgumentException("Set LAB8_TEST_PASSWORD (12+ letters/digits/_/!)");
  }

  private static boolean roleExists(Connection admin, String name) throws SQLException {
    try (var query = admin.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")) {
      query.setString(1, name);
      try (var result = query.executeQuery()) {
        return result.next();
      }
    }
  }

  private static void createRole(Connection admin, String name, String password)
      throws SQLException {
    validateName(name);
    validatePassword(password);
    if (roleExists(admin, name)) return;
    // Only fixed identifiers and generated/validated passwords reach this DDL.
    try (var statement = admin.createStatement()) {
      statement.execute(
          "CREATE ROLE "
              + name
              + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '"
              + password
              + "'");
    }
  }

  private static void createDatabase(Connection admin, String name) throws SQLException {
    validateName(name);
    try (var query =
        admin.prepareStatement("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=?")) {
      query.setString(1, name);
      try (var result = query.executeQuery()) {
        if (result.next()) {
          if (!name.equals(result.getString(1)))
            throw new IllegalStateException("Existing database has another owner: " + name);
          return;
        }
      }
    }
    try (var statement = admin.createStatement()) {
      statement.execute(
          "CREATE DATABASE " + name + " OWNER " + name + " ENCODING 'UTF8' TEMPLATE template0");
      statement.execute("REVOKE CONNECT, TEMPORARY ON DATABASE " + name + " FROM PUBLIC");
    }
  }
}
