package io.simpleide.language.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
    @Tag("language-tools")
    void runsJavaScriptAndPublishesOutput() throws Exception {
        requireLanguageTool("node");
        Path source = Files.writeString(
                tempDirectory.resolve("main.js"),
                "console.log('javascript-output');\n"
        );

        runAndAssertOutput(new JSLanguage(source), "javascript-output");
    }

    @Test
    @Tag("language-tools")
    void forwardsInputToJavaScriptAsynchronously() throws Exception {
        requireLanguageTool("node");
        Path source = Files.writeString(
                tempDirectory.resolve("input.js"),
                "console.log('ready');\n"
                        + "process.stdin.once('data', data => { "
                        + "console.log('js-received:' + data.toString().trim()); process.exit(0); });\n"
        );

        runAndAssertInputOutput(new JSLanguage(source), "message", "js-received:message");
    }

    @Test
    @Tag("language-tools")
    void runsPythonAndForwardsInputAsynchronously() throws Exception {
        String interpreter = new Python3Language().getInterpreter();
        requireLanguageTool(interpreter);
        Path source = Files.writeString(
                tempDirectory.resolve("main.py"),
                "value = input()\nprint('python-received:' + value)\n"
        );
        Input.getInstance().set("user input");

        runAndAssertOutput(new Python3Language(source), "python-received:user input");
    }

    @Test
    @Tag("language-tools")
    void forceStopsManagedJavaScriptWebServer() throws Exception {
        requireLanguageTool("node");
        Path source = Files.writeString(
                tempDirectory.resolve("server.js"),
                "const http = require('http');\n"
                        + "const server = http.createServer((request, response) => response.end('alive'));\n"
                        + "server.listen(0, '127.0.0.1', () => console.log('listening:' + server.address().port));\n"
        );

        JSLanguage language = new JSLanguage(source);
        RunnableNode node = language.startManaged();
        try {
            String listening = Output.getInstance().get(15);
            assertTrue(listening != null && listening.startsWith("listening:"));
            int port = Integer.parseInt(listening.substring("listening:".length()));
            await().atMost(5, TimeUnit.SECONDS).until(() -> canConnect(port));

            language.forceStop();

            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
            await().atMost(5, TimeUnit.SECONDS).until(() -> !canConnect(port));
        } finally {
            if (Boolean.TRUE.equals(ThreadMaster.getInstance().isNodeRunning(node))) {
                language.forceStop();
            }
        }
    }

    private void requireLanguageTool(String executable) {
        String message = executable + " is not installed";
        if (System.getenv("SIMPLEIDE_EXPECTED_PLATFORM") != null) {
            assertTrue(OSpathUtil.which(executable) != null, message + " on CI");
        } else {
            assumeTrue(OSpathUtil.which(executable) != null, message);
        }
    }

    private void runAndAssertOutput(AbstractLanguage language, String expectedOutput) throws Exception {
        RunnableNode node = language.startManaged();
        assertEquals(expectedOutput, Output.getInstance().get(30));
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
    }

    private void runAndAssertInputOutput(
            AbstractLanguage language,
            String input,
            String expectedOutput) throws Exception {
        RunnableNode node = language.startManaged();
        try {
            assertEquals("ready", Output.getInstance().get(30));
            Input.getInstance().set(input);
            assertEquals(expectedOutput, Output.getInstance().get(30));
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
        } finally {
            if (Boolean.TRUE.equals(ThreadMaster.getInstance().isNodeRunning(node))) {
                language.forceStop();
            }
        }
    }

    private boolean canConnect(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
