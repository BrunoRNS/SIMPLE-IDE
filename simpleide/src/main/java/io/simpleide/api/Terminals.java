package io.simpleide.api;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;
import java.util.Objects;

/** Creates embedded BusyBox terminal components. */
public final class Terminals {

  private Terminals() {}

  /**
   * Creates an empty terminal panel.
   *
   * @return a new terminal panel
   */
  public static TerminalPanel panel() {
    return new TerminalPanel();
  }

  /**
   * Creates a controller that manages the terminal tabs of a panel.
   *
   * @param panel panel created by {@link #panel()}
   * @return a terminal controller
   */
  public static BusyBoxTerminalController controller(TerminalPanel panel) {
    Threads.master();
    return new BusyBoxTerminalController(Objects.requireNonNull(panel, "panel"));
  }

  /**
   * Returns whether the embedded BusyBox terminal is supported on this platform.
   *
   * @return {@code true} when a platform-specific BusyBox binary is available
   */
  public static boolean isSupported() {
    return LangPlatform.getCurrentPlatform() != LangPlatform.UNKNOWN;
  }
}
