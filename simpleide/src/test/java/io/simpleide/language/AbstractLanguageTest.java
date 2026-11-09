package io.simpleide.language;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.simpleide.language.exceptions.ProcessAlreadyExistsException;
import io.simpleide.language.io.Output;
import io.simpleide.language.io.holder.InputHolder;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

class AbstractLanguageTest {

    private Logger languageLogger;
    private ThreadSafeListAppender logCapture;

    @BeforeEach
    public void captureLanguageLogs() throws InterruptedException {
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
    public void stopCapturingLanguageLogs() {
        languageLogger.detachAppender(logCapture);
        logCapture.stop();
    }

    @Test
    public void exposesLanguageNamesAndStartsProcessThroughThreadMaster() throws InterruptedException {
        TestLanguage language = new TestLanguage("success");
        RunnableNode node = language.startManagedTask();
        awaitOutput("child output");
        language.awaitExitHandler();

        assertEquals("Test language", language.getLangFriendly());
        assertEquals("test", language.getLangConstant());
        assertTrue(ThreadMaster.getInstance().showTree().containsKey(node.getId()));
        assertTrue(logCapture.messages().contains("[OUTPUT] child output"));
        assertEquals("child output", Output.getInstance().get(1));
    }

    @Test
    public void capturesOutputAndSuccessfulExit() throws InterruptedException {
        TestLanguage language = new TestLanguage("success");

        language.run();
        language.awaitExitHandler();

        assertTrue(logCapture.messages().contains("[OUTPUT] child output"));
        assertTrue(logCapture.messages().contains("[IDE] Process completed successfully."));
        assertEquals("child output", Output.getInstance().get(1));
    }

    @Test
    public void capturesOutputAndReportsNonzeroExit() throws InterruptedException {
        TestLanguage language = new TestLanguage("failure");

        language.run();
        language.awaitExitHandler();

        assertTrue(logCapture.messages().contains("[OUTPUT] child output"));
        assertTrue(logCapture.messages().contains("[OUTPUT] RUNTIME ERROR: 7"));
        assertTrue(logCapture.messages().contains(
            "[Runtime Error] The developer's program broke with code: 7"
        ));
        assertEquals("child output", Output.getInstance().get(1));
    }

    @Test
    public void forwardsInputFromInputHolderToRunningProcess() throws InterruptedException {
        TestLanguage language = new TestLanguage("input");
        language.startManagedTask();

        awaitOutput("ready");
        InputHolder.INSTANCE.set("user input");
        awaitOutput("received:user input");
        language.awaitExitHandler();

        assertEquals("ready", Output.getInstance().get(1));
        assertEquals("received:user input", Output.getInstance().get(1));
    }

    @Test
    public void rejectsAnotherProcessWhileOneIsRunningAndCanStopIt() throws InterruptedException {
        TestLanguage language = new TestLanguage("wait");
        language.startManagedTask();

        try {
            awaitOutput("ready");
            assertEquals("ready", Output.getInstance().get(1));
            assertThatThrownBy(language::run)
                .isInstanceOfSatisfying(
                    ProcessAlreadyExistsException.class,
                    exception -> assertSame(language.getProcess(), exception.getActualProcess())
                );
            language.forceStop();
            language.awaitExitHandler();
            assertTrue(logCapture.messages().contains("[IDE] Process manually interrupted by the user."));
            assertFalse(logCapture.messages().stream().anyMatch(message -> message.contains("Runtime Error")));
        } finally {
            if (language.getProcess() != null && language.getProcess().isAlive()) {
                language.forceStop();
                language.awaitExitHandler();
            }
        }
    }

    private void awaitOutput(String output) {
        await("Expected logged process output: " + output)
            .atMost(5, TimeUnit.SECONDS)
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
            String java = new File(
                System.getProperty("java.home"),
                "bin" + File.separator + (isWindows() ? "java.exe" : "java")
            ).getAbsolutePath();
            pb = new ProcessBuilder(
                Arrays.asList(
                    java,
                    "-cp",
                    System.getProperty("java.class.path"),
                    ChildProcess.class.getName(),
                    mode
                )
            );
        }

        private static boolean isWindows() {
            return System.getProperty("os.name").toLowerCase().contains("win");
        }

        private Process getProcess() {
            return p;
        }

        private RunnableNode startManagedTask() {
            return startManaged();
        }

        private void awaitExitHandler() {
            await("The process exit handler should clear the process reference.")
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .until(() -> p == null);
            }

    }

    public static final class ChildProcess {
        public static void main(String[] args) throws IOException, InterruptedException {
            if ("wait".equals(args[0])) {
                System.out.println("ready");
                System.out.flush();
                System.in.read();
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

            System.out.println("child output");
            if ("failure".equals(args[0])) {
                System.exit(7);
            }
        }
    }
}
