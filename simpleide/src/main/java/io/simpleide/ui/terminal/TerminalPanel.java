package io.simpleide.ui.terminal;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.jediterm.terminal.TtyConnector;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.ThemeColors;

/**
 * Panel that hosts the tabbed terminal views and their add and close buttons.
 *
 * <p>
 * The panel supports at most {@link #MAX_TABS} tabs and keeps buttons icon-only with
 * accessible names. Actions are supplied by {@link BusyBoxTerminalController}; the panel
 * itself never starts a process.
 *
 * <p>
 * Covered by {@code TerminalPanelTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class TerminalPanel extends JPanel {

	static final int MAX_TABS = 5;

	@SuppressWarnings("unused")
	private static final Color ICON_ACCENT = new Color(0x6E7781);

	private final JTabbedPane terminalTabs = new JTabbedPane();

	private final JButton addButton = new TerminalActionButton();

	private final JButton closeButton = new TerminalActionButton();

	private final Map<BigInteger, TerminalView> views = new LinkedHashMap<>();

	/**
	 * Creates an empty terminal panel.
	 */
	public TerminalPanel() {
		super(new BorderLayout());
		JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
		actions.setOpaque(false);
		actions.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
		Dimension buttonSize = new Dimension(38, 34);

		addButton.setIcon(createActionIcon("io/simpleide/ui/terminal/terminal-add.svg"));
		addButton.setToolTipText("Add terminal");
		addButton.setPreferredSize(buttonSize);
		addButton.setMaximumSize(buttonSize);
		addButton.getAccessibleContext().setAccessibleName("Add terminal");

		closeButton.setIcon(createActionIcon("io/simpleide/ui/terminal/terminal-close.svg"));
		closeButton.setToolTipText("Close selected terminal");
		closeButton.setPreferredSize(buttonSize);
		closeButton.setMaximumSize(buttonSize);
		closeButton.getAccessibleContext().setAccessibleName("Close selected terminal");
		closeButton.setEnabled(false);
		TerminalPanel.applyThemeToActionButton(addButton);
		TerminalPanel.applyThemeToActionButton(closeButton);

		actions.add(addButton);
		actions.add(javax.swing.Box.createVerticalStrut(8));
		actions.add(closeButton);
		this.add(terminalTabs, BorderLayout.CENTER);
		this.add(actions, BorderLayout.EAST);
	}

	@Override
	public void updateUI() {
		super.updateUI();
		if (terminalTabs != null) {
			Color background = UIManager.getColor("TabbedPane.background");
			Color foreground = UIManager.getColor("TabbedPane.foreground");
			Color panelBackground = UIManager.getColor("Panel.background");
			if (background != null) {
				terminalTabs.setBackground(background);
			}
			if (panelBackground != null) {
				setBackground(panelBackground);
			}
			if (foreground != null) {
				terminalTabs.setForeground(foreground);
			}
		}
		if (addButton != null) {
			applyThemeToActionButton(addButton);
			addButton.setIcon(createActionIcon("io/simpleide/ui/terminal/terminal-add.svg"));
		}
		if (closeButton != null) {
			applyThemeToActionButton(closeButton);
			closeButton.setIcon(createActionIcon("io/simpleide/ui/terminal/terminal-close.svg"));
		}
	}

	private static void applyThemeToActionButton(JButton button) {
		button.putClientProperty("JButton.buttonType", null);
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder());
		Color background = UIManager.getColor("Panel.background");
		Color foreground = UIManager.getColor("Button.foreground");
		if (background != null) {
			button.setBackground(background);
		}
		if (foreground != null) {
			button.setForeground(ThemeColors.readable(foreground, background));
		}
	}

	FlatSVGIcon createActionIcon(String resource) {
		Font font = UIManager.getFont("Menu.font");
		if (font == null) {
			font = UIManager.getFont("Label.font");
		}
		int iconSize = Math.max(18, font == null ? 18 : font.getSize());
		return new FlatSVGIcon(resource, iconSize, iconSize)
			.setColorFilter(new FlatSVGIcon.ColorFilter((component, color) -> {
				if (ICON_ACCENT.equals(color)) {
					Color foreground = component == null ? null : component.getForeground();
					if (foreground == null) {
						foreground = UIManager.getColor("Button.foreground");
					}
					if (foreground == null) {
						foreground = UIManager.getColor("Label.foreground");
					}
					return ThemeColors.readable(foreground == null ? Color.GRAY : foreground,
							getIconBackground(component));
				}
				if (Color.WHITE.equals(color)) {
					return this.getIconBackground(component);
				}
				return color;
			}));
	}

	Color getIconBackground(Component component) {
		Color background = component == null ? null : component.getBackground();
		if (background == null) {
			background = UIManager.getColor("Button.background");
		}
		if (background == null) {
			background = UIManager.getColor("Panel.background");
		}
		return background == null ? Color.WHITE : background;
	}

	void setActions(Runnable addAction, Runnable closeAction) {
		addButton.addActionListener(new ActionNode(event -> addAction.run()));
		closeButton.addActionListener(new ActionNode(event -> closeAction.run()));
	}

	void setActionsEnabled(boolean canAdd, boolean canClose, boolean platformSupported) {
		addButton.setEnabled(canAdd);
		if (!platformSupported) {
			addButton.setToolTipText("Terminal is not supported on this platform");
		}
		else if (!canAdd) {
			addButton.setToolTipText("Cannot add another terminal");
		}
		else {
			addButton.setToolTipText("Add terminal");
		}
		closeButton.setEnabled(canClose);
		closeButton.setToolTipText(canClose ? "Close selected terminal"
				: getSelectedSessionId() == null ? "No terminal selected" : "Selected terminal cannot be closed");
	}

	void addTerminalTab(BigInteger sessionId) {
		if (views.containsKey(sessionId)) {
			throw new IllegalArgumentException("Terminal session already exists: " + sessionId + ".");
		}
		if (views.size() >= MAX_TABS) {
			throw new IllegalStateException("A maximum of " + MAX_TABS + " terminal tabs is supported.");
		}
		TerminalView view = new TerminalView(sessionId);
		views.put(sessionId, view);
		terminalTabs.addTab(sessionId.toString(), view);
		terminalTabs.setSelectedComponent(view);
	}

	void showTerminal(BigInteger sessionId, TtyConnector connector) {
		TerminalView view = views.get(sessionId);
		if (view == null) {
			throw new IllegalStateException("No terminal tab exists for session " + sessionId + ".");
		}
		view.show(connector);
	}

	void closeTerminalWidget(BigInteger sessionId) {
		TerminalView view = views.get(sessionId);
		if (view != null) {
			view.closeWidget();
		}
	}

	void removeTerminal(BigInteger sessionId) {
		TerminalView view = views.remove(sessionId);
		if (view == null) {
			return;
		}
		view.removeWidget();
		terminalTabs.remove(view);
	}

	BigInteger getSelectedSessionId() {
		Object selected = terminalTabs.getSelectedComponent();
		for (Map.Entry<BigInteger, TerminalView> entry : views.entrySet()) {
			if (entry.getValue() == selected) {
				return entry.getKey();
			}
		}
		return null;
	}

	private static final class TerminalActionButton extends JButton {

		@Override
		public void updateUI() {
			super.updateUI();
			applyThemeToActionButton(this);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			if (isEnabled()) {
				Color panel = ThemeColors.ui("Panel.background", "control");
				Color state = getModel().isPressed() && getModel().isArmed()
						? ThemeColors.ui("Button.pressedBackground")
						: getModel().isRollover() ? ThemeColors.ui("Button.hoverBackground") : null;
				if (state != null && panel != null) {
					float blend = getModel().isPressed() && getModel().isArmed() ? 0.35f : 0.55f;
					Graphics2D background = (Graphics2D) graphics.create();
					try {
						background.setColor(ThemeColors.blend(state, panel, blend));
						background.fillRect(0, 0, getWidth(), getHeight());
					}
					finally {
						background.dispose();
					}
				}
			}
			super.paintComponent(graphics);
		}

	}

}
