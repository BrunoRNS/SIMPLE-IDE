package io.simpleide.ui.sidebar;

import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

class ExplorerPanelTest {

	@Test
	void opensTheFolderChooserFromTheEmptyState() throws Exception {
		AtomicBoolean opened = new AtomicBoolean();
		AtomicReference<JButton> openButton = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			ExplorerPanel panel = new ExplorerPanel(new JScrollPane(), () -> opened.set(true));
			openButton.set(findButton(panel));
			assertThat(openButton.get()).isNotNull().hasFieldOrPropertyWithValue("text", "Open folder");
		});

		SwingUtilities.invokeAndWait(() -> openButton.get().doClick());
		await().atMost(Duration.ofSeconds(5)).untilTrue(opened);
	}

	private static JButton findButton(Container container) {
		for (Component child : container.getComponents()) {
			if (child instanceof JButton button) {
				return button;
			}
			if (child instanceof Container nested) {
				JButton result = findButton(nested);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

}
