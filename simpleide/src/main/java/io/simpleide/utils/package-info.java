/**
 * Small shared utilities for files, resources and executable lookup.
 *
 * <p>
 * {@link io.simpleide.utils.FileUtil} reads strict UTF-8 text from regular files and
 * rejects directories and malformed input. {@link io.simpleide.utils.ResourceUtil}
 * resolves classpath resources as filesystem paths, both from a packaged JAR and from a
 * source checkout, and is used for icons and theme files.
 * {@link io.simpleide.utils.OSpathUtil} locates executables on the host {@code PATH} and
 * is the basis for language toolchain discovery.
 *
 * <p>
 * The helpers are stateless. File and resource reads block the calling thread and are not
 * cached, so callers should avoid repeating them on the Swing Event Dispatch Thread.
 * Executable lookup inspects the environment and performs filesystem checks; cache the
 * result if it is used in a tight loop.
 *
 * <p>
 * The {@code mappeable} subpackage provides reflective map conversion, and the
 * {@code service} subpackage provides the file operations behind the project tree.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
package io.simpleide.utils;
