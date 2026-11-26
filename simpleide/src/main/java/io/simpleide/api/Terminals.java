package io.simpleide.api;

import java.util.Objects;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;

/**
 * Creates embedded BusyBox terminal components.
 *
 * <p>
 * Creating a controller initializes the shared thread manager. The panel itself is a
 * regular Swing component and can be mounted in any container.
 *
 * <p>
 * Covered by {@code TerminalsTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/terminal.md">docs/api/terminal.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class Terminals {

	private Terminals() {
	}

	/**
	 * Creates an empty terminal panel.
	 * @return a new terminal panel
	 */
	public static TerminalPanel panel() {
		return new TerminalPanel();
	}

	/**
	 * Creates a controller that manages the terminal tabs of a panel. New sessions start
	 * in the user's home directory, or in the application startup directory if the home
	 * directory is inaccessible.
	 * @param panel panel created by {@link #panel()}
	 * @return a terminal controller
	 */
	public static BusyBoxTerminalController controller(TerminalPanel panel) {
		Threads.master();
		return new BusyBoxTerminalController(Objects.requireNonNull(panel, "panel"));
	}

	/**
	 * Returns whether the embedded BusyBox terminal is supported on this platform.
	 * @return {@code true} when a platform-specific BusyBox binary is available
	 */
	public static boolean isSupported() {
		return LangPlatform.getCurrentPlatform() != LangPlatform.UNKNOWN;
	}

}
