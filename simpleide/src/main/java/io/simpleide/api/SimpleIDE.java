package io.simpleide.api;

import io.simpleide.App;
import io.simpleide.SimpleIDEVersion;
import io.simpleide.language.config.LangConfig;
import io.simpleide.thread.ThreadMaster;

/** Convenience entry point for the most common Simple IDE integrations. */
public final class SimpleIDE {

  private SimpleIDE() {}

  /**
   * Launches the complete Simple IDE desktop application.
   *
   * @param args application arguments
   */
  public static void launch(String... args) {
    App.main(args == null ? new String[0] : args);
  }

  /**
   * Returns the version declared by the packaged application.
   *
   * @return application version, for example {@code 1.0.0beta}
   */
  public static String version() {
    return SimpleIDEVersion.VERSION;
  }

  /**
   * Returns the initialized application thread manager.
   *
   * @return the shared {@link ThreadMaster}
   */
  public static ThreadMaster threads() {
    return Threads.master();
  }

  /**
   * Installs every language whose external toolchain is available.
   *
   * @return the shared language configuration
   */
  public static LangConfig installLanguages() {
    return Languages.installAvailable();
  }
}
