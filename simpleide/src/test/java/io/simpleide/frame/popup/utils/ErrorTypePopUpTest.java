package io.simpleide.frame.popup.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ErrorTypePopUpTest {
  @Test
  public void providesExpectedErrorTypesAndDescriptions() {
    assertThat(ErrorTypePopUp.values())
        .containsExactly(
            ErrorTypePopUp.RUNTIME_ERROR,
            ErrorTypePopUp.COMPILE_ERROR,
            ErrorTypePopUp.LINKER_ERROR,
            ErrorTypePopUp.INTERNAL_ERROR,
            ErrorTypePopUp.FATAL_ERROR);

    assertThat(ErrorTypePopUp.RUNTIME_ERROR).hasToString("Runtime error");
    assertThat(ErrorTypePopUp.COMPILE_ERROR).hasToString("Compile error");
    assertThat(ErrorTypePopUp.LINKER_ERROR).hasToString("Linker error");
    assertThat(ErrorTypePopUp.INTERNAL_ERROR).hasToString("Internal error");
    assertThat(ErrorTypePopUp.FATAL_ERROR).hasToString("Fatal error");
  }
}
