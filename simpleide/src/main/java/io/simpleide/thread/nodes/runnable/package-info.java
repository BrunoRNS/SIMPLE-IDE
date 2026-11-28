/**
 * Thread tree nodes that execute on managed background workers.
 *
 * <p>
 * {@link io.simpleide.thread.nodes.runnable.RunnableNode} is a {@code Node} that reports
 * {@code false} from {@code isEDT()} and derives its level from its parent. It is the
 * standard node used for background tasks registered with the thread manager.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/threading.md">docs/api/threading.md</a>
 */
package io.simpleide.thread.nodes.runnable;
