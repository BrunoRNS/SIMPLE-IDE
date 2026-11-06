package io.simpleide.frame.popup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class InputPopUPTest {

  @Test
  void enablesInputAndReturnsEnteredValueWhenDialogCloses() {
    AtomicReference<String> result = new AtomicReference<>();
    InputPopUP popup = new InputPopUP("Rename", "New name:", null, result::set);

    popup.jp.setInputValue("Main.java");
    popup.jp.setValue("Ok");
    popup.onDialogClosed();

    assertThat(popup.jp.getWantsInput()).isTrue();
    assertThat(result).hasValue("Main.java");
  }

  @Test
  void returnsNullWhenDialogClosesWithoutInput() {
    AtomicReference<String> result = new AtomicReference<>("existing");
    InputPopUP popup = new InputPopUP("Rename", "New name:", null, result::set);

    popup.onDialogClosed();

    assertThat(result).hasValue(null);
  }
}
