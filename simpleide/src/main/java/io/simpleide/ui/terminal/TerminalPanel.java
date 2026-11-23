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

	/**
	 * Minimum WCAG contrast ratio used by the deprecated legacy icon helpers.
	 * @deprecated since v1.1.0, use {@link ThemeColors#MIN_LARGE_TEXT_CONTRAST} instead
	 */
	@Deprecated
	private static final double MIN_ICON_CONTRAST = 4.5;

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
					Color accent = UIManager.getColor("Component.accentColor");
					if (accent == null) {
						accent = component == null ? null : component.getForeground();
					}
					if (accent == null) {
						accent = UIManager.getColor("Button.foreground");
					}
					if (accent == null) {
						accent = UIManager.getColor("Label.foreground");
					}
					return ThemeColors.readable(accent == null ? Color.GRAY : accent, getIconBackground(component),
							ThemeColors.MIN_LARGE_TEXT_CONTRAST);
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

	/**
	 * Returns a foreground color that stays readable on the given background.
	 *
	 * <p>
	 * Legacy helper kept for code written against v1.0.0beta. New code must use
	 * {@link ThemeColors#readable(Color, Color, double)} with
	 * {@link ThemeColors#MIN_LARGE_TEXT_CONTRAST}.
	 * @param preferred preferred foreground color
	 * @param background background color behind the icon
	 * @return a readable foreground color
	 * @deprecated since v1.1.0, use
	 * {@code ThemeColors.readable(preferred, background, ThemeColors.MIN_LARGE_TEXT_CONTRAST)}
	 * instead
	 */
	@Deprecated
	static Color getAccessibleIconForeground(Color preferred, Color background) {
		Color effectiveBackground = background == null ? Color.WHITE : background;
		Color effectiveForeground = preferred == null ? Color.DARK_GRAY : preferred;
		if (contrastRatio(effectiveForeground, effectiveBackground) >= MIN_ICON_CONTRAST) {
			return effectiveForeground;
		}
		return contrastRatio(Color.BLACK, effectiveBackground) >= contrastRatio(Color.WHITE, effectiveBackground)
				? Color.BLACK : Color.WHITE;
	}

	/**
	 * Calculates the WCAG contrast ratio, compositing translucent colors over white.
	 * @param foreground foreground color
	 * @param background background color
	 * @return the contrast ratio between the two colors
	 * @deprecated since v1.1.0, use {@link ThemeColors#contrastRatio(Color, Color)}
	 * instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private static double contrastRatio(Color foreground, Color background) {
		Color opaqueBackground = composite(background, Color.WHITE);
		Color opaqueForeground = composite(foreground, opaqueBackground);
		double foregroundLuminance = relativeLuminance(opaqueForeground);
		double backgroundLuminance = relativeLuminance(opaqueBackground);
		double lighter = Math.max(foregroundLuminance, backgroundLuminance);
		double darker = Math.min(foregroundLuminance, backgroundLuminance);
		return (lighter + 0.05) / (darker + 0.05);
	}

	/**
	 * Composites a translucent color over an opaque background.
	 * @param color color to composite
	 * @param background opaque background color
	 * @return the resulting opaque color
	 * @deprecated since v1.1.0, use {@link ThemeColors#blend(Color, Color, float)}
	 * instead; that method takes an explicit blend ratio instead of the alpha channel
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private static Color composite(Color color, Color background) {
		if (color.getAlpha() == 255) {
			return color;
		}
		double alpha = color.getAlpha() / 255.0;
		return new Color((int) Math.round(color.getRed() * alpha + background.getRed() * (1 - alpha)),
				(int) Math.round(color.getGreen() * alpha + background.getGreen() * (1 - alpha)),
				(int) Math.round(color.getBlue() * alpha + background.getBlue() * (1 - alpha)));
	}

	/**
	 * Returns the relative luminance of a color.
	 * @param color color to inspect
	 * @return luminance between {@code 0} and {@code 1}
	 * @deprecated since v1.1.0, use {@link ThemeColors#relativeLuminance(Color)} instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private static double relativeLuminance(Color color) {
		return 0.2126 * linearize(color.getRed()) + 0.7152 * linearize(color.getGreen())
				+ 0.0722 * linearize(color.getBlue());
	}

	/**
	 * Converts a color channel to linear RGB.
	 * @param channel channel value between {@code 0} and {@code 255}
	 * @return the linearized channel
	 * @deprecated since v1.1.0, the linearization helper moved into {@link ThemeColors}
	 * and is internal to that class; use {@link ThemeColors#relativeLuminance(Color)} for
	 * luminance calculations
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private static double linearize(int channel) {
		double normalized = channel / 255.0;
		return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
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
