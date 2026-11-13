package io.simpleide.language.compiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.AbstractLanguage;

public abstract class AbstractCompilerLanguage extends AbstractLanguage {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompilerLanguage.class);

    protected String executable;
    protected String compiler;
    protected String linker;
    protected String assembler;
    protected String runtime;
    protected List<String> execute = List.of();
    protected Path sourceFile;

    private Path artifactDirectory;
    @SuppressWarnings("unused")
    private Path executablePath;
    private final List<Path> artifacts = new ArrayList<>();

    protected AbstractCompilerLanguage() {
        super();
    }

    protected final Path createArtifactPath(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Artifact name must not be blank.");
        }
        if (artifactDirectory == null) {
            try {
                artifactDirectory = Files.createTempDirectory("simpleide-compiled-");
                artifactDirectory.toFile().deleteOnExit();
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not create a temporary build directory.", exception);
            }
        }

        Path artifact = artifactDirectory.resolve(name);
        artifacts.add(artifact);
        artifact.toFile().deleteOnExit();
        return artifact;
    }

    protected final Path normalizeSource(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("Source file path must not be null.");
        }
        return file.toAbsolutePath().normalize();
    }

    protected final void configure(
            Path file,
            String friendlyName,
            String languageConstant,
            Path executablePath,
            List<List<String>> compilationCommands,
            List<String> executeCommand) {
        if (file == null) {
            throw new IllegalArgumentException("Source file path must not be null.");
        }
        if (executablePath == null || compilationCommands == null || compilationCommands.isEmpty()
                || executeCommand == null || executeCommand.isEmpty()) {
            throw new IllegalArgumentException("Compiler and execution commands must be configured.");
        }

        sourceFile = normalizeSource(file);
        this.executablePath = executablePath;
        this.executable = executablePath.toString();
        this.langFriendly = friendlyName;
        this.langConstant = languageConstant;
        this.execute = List.copyOf(executeCommand);

        List<ProcessBuilder> builders = new ArrayList<>(compilationCommands.size() + 1);
        for (List<String> command : compilationCommands) {
            if (command == null || command.isEmpty() || command.stream().anyMatch(arg -> arg == null || arg.isBlank())) {
                throw new IllegalArgumentException("Compilation commands must not be empty.");
            }
            builders.add(new ProcessBuilder(command));
        }
        builders.add(new ProcessBuilder(this.execute));
        processSteps = Collections.unmodifiableList(builders);
        pb = builders.get(0);
    }

    protected final Path getArtifactDirectory() {
        return artifactDirectory;
    }

    protected static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    protected final void writeArtifact(Path artifact, String contents) {
        try {
            Files.writeString(artifact, contents);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write temporary compiler configuration.", exception);
        }
    }

    public String getCompiler() {
        return compiler;
    }

    public String getAssembler() {
        return assembler;
    }

    public String getLinker() {
        return linker;
    }

    public String getRuntime() {
        return runtime;
    }

    public String getExecutable() {
        return executable;
    }

    public List<String> execute() {
        return execute;
    }

    final List<List<String>> getProcessCommands() {
        return processSteps.stream()
                .map(step -> List.copyOf(step.command()))
                .toList();
    }

    @Override
    protected void onExecutionFinished() {
        for (Path artifact : artifacts) {
            try {
                Files.deleteIfExists(artifact);
            } catch (IOException exception) {
                LOGGER.warn("Could not remove temporary compiler artifact {}.", artifact, exception);
            }
        }
        if (artifactDirectory != null) {
            try {
                Files.deleteIfExists(artifactDirectory);
            } catch (IOException exception) {
                LOGGER.warn("Could not remove temporary build directory {}.", artifactDirectory, exception);
            }
        }
        artifacts.clear();
        executablePath = null;
        artifactDirectory = null;
    }
}
