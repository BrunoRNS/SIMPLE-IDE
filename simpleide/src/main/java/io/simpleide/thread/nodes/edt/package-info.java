/**
 * Thread tree nodes that execute on the Swing Event Dispatch Thread.
 *
 * <p>
 * {@link io.simpleide.thread.nodes.edt.EDTNode} is a convenience {@code Node} anchored to
 * the thread tree root that reports level one and requires EDT execution. Listener
 * adapters built on it live in {@code io.simpleide.thread.nodes.edt.listeners}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/threading.md">docs/api/threading.md</a>
 */
package io.simpleide.thread.nodes.edt;
