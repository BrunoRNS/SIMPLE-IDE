package io.simpleide;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class SimpleIDEVersion {

  public static final String VERSION = loadVersion();

  private SimpleIDEVersion() {}

  private static String loadVersion() {
    Properties properties = new Properties();
    try (InputStream input = SimpleIDEVersion.class.getResourceAsStream("/simpleide.properties")) {
      if (input == null) {
        throw new IllegalStateException("Missing resource: /simpleide.properties");
      }
      properties.load(input);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load /simpleide.properties", e);
    }

    String version = properties.getProperty("version");
    if (version == null || version.isBlank()) {
      throw new IllegalStateException("Missing version in /simpleide.properties");
    }
    return version;
  }
}
