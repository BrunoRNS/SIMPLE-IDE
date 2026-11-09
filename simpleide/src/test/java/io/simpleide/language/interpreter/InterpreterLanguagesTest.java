package io.simpleide.language.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.utils.OSpathUtil;

class InterpreterLanguagesTest {

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void clearStreams() throws InterruptedException {
        while (Input.getInstance().get(0) != null) {
            // Clear input from prior process tests.
        }
        while (Output.getInstance().get(0) != null) {
            // Clear output from prior process tests.
        }
    }

    @Test
    void interpreterMetadataAndPathConstructorsAreConfigured() {
        JSLanguage javascript = new JSLanguage(tempDirectory.resolve("main.js"));
        Python3Language python = new Python3Language(tempDirectory.resolve("main.py"));

        assertEquals("node", new JSLanguage().getInterpreter());
        assertEquals("node", InterpreterRequiredFile.getInterpreter(JSLanguage.class));
        assertThrows(IllegalArgumentException.class, () -> InterpreterRequiredFile.getInterpreter(null));
        assertEquals("node", javascript.getInterpreter());
        assertEquals("javascript", javascript.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT, javascript.getLangConstant());
        assertEquals(List.of(), javascript.getAdditionalArgs());

        assertEquals(new Python3Language().getInterpreter(), python.getInterpreter());
        assertEquals(python.getInterpreter(), InterpreterRequiredFile.getInterpreter(Python3Language.class));
        assertEquals("python3", python.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_PYTHON, python.getLangConstant());
    }

    @Test
    void runsJavaScriptAndPublishesOutput() throws Exception {
        assumeTrue(OSpathUtil.which("node") != null, "node is not installed");
        Path source = Files.writeString(
                tempDirectory.resolve("main.js"),
                "console.log('javascript-output');\n"
        );

        runAndAssertOutput(new JSLanguage(source), "javascript-output");
    }

    @Test
    void runsPythonAndForwardsInputAsynchronously() throws Exception {
        String interpreter = new Python3Language().getInterpreter();
        assumeTrue(OSpathUtil.which(interpreter) != null, interpreter + " is not installed");
        Path source = Files.writeString(
                tempDirectory.resolve("main.py"),
                "value = input()\nprint('python-received:' + value)\n"
        );
        Input.getInstance().set("user input");

        runAndAssertOutput(new Python3Language(source), "python-received:user input");
    }

    private void runAndAssertOutput(AbstractLanguage language, String expectedOutput) throws Exception {
        RunnableNode node = language.startManaged();
        assertEquals(expectedOutput, Output.getInstance().get(30));
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
    }
}
