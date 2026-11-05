package common;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Config {
  private final Properties values = new Properties();

  private Config(Properties source) {
    values.putAll(source);
  }

  public Config(String path) throws IOException {
    try (var in = Files.newBufferedReader(Path.of(path))) {
      values.load(in);
    }
  }

  public String get(String key) {
    String value = values.getProperty(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Thiếu cấu hình: " + key);
    return value.trim();
  }

  public int number(String key) {
    return Integer.parseInt(get(key));
  }

  public String getOrDefault(String key, String fallback) {
    return values.getProperty(key, fallback).trim();
  }

  public Config with(Map<String, String> changes) {
    Config result = new Config(values);
    result.values.putAll(changes);
    return result;
  }

  public Config without(String... keys) {
    Config result = new Config(values);
    for (String key : keys) result.values.remove(key);
    return result;
  }

  public void save(Path path) throws IOException {
    Path target = path.toAbsolutePath();
    Path temp = Files.createTempFile(target.getParent(), "network-", ".tmp");
    try {
      try (var out = Files.newBufferedWriter(temp)) {
        values.store(out, "Local machine settings");
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
