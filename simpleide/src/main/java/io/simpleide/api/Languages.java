package io.simpleide.api;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.config.LangConfig;
import io.simpleide.language.config.LangConfigHolder;
import io.simpleide.language.config.LangSetup;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Facade for language discovery, configuration and managed execution. */
public final class Languages {

  private static final AtomicBoolean SETUP = new AtomicBoolean();

  private Languages() {}

  /**
   * Returns the shared language configuration without installing anything.
   *
   * @return the shared {@link LangConfig}
   */
  public static LangConfig config() {
    return LangConfigHolder.LANG_CONFIG;
  }

  /**
   * Registers every language whose external toolchain is available.
   *
   * <p>The setup is performed only once per JVM. Calling this method repeatedly is safe.
   *
   * @return the shared language configuration
   */
  public static LangConfig installAvailable() {
    if (SETUP.compareAndSet(false, true)) {
      new LangSetup(config()).run();
    }
    return config();
  }

  /**
   * Extracts the lower-case extension, including the leading dot, from a source path.
   *
   * @param file source path
   * @return the extension such as {@code .java}, or an empty string when the file has none
   */
  public static String extensionOf(Path file) {
    Objects.requireNonNull(file, "file");
    Path name = file.getFileName();
    if (name == null) {
      return "";
    }
    String value = name.toString();
    int dot = value.lastIndexOf('.');
    return dot < 0 ? "" : value.substring(dot).toLowerCase(Locale.ROOT);
  }

  /**
   * Returns every extension registered by an installed language.
   *
   * <p>The setup is performed once per JVM, so repeated calls are cheap.
   *
   * @return the sorted, unmodifiable set of registered extensions (with a leading dot), possibly
   *     empty when no toolchain is available
   */
  public static Set<String> supportedExtensions() {
    installAvailable();
    return config().getLanguages().keySet().stream()
        .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
  }

  /**
   * Returns whether an installed language supports the supplied source file.
   *
   * @param file source path
   * @return {@code true} when the extension is registered
   */
  public static boolean isAvailable(Path file) {
    installAvailable();
    return config().getLanguage(extensionOf(file)) != null;
  }

  /**
   * Creates a language instance for a source file.
   *
   * @param file source path
   * @return the language instance
   * @throws IllegalArgumentException when no installed language supports the extension
   */
  public static AbstractLanguage create(Path file) {
    installAvailable();
    String extension = extensionOf(file);
    AbstractLanguage language = config().createLanguage(extension, file);
    if (language == null) {
      throw new IllegalArgumentException("No installed language supports " + extension + ".");
    }
    return language;
  }

  /**
   * Creates and starts a language process managed by {@link Threads}.
   *
   * @param file source path
   * @return the running language instance
   */
  public static AbstractLanguage start(Path file) {
    AbstractLanguage language = create(file);
    language.startManaged();
    return language;
  }

  /**
   * Stops a managed language process.
   *
   * @param language language instance to stop
   */
  public static void stop(AbstractLanguage language) {
    Objects.requireNonNull(language, "language").forceStop();
  }
}
