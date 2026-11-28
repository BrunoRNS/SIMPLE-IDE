/**
 * Project file tree shown in the Simple IDE main window.
 *
 * <p>
 * {@link io.simpleide.ui.treefile.Tree} is the Swing tree that lazily expands directories
 * and reflects the file system. {@link io.simpleide.ui.treefile.TreeFile} is the common
 * model for entries, with {@link io.simpleide.ui.treefile.Directory} and
 * {@link io.simpleide.ui.treefile.File} as its concrete types and
 * {@link io.simpleide.ui.treefile.TypeFile} as the extension classification used for
 * syntax highlighting and icons. {@link io.simpleide.ui.treefile.TreeFileActions} adds
 * the context menu, {@link io.simpleide.ui.treefile.TreeFileCellRenderer} draws the
 * entries, {@link io.simpleide.ui.treefile.TreeState} tracks the selection, and
 * {@link io.simpleide.ui.treefile.Watcher} keeps the tree synchronized with disk changes.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/file-tree.md">docs/api/ui/file-tree.md</a>
 */
package io.simpleide.ui.treefile;
