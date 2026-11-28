/**
 * Registry and traversal support for the thread tree.
 *
 * <p>
 * {@link io.simpleide.thread.tree.ThreadTreeRegistry} stores registered nodes by
 * identifier, validates parent and level relationships, removes nodes as a cascade and
 * produces breadth-first and depth-first traversals.
 * {@link io.simpleide.thread.tree.TreeNode} is a single tree entry, and
 * {@link io.simpleide.thread.tree.ThreadTree} is the minimal contract implemented by the
 * thread manager.
 *
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/THREADMASTER.md">docs/reference/THREADMASTER.md</a>
 */
package io.simpleide.thread.tree;
