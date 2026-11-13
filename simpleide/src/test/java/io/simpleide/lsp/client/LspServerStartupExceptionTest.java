package io.simpleide.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LspServerStartupExceptionTest {

  @Test
  void identifiesExitCode13AsAJvmCompatibilityFailure() {
    LspServerStartupException error = new LspServerStartupException(13);

    assertThat(error.getExitCode()).isEqualTo(13);
    assertThat(error.isJvmCompatibilityFailure()).isTrue();
    assertThat(error.getMessage())
        .contains("code 13")
        .containsIgnoringCase("known issue");
  }

  @Test
  void doesNotTreatOtherExitCodesAsAJvmCompatibilityFailure() {
    LspServerStartupException error = new LspServerStartupException(1);

    assertThat(error.getExitCode()).isEqualTo(1);
    assertThat(error.isJvmCompatibilityFailure()).isFalse();
    assertThat(error.getMessage())
        .contains("code 1")
        .doesNotContainIgnoringCase("known issue");
  }
}
