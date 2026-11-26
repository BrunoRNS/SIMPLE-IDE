package io.simpleide.ui.menubar.menus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.simpleide.settings.IdeAction;
import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.KeybindingManager;

/**
 * Tests {@link io.simpleide.ui.menubar.menus.ExecuteMenu}.
 */
class ExecuteMenuTest {

	@Test
	void exposesRunAndDebugItemsInOrder() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			ExecuteMenu menu = new ExecuteMenu(() -> {
			}, () -> {
			});
			menu.applySettings(new IdeSettings());

			assertThat(menu.getText()).isEqualTo("Execute");
			assertThat(menu.getItemCount()).isEqualTo(2);
			JMenuItem run = menu.getItem(0);
			JMenuItem debug = menu.getItem(1);
			assertThat(run.getText()).isEqualTo("Run");
			assertThat(debug.getText()).isEqualTo("Debug");
			assertThat(run.getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("F5"));
			assertThat(debug.getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl F5"));
		});
	}

	@Test
	void appliesConfiguredShortcuts() throws Exception {
		IdeSettings settings = new IdeSettings();
		settings.setKeybinding(IdeAction.RUN, "ctrl alt R");
		settings.setKeybinding(IdeAction.DEBUG, "ctrl shift D");

		SwingUtilities.invokeAndWait(() -> {
			ExecuteMenu menu = new ExecuteMenu(() -> {
			}, () -> {
			});
			menu.applySettings(settings);

			assertThat(menu.getRunItem().getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl alt R"));
			assertThat(menu.getDebugItem().getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl shift D"));
		});
	}

	@Test
	void disablesDebugWithAnExplanation() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			ExecuteMenu menu = new ExecuteMenu(() -> {
			}, () -> {
			});
			menu.setDebugEnabled(false);

			assertThat(menu.getDebugItem().isEnabled()).isFalse();
			assertThat(menu.getDebugItem().getToolTipText()).isEqualTo(ExecuteMenu.DEBUG_UNAVAILABLE_TOOLTIP);
		});
	}

	@Test
	void removesDebugShortcutWhenDebugActionIsUnavailable() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			IdeSettings settings = new IdeSettings();
			KeybindingManager manager = new KeybindingManager(settings);
			javax.swing.JRootPane root = new javax.swing.JRootPane();
			manager.install(root, java.util.Map.of(IdeAction.RUN, () -> {
			}));

			assertThat(
					root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("F5")))
				.isNotNull();
			assertThat(root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
				.get(KeyStroke.getKeyStroke("ctrl F5"))).isNull();
			manager.uninstall();
		});
	}

	@Test
	void insertsExecuteBetweenSettingsAndHelp() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			IdeMenuBar menuBar = new IdeMenuBar(() -> {
			}, () -> {
			}, () -> {
			}, () -> {
			}, () -> {
			}, () -> {
			}, () -> {
			});

			assertThat(menuBar.getMenu(1).getText()).isEqualTo("Settings");
			assertThat(menuBar.getMenu(2).getText()).isEqualTo("Execute");
			assertThat(menuBar.getMenu(3).getText()).isEqualTo("Help");
		});
	}

	@Test
	void runsActionsWhenClicked() throws Exception {
		CountDownLatch clicked = new CountDownLatch(2);
		SwingUtilities.invokeAndWait(() -> {
			ExecuteMenu menu = new ExecuteMenu(clicked::countDown, clicked::countDown);
			menu.getRunItem().doClick();
			menu.getDebugItem().doClick();
		});

		assertThat(clicked.await(10, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void rejectsMissingActions() {
		assertThatThrownBy(() -> new ExecuteMenu(null, () -> {
		})).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ExecuteMenu(() -> {
		}, null)).isInstanceOf(NullPointerException.class);
	}

}
