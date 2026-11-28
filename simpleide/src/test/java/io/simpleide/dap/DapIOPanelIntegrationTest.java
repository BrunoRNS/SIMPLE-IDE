package io.simpleide.dap;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.io.Output;
import io.simpleide.ui.io.IOPanel;

/**
 * Integration test for the debug buttons shown by the I/O tab.
 *
 * <p>
 * A real {@link FakeDapServer} process backs the session, the panel is bound to it, and
 * every button is clicked through {@link AbstractButton#doClick()}. The assertions read
 * the adapter echo, so a button that is wired to the wrong request, or not wired at all,
 * fails the test.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("debug")
class DapIOPanelIntegrationTest {

	@TempDir
	Path tempDirectory;

	private final List<String> published = new CopyOnWriteArrayList<>();

	private final List<String> events = new CopyOnWriteArrayList<>();

	private AutoCloseable outputSubscription;

	private DapSession session;

	private IOPanel panel;

	@BeforeEach
	void subscribeToOutput() {
		published.clear();
		events.clear();
		outputSubscription = Output.getInstance()
			.addListener(line -> published.add("[" + line.type() + "] " + line.text()));
	}

	@AfterEach
	void cleanUp() throws Exception {
		if (session != null) {
			session.close();
			session = null;
		}
		outputSubscription.close();
	}

	@Test
	void everyDebugButtonSendsItsRequestToTheAdapter() throws Exception {
		session = startSession();
		await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("stopped:breakpoint"));

		SwingUtilities.invokeAndWait(() -> {
			panel = new IOPanel();
			panel.beginDebug(null);
			panel.setSession(session);
		});
		assertThat(panel.getDebugControls().getComponentCount()).isEqualTo(6);

		clickButton("Step Over");
		awaitOutput("{CONTROL} next");

		clickButton("Step Into");
		awaitOutput("{CONTROL} stepIn");

		clickButton("Step Out");
		awaitOutput("{CONTROL} stepOut");

		clickButton("Pause");
		awaitOutput("{CONTROL} pause");

		clickButton("Continue");
		awaitOutput("{CONTROL} continue");

		clickButton("Stop");
		await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("terminated"));
		awaitOutput("[OUTPUT] " + Output.FINISHED);

		SwingUtilities.invokeAndWait(() -> panel.finishExecution());
		assertThat(panel.isExecutionActive()).isFalse();
		assertThat(panel.isDebugMode()).isFalse();
	}

	private void clickButton(String label) throws Exception {
		AbstractButton button = findButton(panel.getDebugControls(), label);
		assertThat(button).as("debug button %s", label).isNotNull();
		SwingUtilities.invokeAndWait(button::doClick);
	}

	private static AbstractButton findButton(Component component, String label) {
		if (component instanceof AbstractButton button && label.equals(button.getText())) {
			return button;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				AbstractButton found = findButton(child, label);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private void awaitOutput(String fragment) {
		await().atMost(20, TimeUnit.SECONDS)
			.untilAsserted(() -> assertThat(published).as("output lines").anyMatch(line -> line.contains(fragment)));
	}

	private DapSession startSession() throws Exception {
		DapAdapter adapter = new DapAdapter("fake", "Fake debug adapter", "1.0.0", "fake", javaExecutable(),
				List.of("-cp", classpath(), FakeDapServer.class.getName()), List.of(), "stdio", Map.of(), Map.of());
		DapSession started = new DapSession(adapter, tempDirectory, program(), new DapSession.Listener() {
			@Override
			public void onStopped(int threadId, String reason) {
				events.add("stopped:" + reason);
			}

			@Override
			public void onTerminated() {
				events.add("terminated");
			}
		});
		started.start(List.of(2));
		return started;
	}

	private Path program() throws IOException {
		Path program = tempDirectory.resolve("program.js");
		Files.writeString(program, "console.log('one');\nconsole.log('two');\n");
		return program;
	}

	private static String javaExecutable() {
		String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe"
				: "java";
		return Path.of(System.getProperty("java.home"), "bin", executable).toString();
	}

	private static String classpath() {
		return System.getProperty("java.class.path");
	}

}
