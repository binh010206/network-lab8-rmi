package common;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Config {
  private final Properties values = new Properties();

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
}
