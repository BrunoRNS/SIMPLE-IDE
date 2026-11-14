package io.simpleide.language.jvmbytecode;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.utils.ResourceUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBytecodeLanguage extends AbstractLanguage {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBytecodeLanguage.class);

  private static final String COMPILER_JAR_RESOURCE = "simplebytecode-compiler.jar";
  private static final Path COMPILER_JAR = getCompilerJarPath();

  protected AbstractBytecodeLanguage(Path filePath, String friendlyName, String languageConstant) {
    if (filePath == null) {
      throw new IllegalArgumentException("Source file path must not be null.");
    }
    if (COMPILER_JAR == null) {
      LOGGER.error(
          "[IDE] Cannot configure {} execution: compiler JAR is unavailable.", friendlyName);
      return;
    }

    this.langFriendly = friendlyName;
    this.langConstant = languageConstant;
    Path sourceFile = filePath.toAbsolutePath().normalize();
    Optional<Path> javaExecutable = findJavaExecutable();
    if (javaExecutable.isEmpty()) {
      LOGGER.error(
          "[IDE] Cannot configure {} execution: Java executable was not found.", friendlyName);
      return;
    }

    this.pb =
        new ProcessBuilder(
            javaExecutable.get().toString(),
            "-jar",
            COMPILER_JAR.toString(),
            sourceFile.toString());
  }

  public static boolean isAvailable() {
    return COMPILER_JAR != null && findJavaExecutable().isPresent();
  }

  private static Optional<Path> findJavaExecutable() {
    boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    String exeName = isWindows ? "java.exe" : "java";

    Optional<String> currentCmd = ProcessHandle.current().info().command();
    if (currentCmd.isPresent()) {
      Path cmdPath = Paths.get(currentCmd.get());
      Path fileName = cmdPath.getFileName();
      if (fileName != null && fileName.toString().equalsIgnoreCase(exeName)) {
        Optional<Path> executable = executableFile(cmdPath);
        if (executable.isPresent()) {
          return executable;
        }
      }
    }

    String javaHomeProp = System.getProperty("java.home");
    if (javaHomeProp != null && !javaHomeProp.isEmpty()) {
      Optional<Path> executable = executableFile(Paths.get(javaHomeProp, "bin", exeName));
      if (executable.isPresent()) {
        return executable;
      }
    }

    String envJavaHome = System.getenv("JAVA_HOME");
    if (envJavaHome != null && !envJavaHome.isEmpty()) {
      Optional<Path> executable = executableFile(Paths.get(envJavaHome, "bin", exeName));
      if (executable.isPresent()) {
        return executable;
      }
    }

    String pathEnv = System.getenv("PATH");
    if (pathEnv != null) {
      for (String dir : pathEnv.split(File.pathSeparator)) {
        if (!dir.isBlank()) {
          Optional<Path> executable = executableFile(Paths.get(dir, exeName));
          if (executable.isPresent()) {
            return executable;
          }
        }
      }
    }

    return Optional.empty();
  }

  private static Optional<Path> executableFile(Path path) {
    Path absolutePath = path.toAbsolutePath().normalize();
    return Files.isRegularFile(absolutePath) && Files.isExecutable(absolutePath)
        ? Optional.of(absolutePath)
        : Optional.empty();
  }

  private static Path getCompilerJarPath() {
    try {
      return ResourceUtil.getResourcePath(AbstractBytecodeLanguage.class, COMPILER_JAR_RESOURCE);
    } catch (IOException exception) {
      Path buildDirectoryJar = compilerJarFromBuildDirectory();
      if (buildDirectoryJar != null) {
        return buildDirectoryJar;
      }
      LOGGER.error(
          "[IDE] Could not locate the embedded simplebytecode compiler JAR: {}",
          COMPILER_JAR_RESOURCE,
          exception);
      return null;
    }
  }

  /**
   * Locates the compiler fat JAR in the sibling module build directory. This keeps test runs and
   * development launches working before the packaging phase copies the JAR into the resources.
   *
   * @return the newest compiler fat JAR, or {@code null} when none exists
   */
  private static Path compilerJarFromBuildDirectory() {
    Path buildDirectory = Paths.get("..", "simplebytecode-compiler", "target").toAbsolutePath();
    if (!Files.isDirectory(buildDirectory)) {
      return null;
    }
    try (java.util.stream.Stream<Path> candidates =
        java.nio.file.Files.list(buildDirectory)
            .filter(
                path ->
                    path.getFileName().toString().endsWith("-jar-with-dependencies.jar")
                        && path.getFileName().toString().startsWith("simplebytecode-compiler"))) {
      return candidates
          .max(java.util.Comparator.comparingLong(path -> path.toFile().lastModified()))
          .orElse(null);
    } catch (IOException exception) {
      return null;
    }
  }
}
