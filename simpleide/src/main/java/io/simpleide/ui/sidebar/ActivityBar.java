package io.simpleide.ui.sidebar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.sidebar.icons.SidebarIconResources;
import io.simpleide.ui.sidebar.icons.SidebarIconResources.State;
import io.simpleide.ui.sidebar.icons.SidebarIconResources.Type;

/**
 * Vertical activity bar that switches the left sidebar view.
 *
 * <p>
 * Explorer and Search sit at the top and Settings is pinned to the bottom, mirroring the
 * VS Code activity bar. Every click runs through an EDT node and reports the requested
 * {@link SidebarView} to the registered listener. The bar itself only tracks which view
 * is selected; showing, hiding and replacing the view is handled by the sidebar owner.
 *
 * <p>
 * Each button loads a dedicated SVG for the active theme and its idle, hover or selected
 * state. The selected view also receives a subtle background and an accent marker.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ActivityBar extends JPanel {

	private static final int BAR_WIDTH = 48;

	private static final int BUTTON_WIDTH = 42;

	private static final int BUTTON_HEIGHT = 52;

	private final Map<SidebarView, ActivityButton> buttons = new EnumMap<>(SidebarView.class);

	private SidebarView selectedView = SidebarView.EXPLORER;

	private Consumer<SidebarView> selectionListener = view -> {
	};

	/**
	 * Creates the activity bar with its three view buttons.
	 */
	public ActivityBar() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setPreferredSize(new Dimension(BAR_WIDTH, 0));
		setMinimumSize(new Dimension(BAR_WIDTH, 0));
		setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));

		addButton(SidebarView.EXPLORER, Type.EXPLORER, "Explorer");
		addButton(SidebarView.SEARCH, Type.SEARCH, "Search");
		add(Box.createVerticalGlue());
		addButton(SidebarView.SETTINGS, Type.SETTINGS, "Settings");
		refreshSelection();
	}

	/**
	 * Registers the listener notified when a view is requested.
	 * @param listener listener invoked on the EDT, or {@code null} to clear it
	 */
	public void setSelectionListener(Consumer<SidebarView> listener) {
		selectionListener = listener == null ? view -> {
		} : listener;
	}

	/**
	 * Marks a view as selected.
	 * @param view selected view
	 */
	public void setSelected(SidebarView view) {
		selectedView = Objects.requireNonNull(view, "view");
		refreshSelection();
	}

	/**
	 * Returns the selected view.
	 * @return the view highlighted by the activity bar
	 */
	public SidebarView getSelected() {
		return selectedView;
	}

	/**
	 * Reapplies the colors of the current theme to the view buttons.
	 */
	public void refreshTheme() {
		ThemeManager.ThemeType theme = ThemeManager.getInstance().getCurrentTheme();
		buttons.values().forEach(button -> button.setTheme(theme));
		repaint();
	}

	private void addButton(SidebarView view, Type type, String tooltip) {
		ActivityButton button = new ActivityButton(type, ThemeManager.getInstance().getCurrentTheme());
		button.setToolTipText(tooltip);
		button.getAccessibleContext().setAccessibleName(tooltip);
		button.setFocusable(false);
		button.setContentAreaFilled(false);
		button.setRolloverEnabled(true);
		button.setOpaque(false);
		button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		button.setAlignmentX(CENTER_ALIGNMENT);
		button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
		button.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
		button.setMaximumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
		button.addActionListener(new ActionNode(event -> selectionListener.accept(view)));
		button.getModel().addChangeListener(event -> button.refreshIcon());
		buttons.put(view, button);
		add(button);
	}

	private void refreshSelection() {
		for (Map.Entry<SidebarView, ActivityButton> entry : buttons.entrySet()) {
			entry.getValue().setSelectedState(entry.getKey() == selectedView);
			entry.getValue().repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Color background = ThemePalette.of(ThemeManager.getInstance().getCurrentTheme()).surface();
		graphics.setColor(background);
		graphics.fillRect(0, 0, getWidth(), getHeight());
		super.paintComponent(graphics);
	}

	private final class ActivityButton extends JButton {

		private final Type type;

		private ThemeManager.ThemeType theme;

		private boolean selected;

		ActivityButton(Type type, ThemeManager.ThemeType theme) {
			this.type = type;
			this.theme = theme;
			refreshIcon();
		}

		void setSelectedState(boolean selected) {
			this.selected = selected;
			getAccessibleContext().setAccessibleDescription(selected ? "Selected" : null);
			refreshIcon();
		}

		void setTheme(ThemeManager.ThemeType theme) {
			this.theme = theme;
			refreshIcon();
		}

		void refreshIcon() {
			State state = selected ? State.SELECTED : getModel().isRollover() ? State.HOVER : State.IDLE;
			setIcon(SidebarIconResources.get(theme, state, type));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				ThemePalette palette = ThemePalette.of(theme);
				if (selected) {
					g.setColor(new Color(palette.accent().getRed(), palette.accent().getGreen(),
							palette.accent().getBlue(), 24));
					g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
					g.setColor(palette.accent());
					g.fillRoundRect(0, 15, 3, getHeight() - 30, 3, 3);
				}
				else if (getModel().isRollover()) {
					Color foreground = palette.foreground();
					g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 16));
					g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
				}
			}
			finally {
				g.dispose();
			}
			super.paintComponent(graphics);
		}

	}

}
