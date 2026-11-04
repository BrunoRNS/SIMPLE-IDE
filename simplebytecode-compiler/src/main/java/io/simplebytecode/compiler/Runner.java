package io.simplebytecode.compiler;

import java.nio.file.Path;
import java.util.Objects;

import io.simplebytecode.compiler.utils.MatchExtension;

public class Runner implements Runnable {

    private final Class<? extends RunnerInterface> runner;
    private final Path file;

    public Runner(Path file) {
        this.file = Objects.requireNonNull(file, "File path must not be null.")
            .toAbsolutePath()
            .normalize();
        this.runner = MatchExtension.getRunnerFromExt(getFileExtension(this.file));
        if (runner == null) {
            throw new UnsupportedOperationException(
                "No runner found for file extension: " + getFileExtension(this.file)
            );
        }
    }

    private String getFileExtension(Path file) {
        if (file.getFileName() == null) {
            return "";
        }
        String name = file.getFileName().toString();
        int lastIndex = name.lastIndexOf('.');
        if (lastIndex < 0 || lastIndex == name.length() - 1) {
            return "";
        }
        return name.substring(lastIndex + 1);
    }

    @Override
    public void run() {
        try {
            RunnerInterface runnerInstance = runner.getDeclaredConstructor().newInstance();
            String src = runnerInstance.loadFile(file);
            runnerInstance.prepare(src);
            runnerInstance.run();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create runner: " + runner.getName(), exception);
        }
    }
}
