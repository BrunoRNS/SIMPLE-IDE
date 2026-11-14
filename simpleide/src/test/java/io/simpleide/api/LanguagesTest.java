package io.simpleide.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguagesTest {

  @TempDir Path tempDirectory;

  @Test
  void configIsAlwaysAvailable() {
    assertThat(Languages.config()).isNotNull();
  }

  @Test
  void extensionOfExtractsTheLowercaseExtension() {
    assertThat(Languages.extensionOf(Path.of("src", "Main.java"))).isEqualTo(".java");
    assertThat(Languages.extensionOf(tempDirectory.resolve("SCRIPT.PY"))).isEqualTo(".py");
    assertThat(Languages.extensionOf(tempDirectory.resolve("Makefile"))).isEmpty();
    assertThat(Languages.extensionOf(tempDirectory.resolve("archive.tar.gz"))).isEqualTo(".gz");
  }

  @Test
  void extensionOfRejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> Languages.extensionOf(null));
  }

  @Test
  void installAvailableIsIdempotent() {
    Languages.installAvailable();
    assertThat(Languages.installAvailable()).isSameAs(Languages.config());
  }

  @Test
  void createRejectsUnsupportedExtensions() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Languages.create(tempDirectory.resolve("notes.nolang")));
  }

  @Test
  void supportedExtensionsContainsTheDotAndIsSafeToReuse() {
    for (String extension : Languages.supportedExtensions()) {
      assertThat(extension).startsWith(".");
      assertThat(extension).isLowerCase();
    }
  }
}
