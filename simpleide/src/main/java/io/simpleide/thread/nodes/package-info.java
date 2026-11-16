/**
 * Node contracts used by the Simple IDE thread tree.
 *
 * <p>
 * {@link io.simpleide.thread.nodes.NodePattern} describes a unit of scheduled work: what
 * to run on start, what to run on close, its tree level and whether it must execute on
 * the Swing Event Dispatch Thread. {@link io.simpleide.thread.nodes.Node} adds parent
 * tracking and stable identity, and {@link io.simpleide.thread.nodes.NodeIdGenerator}
 * provides process-wide identifiers.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/threading.md">docs/api/threading.md</a>
 */
package io.simpleide.thread.nodes;
