package io.simpleide.language.jvmbytecode;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.simpleide.language.AbstractLanguage;

class JvmBytecodeLanguageTest {

    @TempDir
    Path tempDir;

    private Logger languageLogger;
    private Logger bytecodeLanguageLogger;
    private ThreadSafeListAppender logCapture;

    @BeforeEach
    void captureLanguageLogs() {
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
    void logsInsteadOfThrowingWhenSourcePathIsNull() {
        JavaLanguage language = assertDoesNotThrow(() -> new JavaLanguage(null));

        assertDoesNotThrow(language::run);
        List<String> messages = logCapture.messages();
        assertTrue(messages.stream().anyMatch(message -> message.contains("source file path is null")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("command was not configured")));
    }

    @Test
    void runsJavaThroughEmbeddedCompilerJar() throws Exception {
        JavaLanguage language = new JavaLanguage(writeSource(
            "java",
            "public class Main { public static void main(String[] args) { System.out.println(\"java-output\"); } }"
        ));

        assertEquals("Java", language.getLangFriendly());
        assertEquals("java", language.getLangConstant());
        runAndAssertOutput(language, "java-output");
    }

    @Test
    void runsKotlinThroughEmbeddedCompilerJar() throws Exception {
        KotlinLanguage language = new KotlinLanguage(writeSource("kts", "println(\"kotlin-output\")"));

        assertEquals("Kotlin", language.getLangFriendly());
        assertEquals("kotlin", language.getLangConstant());
        runAndAssertOutput(language, "kotlin-output");
    }

    @Test
    void runsGroovyThroughEmbeddedCompilerJar() throws Exception {
        GroovyLanguage language = new GroovyLanguage(writeSource("groovy", "println 'groovy-output'"));

        assertEquals("Groovy", language.getLangFriendly());
        assertEquals("groovy", language.getLangConstant());
        runAndAssertOutput(language, "groovy-output");
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
