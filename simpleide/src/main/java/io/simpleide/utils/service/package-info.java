/**
 * File operations used by the project tree and the editor save path.
 *
 * <p>
 * {@link io.simpleide.utils.service.FileService} saves UTF-8 text, deletes files and
 * directory trees without following symbolic links, renames entries within their parent
 * directory, and creates new files or directories. Creation returns the matching
 * {@link io.simpleide.ui.treefile.TreeFile} node so the tree can insert it directly.
 *
 * <p>
 * Every user-provided name is validated to a single relative path segment before it is
 * resolved against the parent directory. Absolute paths, {@code .}, {@code ..} and
 * multi-segment names are rejected with {@link IllegalArgumentException}, which prevents
 * an operation from escaping the selected directory. Invalid or malformed names never
 * touch the file system.
 *
 * <p>
 * Operations are synchronous and perform blocking I/O on the calling thread. The tree
 * actions and editor save run them on background nodes or on the EDT as appropriate;
 * embedders should avoid calling them on the EDT for large directory trees.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
package io.simpleide.utils.service;
