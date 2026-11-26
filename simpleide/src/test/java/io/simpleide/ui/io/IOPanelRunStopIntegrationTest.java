package io.simpleide.ui.io;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;

/**
 * Tests the Stop button shown by the I/O tab while a program runs without debugging.
 *
 * <p>
 * A real child process is started through a language runner; the test presses the same
 * button the user does and checks that the process is killed, that the language reports
 * the manual interruption and that the panel returns to its idle state.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
class IOPanelRunStopIntegrationTest {

	@Test
	void stopsAProgramRunningWithoutDebugging() throws Exception {
		drainInput();
		IOPanel panel = new IOPanel();
		RunningLanguage language = new RunningLanguage();
		panel.beginRun(language);
		try {
			language.startManaged();
			await().atMost(30, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(panel.getOutputText()).contains("PROBE started"));
			assertThat(panel.getRunControls().isVisible()).isTrue();

			JButton stop = (JButton) panel.getRunControls().getComponent(0);
			SwingUtilities.invokeAndWait(stop::doClick);

			await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(panel.isExecutionActive()).isFalse());
			assertThat(panel.getOutputText()).contains("Process interrupted by the user.");
			assertThat(panel.getRunControls().isVisible()).isFalse();
		}
		finally {
			language.forceStop();
			panel.finishExecution();
		}
	}

	private static void drainInput() throws InterruptedException {
		while (Input.getInstance().get(0) != null) {
			// Drain input left by other tests.
		}
	}

	private static String javaExecutable() {
		String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe"
				: "java";
		return Path.of(System.getProperty("java.home"), "bin", executable).toString();
	}

	private static final class RunningLanguage extends AbstractLanguage {

		private RunningLanguage() {
			pb = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"),
					RunStopProbe.class.getName());
		}

	}

}
