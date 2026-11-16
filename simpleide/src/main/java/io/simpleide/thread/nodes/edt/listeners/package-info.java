/**
 * Swing listener adapters that route callbacks through the thread manager.
 *
 * <p>
 * {@link io.simpleide.thread.nodes.edt.listeners.ActionNode},
 * {@link io.simpleide.thread.nodes.edt.listeners.ChangeNode} and
 * {@link io.simpleide.thread.nodes.edt.listeners.DocumentNode} bridge Swing listener
 * interfaces into the managed EDT queue, so listener work is tracked as a cancellable
 * thread tree node instead of running directly inside the Swing callback.
 *
 * @author BrunoRNS
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/threading.md">docs/api/threading.md</a>
 */
package io.simpleide.thread.nodes.edt.listeners;
