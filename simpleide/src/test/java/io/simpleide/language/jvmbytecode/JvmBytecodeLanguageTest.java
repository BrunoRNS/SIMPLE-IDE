package io.simpleide.language.jvmbytecode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

class JvmBytecodeLanguageTest {

    @TempDir
    Path tempDir;

    private Logger languageLogger;
    private Logger bytecodeLanguageLogger;
    private ThreadSafeListAppender logCapture;

    @BeforeEach
    void captureLanguageLogs() throws InterruptedException {
        while (Input.getInstance().get(0) != null) {
            // Clear input from prior process tests.
        }
        while (Output.getInstance().get(0) != null) {
            // Clear output from prior process tests.
        }
        languageLogger = (Logger) LoggerFactory.getLogger(AbstractLanguage.class);
        bytecodeLanguageLogger = (Logger) LoggerFactory.getLogger(AbstractBytecodeLanguage.class);
        logCapture = new ThreadSafeListAppender();
        logCapture.setContext(languageLogger.getLoggerContext());
        logCapture.start();
        languageLogger.addAppender(logCapture);
        bytecodeLanguageLogger.addAppender(logCapture);
    }

    @AfterEach
    void stopCapturingLanguageLogs() {
        languageLogger.detachAppender(logCapture);
        bytecodeLanguageLogger.detachAppender(logCapture);
        logCapture.stop();
    }

    @Test
    void rejectsNullSourcePathWithoutLoggingExecutionErrors() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new JavaLanguage(null)
        );
        assertEquals("Source file path must not be null.", exception.getMessage());
        List<String> messages = logCapture.messages();
        assertTrue(messages.stream().noneMatch(message -> message.contains("source file path is null")));
        assertTrue(messages.stream().noneMatch(message -> message.contains("command was not configured")));
    }

    @Test
    void runsJavaThroughEmbeddedCompilerJar() throws Exception {
        JavaLanguage language = new JavaLanguage(writeSource(
            "java",
            "public class Main { public static void main(String[] args) { System.out.println(\"java-output\"); } }"
        ));

        assertEquals("java", language.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_JAVA, language.getLangConstant());
        runAndAssertOutput(language, "java-output");
    }

    @Test
    void forwardsInputToJavaThroughEmbeddedCompilerJar() throws Exception {
        JavaLanguage language = new JavaLanguage(writeSource(
            "java",
            "import java.util.Scanner; public class Main { public static void main(String[] args) { "
                    + "System.out.println(\"ready\"); "
                    + "System.out.println(\"java-received:\" + new Scanner(System.in).nextLine()); } }"
        ));

        runAndAssertInputOutput(language, "message", "java-received:message");
    }

    @Test
    void runsKotlinThroughEmbeddedCompilerJar() throws Exception {
        KotlinLanguage language = new KotlinLanguage(writeSource("kts", "println(\"kotlin-output\")"));

        assertEquals("kotlin", language.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_KOTLIN, language.getLangConstant());
        runAndAssertOutput(language, "kotlin-output");
    }

    @Test
    void forwardsInputToKotlinThroughEmbeddedCompilerJar() throws Exception {
        KotlinLanguage language = new KotlinLanguage(writeSource(
            "kts",
            "println(\"ready\")\nprintln(\"kotlin-received:\" + readLine())"
        ));

        runAndAssertInputOutput(language, "message", "kotlin-received:message");
    }

    @Test
    void runsGroovyThroughEmbeddedCompilerJar() throws Exception {
        GroovyLanguage language = new GroovyLanguage(writeSource("groovy", "println 'groovy-output'"));

        assertEquals("groovy", language.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_GROOVY, language.getLangConstant());
        runAndAssertOutput(language, "groovy-output");
    }

    @Test
    void forwardsInputToGroovyThroughEmbeddedCompilerJar() throws Exception {
        GroovyLanguage language = new GroovyLanguage(writeSource(
            "input.groovy",
            "println 'ready'\nprintln 'groovy-received:' + System.in.newReader().readLine()"
        ));

        runAndAssertInputOutput(language, "message", "groovy-received:message");
    }

    private Path writeSource(String extension, String source) throws Exception {
        Path file = tempDir.resolve("script." + extension);
        return Files.writeString(file, source);
    }

    private void runAndAssertOutput(AbstractBytecodeLanguage language, String expectedOutput) {
        language.run();

        await("Expected output from " + language.getLangFriendly())
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(10, TimeUnit.MILLISECONDS)
            .until(() -> {
                List<String> messages = logCapture.messages();
                return messages.contains("[OUTPUT] " + expectedOutput)
                    && messages.contains("[IDE] Process completed successfully.");
            });
    }

    private void runAndAssertInputOutput(
            AbstractBytecodeLanguage language,
            String input,
            String expectedOutput) throws InterruptedException {
        RunnableNode node = language.startManaged();
        try {
            assertEquals("ready", Output.getInstance().get(30));
            Input.getInstance().set(input);
            assertEquals(expectedOutput, Output.getInstance().get(30));
            await("The " + language.getLangFriendly() + " process should finish.")
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
        } finally {
            if (Boolean.TRUE.equals(ThreadMaster.getInstance().isNodeRunning(node))) {
                language.forceStop();
            }
        }
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
}
