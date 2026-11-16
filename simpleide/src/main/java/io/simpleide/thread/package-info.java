/**
 * Process-wide thread manager built around a tree of cancellable nodes.
 *
 * <p>
 * {@link io.simpleide.thread.ThreadMaster} is the shared entry point. It owns the node
 * tree, schedules work on the Swing Event Dispatch Thread or on bounded background pools,
 * and exposes named pools through {@link io.simpleide.thread.ManagedThreadPool}.
 * {@link io.simpleide.thread.ThreadTaskScheduler} performs the actual scheduling, while
 * {@link io.simpleide.thread.AbstractThreadMaster} implements the common lifecycle and
 * {@link io.simpleide.thread.WatchDead} keeps track of detached processes.
 *
 * <p>
 * The manager is intended to be initialized once per JVM and shared by the application,
 * language processes and terminal sessions.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/threading.md">docs/api/threading.md</a>
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/THREADMASTER.md">docs/reference/THREADMASTER.md</a>
 */
package io.simpleide.thread;
