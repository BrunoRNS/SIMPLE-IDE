/**
 * Root package of the Simple IDE desktop application.
 *
 * <p>
 * {@link io.simpleide.App} is the executable entry point. It installs the available
 * language toolchains, registers an EDT node that creates the main window and initializes
 * the shared thread manager. {@link io.simpleide.SimpleIDEVersion} reads the application
 * version from the packaged {@code /simpleide.properties} resource and fails fast when
 * the resource or its {@code version} entry is missing.
 *
 * <p>
 * The package has no reusable components of its own. Embedding applications start from
 * the stable facades in {@link io.simpleide.api}; application code starts from
 * {@code App}. The main window and its supporting packages live in
 * {@link io.simpleide.frame}.
 *
 * <p>
 * Starting the application shares one {@code ThreadMaster} and one language configuration
 * per JVM. Do not call {@code App.main} twice in the same process, and do not shut the
 * thread manager down from a library that does not own the application.
 *
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 */
package io.simpleide;
