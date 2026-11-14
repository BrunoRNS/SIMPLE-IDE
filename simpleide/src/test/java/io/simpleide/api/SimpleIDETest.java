package io.simpleide.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimpleIDETest {

  @Test
  void versionMatchesThePackagedPropertiesFile() throws Exception {
    java.util.Properties properties = new java.util.Properties();
    try (java.io.InputStream stream = getClass().getResourceAsStream("/simpleide.properties")) {
      properties.load(stream);
    }
    assertThat(SimpleIDE.version()).isEqualTo(properties.getProperty("version"));
  }

  @Test
  void threadsFacadeIsExposed() {
    assertThat(SimpleIDE.threads()).isNotNull();
  }
}
