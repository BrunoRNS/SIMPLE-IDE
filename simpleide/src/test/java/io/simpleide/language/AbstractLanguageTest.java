package io.simpleide.language;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import io.simpleide.language.exceptions.ProcessAlreadyExistsException;
import io.simpleide.language.io.Output;
import io.simpleide.language.io.holder.InputHolder;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * Tests {@link io.simpleide.language.AbstractLanguage}.
 */
class AbstractLanguageTest {

	private Logger languageLogger;

	private ThreadSafeListAppender logCapture;

	@BeforeEach
	@SuppressWarnings("unused")
	void captureLanguageLogs() throws InterruptedException {
		while (Output.getInstance().get(0) != null) {
			// Clear output left by a previous test.
		}
		while (InputHolder.INSTANCE.get(0) != null) {
			// Clear input left by a previous test.
		}
		languageLogger = (Logger) LoggerFactory.getLogger(AbstractLanguage.class);
		logCapture = new ThreadSafeListAppender();
		logCapture.setContext(languageLogger.getLoggerContext());
		logCapture.start();
		languageLogger.addAppender(logCapture);
	}

	@AfterEach
	@SuppressWarnings("unused")
	void stopCapturingLanguageLogs() {
		languageLogger.detachAppender(logCapture);
		logCapture.stop();
	}

	@Test
	void exposesLanguageNamesAndStartsProcessThroughThreadMaster() throws InterruptedException {
		TestLanguage language = new TestLanguage("success");
		RunnableNode node = language.startManaged();
		awaitOutput("child output");
		language.awaitExitHandler();

		assertEquals("Test language", language.getLangFriendly());
		assertEquals("test", language.getLangConstant());
		assertTrue(ThreadMaster.getInstance().showTree().containsKey(node.getId()));
		assertTrue(logCapture.messages().contains("[OUTPUT] child output"));
		assertEquals("child output", Output.getInstance().get(1));
	}

	@Test
	void executesProcessStepsInOrderAndStopsAfterFailure() throws InterruptedException {
		TestLanguage language = new TestLanguage("chain-success");

		language.run();
		language.awaitExitHandler();

		assertEquals("child output", Output.getInstance().get(1));
		assertEquals("second-stage output", Output.getInstance().get(1));
		assertTrue(logCapture.messages().contains("[IDE] Process completed successfully."));

		TestLanguage failingLanguage = new TestLanguage("chain-failure");
		failingLanguage.run();
		failingLanguage.awaitExitHandler();

		assertEquals("child output", Output.getInstance().get(1));
		assertFalse(logCapture.messages().contains("[OUTPUT] should-not-run"));
		assertTrue(logCapture.messages().contains("[OUTPUT] RUNTIME ERROR: 7"));
	}

	@Test
	void notifiesExecutionListenersWhenNoCommandIsConfigured() {
		AbstractLanguage language = new AbstractLanguage() {
		};
		java.util.concurrent.atomic.AtomicInteger notifications = new java.util.concurrent.atomic.AtomicInteger();
		language.addExecutionListener(notifications::incrementAndGet);

		language.run();
		language.run();

		assertEquals(2, notifications.get());
	}

	@Test
	void capturesOutputAndSuccessfulExit() throws InterruptedException {
		TestLanguage language = new TestLanguage("success");

		language.run();
		language.awaitExitHandler();

		assertTrue(logCapture.messages().contains("[OUTPUT] child output"));
		assertTrue(logCapture.messages().contains("[IDE] Process completed successfully."));
		assertEquals("child output", Output.getInstance().get(1));
	}

	@Test
	void capturesOutputAndReportsNonzeroExit() throws InterruptedException {
		TestLanguage language = new TestLanguage("failure");

		language.run();
		language.awaitExitHandler();

		assertTrue(logCapture.messages().contains("[OUTPUT] child output"));
		assertTrue(logCapture.messages().contains("[OUTPUT] RUNTIME ERROR: 7"));
		assertTrue(logCapture.messages().contains("[Runtime Error] The developer's program broke with code: 7"));
		assertEquals("child output", Output.getInstance().get(1));
	}

	@Test
	void forwardsInputFromInputHolderToRunningProcess() throws InterruptedException {
		TestLanguage language = new TestLanguage("input");
		language.startManaged();

		awaitOutput("ready");
		InputHolder.INSTANCE.set("user input");
		awaitOutput("received:user input");
		language.awaitExitHandler();

		assertEquals("ready", Output.getInstance().get(1));
		assertEquals("received:user input", Output.getInstance().get(1));
	}

	@Test
	void reportsWhenTheProcessWaitsForStandardInput() throws Exception {
		List<String> published = new CopyOnWriteArrayList<>();
		TestLanguage language = new TestLanguage("input");
		try (AutoCloseable subscription = Output.getInstance().addListener(line -> published.add(line.text()))) {
			language.startManaged();

			awaitOutput("ready");
			await().atMost(15, TimeUnit.SECONDS).until(() -> published.contains(Output.WAITING_FOR_STDIN));

			InputHolder.INSTANCE.set("user input");
			awaitOutput("received:user input");
			language.awaitExitHandler();
		}
	}

	@Test
	void rejectsAnotherProcessWhileOneIsRunningAndCanStopIt() throws InterruptedException {
		TestLanguage language = new TestLanguage("wait");
		language.startManaged();

		try {
			awaitOutput("ready");
			assertEquals("ready", Output.getInstance().get(1));
			assertThatThrownBy(language::run).isInstanceOfSatisfying(ProcessAlreadyExistsException.class,
					exception -> assertSame(language.getProcess(), exception.getActualProcess()));
			language.forceStop();
			language.awaitExitHandler();
			assertTrue(logCapture.messages().contains("[IDE] Process manually interrupted by the user."));
			assertFalse(logCapture.messages().stream().anyMatch(message -> message.contains("Runtime Error")));
		}
		finally {
			if (language.getProcess() != null && language.getProcess().isAlive()) {
				language.forceStop();
				language.awaitExitHandler();
			}
		}
	}

	private void awaitOutput(String output) {
		await("Expected logged process output: " + output).atMost(15, TimeUnit.SECONDS)
			.pollInterval(10, TimeUnit.MILLISECONDS)
			.until(() -> logCapture.messages().stream().anyMatch(message -> message.contains(output)));
	}

	private static final class ThreadSafeListAppender extends ListAppender<ILoggingEvent> {

		@Override
		protected synchronized void append(ILoggingEvent event) {
			super.append(event);
		}

		private synchronized List<String> messages() {
			return list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		}

	}

	private static final class TestLanguage extends AbstractLanguage {

		private TestLanguage(String mode) {
			langFriendly = "Test language";
			langConstant = "test";
			if (null == mode) {
				pb = new ProcessBuilder(childCommand(mode));
			}
			else
				switch (mode) {
					case "chain-success" -> processSteps = Arrays.asList(new ProcessBuilder(childCommand("success")),
							new ProcessBuilder(childCommand("second-stage")));
					case "chain-failure" -> processSteps = Arrays.asList(new ProcessBuilder(childCommand("failure")),
							new ProcessBuilder(childCommand("should-not-run")));
					default -> pb = new ProcessBuilder(childCommand(mode));
				}
		}

		private static List<String> childCommand(String mode) {
			String java = new File(System.getProperty("java.home"),
					"bin" + File.separator + (isWindows() ? "java.exe" : "java"))
				.getAbsolutePath();
			return Arrays.asList(java, "-cp", System.getProperty("java.class.path"), ChildProcess.class.getName(),
					mode);
		}

		private static boolean isWindows() {
			return System.getProperty("os.name").toLowerCase().contains("win");
		}

		private Process getProcess() {
			return p;
		}

		private void awaitExitHandler() {
			await("The process exit handler should clear the process reference.").atMost(15, TimeUnit.SECONDS)
				.pollInterval(10, TimeUnit.MILLISECONDS)
				.until(() -> p == null);
		}

	}

	static final class ChildProcess {

		public static void main(String[] args) throws IOException, InterruptedException {
			if ("wait".equals(args[0])) {
				System.out.println("ready");
				System.out.flush();
				Thread.sleep(5_000);
				return;
			}

			if ("input".equals(args[0])) {
				System.out.println("ready");
				System.out.flush();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
					System.out.println("received:" + reader.readLine());
				}
				return;
			}

			if ("second-stage".equals(args[0])) {
				System.out.println("second-stage output");
				return;
			}

			if ("should-not-run".equals(args[0])) {
				System.out.println("should-not-run");
				return;
			}

			System.out.println("child output");
			if ("failure".equals(args[0])) {
				System.exit(7);
			}
		}

	}

}
