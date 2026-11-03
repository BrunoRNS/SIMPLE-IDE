package io.simpleide.thread.validation;

@FunctionalInterface
public interface Validation<T> {

    void validate(T value);
}
