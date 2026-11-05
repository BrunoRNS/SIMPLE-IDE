package io.simplebytecode.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerInterfaceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadsUtf8SourceFromAFile() throws Exception {
        Path source = tempDirectory.resolve("source.txt");
        Files.writeString(source, "source text");

        assertThat(new TestRunner().loadFile(source)).isEqualTo("source text");
    }

    private static final class TestRunner implements RunnerInterface {
        @Override
        public void prepare(String src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void run() {
            throw new UnsupportedOperationException();
        }
    }
}
