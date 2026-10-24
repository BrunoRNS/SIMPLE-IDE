package io.simpleide.language;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.language.exceptions.ProcessAlreadyExistsException;

class AbstractLanguageTest {

    @Test
    void exposesLanguageNamesAndCreatesThread() {
        TestLanguage language = new TestLanguage("success");

        assertEquals("Test language", language.getLangFriendly());
        assertEquals("test", language.getLangConstant());
        assertEquals(Thread.State.NEW, language.getThread().getState());
    }

    @Test
    void capturesOutputAndSuccessfulExit() throws InterruptedException {
        TestLanguage language = new TestLanguage("success");

        language.run();
        language.awaitExitHandler();

        assertEquals("child output\n", language.getUserLog());
        assertEquals("[IDE] Process completed successfully.\n", language.getSystemLog());
    }

    @Test
    void capturesOutputAndReportsNonzeroExit() throws InterruptedException {
        TestLanguage language = new TestLanguage("failure");

        language.run();
        language.awaitExitHandler();

        assertEquals("child output\nRUNTIME ERROR: 7\n", language.getUserLog());
        assertEquals(
            "[Runtime Error] The developer's program broke with code: 7\n",
            language.getSystemLog()
        );
    }

    @Test
    void rejectsAnotherProcessWhileOneIsRunningAndCanStopIt() throws InterruptedException {
        TestLanguage language = new TestLanguage("wait");
        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread thread = language.getThread();
        thread.setUncaughtExceptionHandler((ignored, error) -> runFailure.set(error));
        thread.start();

        try {
            language.awaitOutput("ready");
            ProcessAlreadyExistsException exception = assertThrows(
                ProcessAlreadyExistsException.class,
                language::run
            );
            assertSame(language.getProcess(), exception.getActualProcess());
            language.forceStop();
            thread.join(5_000);

            assertFalse(thread.isAlive(), "The process reader should finish after stopping the process.");
            language.awaitExitHandler();
            assertTrue(language.getSystemLog().contains("Process manually interrupted by the user."));
            assertFalse(language.getSystemLog().contains("Runtime Error"));
            assertNull(runFailure.get());
        } finally {
            if (thread.isAlive()) {
                language.forceStop();
                thread.join(5_000);
            }
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

        private void awaitOutput(String output) throws InterruptedException {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!getUserLog().contains(output) && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertTrue(getUserLog().contains(output), "Expected process output: " + output);
        }

        private void awaitExitHandler() throws InterruptedException {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (p != null && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertNull(p, "The process exit handler should clear the process reference.");
        }
    }

    public static final class ChildProcess {
        public static void main(String[] args) throws InterruptedException {
            if ("wait".equals(args[0])) {
                System.out.println("ready");
                System.out.flush();
                Thread.sleep(5_000);
                return;
            }

            System.out.println("child output");
            if ("failure".equals(args[0])) {
                System.exit(7);
            }
        }
    }
}
