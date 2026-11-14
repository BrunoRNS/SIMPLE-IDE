package io.simpleide.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TerminalsTest {

  @Test
  void panelIsCreatedWithoutATerminalSession() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          assertThat(Terminals.panel()).isNotNull();
          assertThat(Terminals.isSupported()).isTrue();
        });
  }

  @Test
  void controllerRequiresAPanel() {
    assertThatNullPointerException().isThrownBy(() -> Terminals.controller(null));
  }
}
