package io.simpleide.ui.io;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.simpleide.language.io.Input;

/**
 * Tests {@link io.simpleide.ui.io.InputForwarder}.
 */
class InputForwarderTest {

	@Test
	void forwardsEachSubmittedLineToTheLanguageInputQueue() throws Exception {
		Input input = new Input();
		InputForwarder forwarder = new InputForwarder(input);

		assertThat(forwarder.submit("first")).isTrue();
		assertThat(forwarder.submit("second\nthird")).isTrue();

		assertThat(input.get(1)).isEqualTo("first");
		assertThat(input.get(1)).isEqualTo("second");
		assertThat(input.get(1)).isEqualTo("third");
	}

	@Test
	void forwardsBlankInputAndStopsAfterClose() throws Exception {
		Input input = new Input();
		InputForwarder forwarder = new InputForwarder(input);

		assertThat(forwarder.submit("")).isTrue();
		assertThat(input.get(1)).isEmpty();
		assertThat(forwarder.submit("   ")).isTrue();
		assertThat(input.get(1)).isEqualTo("   ");
		forwarder.close();

		assertThat(forwarder.isActive()).isFalse();
		assertThat(forwarder.submit("late")).isFalse();
		assertThat(input.get(0)).isNull();
	}

}
