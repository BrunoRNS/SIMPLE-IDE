package io.simpleide.ui.editor;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests the breakpoint API of {@link io.simpleide.ui.editor.Editor}.
 */
class EditorBreakpointTest {

	@Test
	void togglesBreakpointsAndNotifiesListeners() throws Exception {
		List<List<Integer>> notifications = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			editor.setText("one\ntwo\nthree");
			editor.setBreakpointListener(notifications::add);

			assertThat(editor.toggleBreakpoint(2)).isTrue();
			assertThat(editor.toggleBreakpoint(3)).isTrue();
			assertThat(editor.getBreakpointLines()).containsExactly(2, 3);

			assertThat(editor.toggleBreakpoint(2)).isFalse();
			assertThat(editor.getBreakpointLines()).containsExactly(3);

			editor.clearBreakpoints();
			assertThat(editor.getBreakpointLines()).isEmpty();
		});

		assertThat(notifications).hasSize(4);
		assertThat(notifications.get(0)).containsExactly(2);
		assertThat(notifications.get(3)).isEmpty();
	}

	@Test
	void ignoresLinesOutsideTheDocument() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			editor.setText("only line");

			assertThat(editor.toggleBreakpoint(0)).isFalse();
			assertThat(editor.toggleBreakpoint(10)).isFalse();
			assertThat(editor.getBreakpointLines()).isEmpty();
		});
	}

}
