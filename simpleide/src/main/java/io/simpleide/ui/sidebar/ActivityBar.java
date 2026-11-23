package io.simpleide.ui.sidebar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.icon.ThemedSvgIcon;

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
 * The buttons use the bundled 9:16 SVG artwork, centred inside fixed-size buttons so the
 * selected state only changes colours: the icon of the selected view is painted with the
 * theme accent colour while every other icon keeps a readable theme foreground.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ActivityBar extends JPanel {

	private static final int BAR_WIDTH = 48;

	private static final int BUTTON_WIDTH = 42;

	private static final int BUTTON_HEIGHT = 52;

	private static final int ICON_HEIGHT = 48;

	private static final String ICON_PACKAGE = "io.simpleide.icon";

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

		addButton(SidebarView.EXPLORER, "simpleide-explorer-9x16.svg", "Explorer");
		addButton(SidebarView.SEARCH, "simpleide-search-9x16.svg", "Search");
		add(Box.createVerticalGlue());
		addButton(SidebarView.SETTINGS, "simpleide-settings-9x16.svg", "Settings");
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
		refreshSelection();
		repaint();
	}

	private void addButton(SidebarView view, String iconFile, String tooltip) {
		ActivityButton button = new ActivityButton();
		button.setIcon(new ThemedSvgIcon(ICON_PACKAGE, iconFile, ICON_HEIGHT, () -> iconColor(view, button)));
		button.setToolTipText(tooltip);
		button.getAccessibleContext().setAccessibleName(tooltip);
		button.setFocusable(false);
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		button.setAlignmentX(CENTER_ALIGNMENT);
		button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
		button.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
		button.setMaximumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
		button.addActionListener(new ActionNode(event -> selectionListener.accept(view)));
		buttons.put(view, button);
		add(button);
	}

	private void refreshSelection() {
		for (Map.Entry<SidebarView, ActivityButton> entry : buttons.entrySet()) {
			entry.getValue().setSelectedState(entry.getKey() == selectedView);
			entry.getValue().repaint();
		}
	}

	private Color iconColor(SidebarView view, ActivityButton button) {
		Color background = ThemeColors.ui("Panel.background", "control", "Tree.background");
		if (background == null) {
			background = button.getBackground();
		}
		if (background == null) {
			background = Color.DARK_GRAY;
		}
		Color foreground = ThemeColors.ui("Label.foreground", "Panel.foreground");
		if (foreground == null) {
			foreground = ThemeColors.isLight(background) ? Color.BLACK : Color.WHITE;
		}
		if (view == selectedView) {
			Color accent = ThemeColors.ui("Component.accentColor", "Component.focusColor");
			return ThemeColors.readable(accent == null ? foreground : accent, background,
					ThemeColors.MIN_LARGE_TEXT_CONTRAST);
		}
		Color iconColor = button.getModel().isRollover() ? foreground : ThemeColors.blend(foreground, background, 0.3f);
		return ThemeColors.readable(iconColor, background, ThemeColors.MIN_LARGE_TEXT_CONTRAST);
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Color background = UIManager.getColor("Panel.background");
		if (background != null) {
			graphics.setColor(background);
			graphics.fillRect(0, 0, getWidth(), getHeight());
		}
		super.paintComponent(graphics);
	}

	/** Button whose only changing visual state is its icon tint. */
	private final class ActivityButton extends JButton {

		void setSelectedState(boolean selected) {
			getAccessibleContext().setAccessibleDescription(selected ? "Selected" : null);
		}

	}

}
