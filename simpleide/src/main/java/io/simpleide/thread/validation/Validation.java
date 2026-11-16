package io.simpleide.thread.validation;

@FunctionalInterface
/**
 * Validates a value, throwing an exception when the value violates a rule.
 * @param <T> type being validated
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public interface Validation<T> {

	/**
	 * Validates a value.
	 * @param value value to check
	 * @throws IllegalArgumentException when the value is invalid
	 */
	void validate(T value);

}
