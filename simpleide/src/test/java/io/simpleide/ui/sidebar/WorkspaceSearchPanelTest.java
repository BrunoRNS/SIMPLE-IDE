package io.simpleide.ui.sidebar;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSearchPanelTest {

	@TempDir
	Path tempDir;

	@Test
	void searchOptionsUseWorkspaceAndGitIgnoreDefaults() throws Exception {
		WorkspaceSearchPanel[] panel = new WorkspaceSearchPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new WorkspaceSearchPanel(() -> tempDir, Set::of, (path, line) -> {
		}));

		assertThat(checkBox(panel[0], "workspaceSearchOpenEditorsOnly").isSelected()).isFalse();
		assertThat(checkBox(panel[0], "workspaceSearchUseGitIgnore").isSelected()).isTrue();
		assertThat(checkBox(panel[0], "workspaceSearchRegex").isSelected()).isFalse();
	}

	@Test
	void filteringOccurrencesAndClickingOneOpensItsFileAndLine() throws Exception {
		Path file = tempDir.resolve("Matches.txt");
		Files.writeString(file, "first needle\nsecond needle\n");
		AtomicReference<Path> openedPath = new AtomicReference<>();
		AtomicInteger openedLine = new AtomicInteger();
		WorkspaceSearchPanel[] panel = new WorkspaceSearchPanel[1];
		JTree[] results = new JTree[1];
		JTextField[] query = new JTextField[1];
		JTextField[] resultFilter = new JTextField[1];

		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new WorkspaceSearchPanel(() -> tempDir, Set::of, (path, line) -> {
				openedPath.set(path);
				openedLine.set(line);
			});
			results[0] = component(panel[0], "workspaceSearchResults", JTree.class);
			query[0] = component(panel[0], "workspaceSearchQuery", JTextField.class);
			resultFilter[0] = component(panel[0], "workspaceSearchResultFilter", JTextField.class);
			query[0].setText("needle");
			component(panel[0], "workspaceSearchButton", JButton.class).doClick();
		});

		awaitRowCount(results[0], 3);
		SwingUtilities.invokeAndWait(() -> resultFilter[0].setText("second"));
		awaitRowCount(results[0], 2);

		SwingUtilities.invokeAndWait(() -> {
			Rectangle rowBounds = results[0].getRowBounds(1);
			results[0].dispatchEvent(new MouseEvent(results[0], MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
					rowBounds.x + 8, rowBounds.y + rowBounds.height / 2, 1, false, MouseEvent.BUTTON1));
		});

		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(openedPath.get()).isEqualTo(file);
			assertThat(openedLine.get()).isEqualTo(2);
		});

		SwingUtilities.invokeAndWait(() -> {
			resultFilter[0].setText("");
			Rectangle rowBounds = results[0].getRowBounds(0);
			results[0].dispatchEvent(new MouseEvent(results[0], MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
					rowBounds.x + 8, rowBounds.y + rowBounds.height / 2, 1, false, MouseEvent.BUTTON1));
		});
		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(openedLine.get()).isEqualTo(1));
	}

	private void awaitRowCount(JTree tree, int expected) {
		AtomicInteger rowCount = new AtomicInteger();
		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			SwingUtilities.invokeAndWait(() -> rowCount.set(tree.getRowCount()));
			assertThat(rowCount.get()).isEqualTo(expected);
		});
	}

	private static JCheckBox checkBox(Component root, String name) {
		return component(root, name, JCheckBox.class);
	}

	private static <T extends Component> T component(Component root, String name, Class<T> type) {
		if (type.isInstance(root) && name.equals(root.getName())) {
			return type.cast(root);
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				T found = component(child, name, type);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

}
