/**
 * Swing listener adapters that route callbacks through the thread manager.
 *
 * <p>
 * {@link io.simpleide.thread.nodes.edt.listeners.ActionNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.ChangeNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.DocumentNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.MouseNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.WindowNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.TreeSelectionNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.TreeWillExpandNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.PropertyChangeNode} and
 * {@link io.simpleide.thread.nodes.edt.listeners.SwingActionNode} bridge Swing listener
 * and action interfaces into the managed EDT queue, so listener work is tracked as a
 * cancellable thread tree node instead of running directly inside the Swing callback.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/threading.md">docs/api/threading.md</a>
 */
package io.simpleide.thread.nodes.edt.listeners;
