/**
 * Validation rules applied when thread nodes are registered or executed.
 *
 * <p>
 * {@link io.simpleide.thread.validation.Validation} is the small functional contract.
 * {@link io.simpleide.thread.validation.NodeRegistrationValidation} verifies levels and
 * EDT compatibility before a node enters the tree, and
 * {@link io.simpleide.thread.validation.NodeExecutionValidation} verifies at run time
 * that the node executes on the thread kind it declares.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/THREADMASTER.md">docs/reference/THREADMASTER.md</a>
 */
package io.simpleide.thread.validation;
