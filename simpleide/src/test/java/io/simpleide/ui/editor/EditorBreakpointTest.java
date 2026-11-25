package io.simpleide.ui.editor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.fife.ui.rtextarea.IconRowHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.core.Document;
import io.simpleide.ui.treefile.SimpleIDEFile;

/**
 * Tests the breakpoint support of {@link io.simpleide.ui.editor.Editor} and the gutter
 * interaction of {@link io.simpleide.ui.editor.EditorTab}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
class EditorBreakpointTest {

	@TempDir
	Path root;

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

	@Test
	void clickingTheGutterIconColumnTogglesBreakpoints() throws Exception {
		EditorTab tab = openTab();
		IconRowHeader header = awaitIconRowHeader(tab);
		Editor editor = tab.getEditor();

		SwingUtilities.invokeAndWait(() -> {
			editor.setSize(600, 400);
			int y = lineCenterY(editor, 2);

			header.dispatchEvent(mouseEvent(header, MouseEvent.MOUSE_MOVED,
					SwingUtilities.convertPoint(editor, new Point(2, y), header), MouseEvent.NOBUTTON));
			assertThat(editor.getHoverLine()).isEqualTo(2);

			header.dispatchEvent(mouseEvent(header, MouseEvent.MOUSE_CLICKED,
					SwingUtilities.convertPoint(editor, new Point(2, y), header), MouseEvent.BUTTON1));
			assertThat(tab.getBreakpoints()).containsExactly(2);

			header.dispatchEvent(mouseEvent(header, MouseEvent.MOUSE_CLICKED,
					SwingUtilities.convertPoint(editor, new Point(2, y), header), MouseEvent.BUTTON1));
			assertThat(tab.getBreakpoints()).isEmpty();

			header.dispatchEvent(new MouseEvent(header, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, -5, 0,
					0, false, MouseEvent.NOBUTTON));
			assertThat(editor.getHoverLine()).isZero();
		});
	}

	private EditorTab openTab() throws Exception {
		Path path = Files.writeString(root.resolve("Main.java"), "class Main {\n  int value = 1;\n}\n");
		AtomicReference<EditorTab> holder = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			try {
				holder.set(new EditorTab(new SimpleIDEFile(path), new Document(path, "java")));
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});
		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> SwingUtilities.invokeAndWait(() -> {
			assertThat(holder.get().getEditor()).isNotNull();
			assertThat(findIconRowHeader(holder.get())).isNotNull();
		}));
		return holder.get();
	}

	private IconRowHeader awaitIconRowHeader(EditorTab tab) {
		IconRowHeader header = findIconRowHeader(tab);
		assertThat(header).isNotNull();
		return header;
	}

	private static int lineCenterY(Editor editor, int line) {
		try {
			Rectangle2D rectangle = editor.modelToView2D(editor.getLineStartOffset(line - 1));
			assertThat(rectangle).isNotNull();
			return (int) rectangle.getCenterY();
		}
		catch (BadLocationException error) {
			throw new AssertionError(error);
		}
	}

	private static MouseEvent mouseEvent(Component source, int id, Point point, int button) {
		return new MouseEvent(source, id, System.currentTimeMillis(), 0, point.x, point.y, 1, false, button);
	}

	private static IconRowHeader findIconRowHeader(Component component) {
		if (component instanceof IconRowHeader header) {
			return header;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				IconRowHeader found = findIconRowHeader(child);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

}
