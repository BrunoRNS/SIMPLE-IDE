package io.simpleide.ui.sidebar;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.icon.MaterialIcons;
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

/**
 * Vertical activity bar that switches the left sidebar view.
 *
 * <p>
 * Explorer and Search sit at the top and Settings is pinned to the bottom, mirroring the
 * VS Code activity bar. Every click runs through an EDT node and reports the requested
 * {@link SidebarView} to the registered listener. The bar itself only tracks which view
 * is selected; showing, hiding and replacing the view is handled by the sidebar owner.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ActivityBar extends JPanel {

	private static final int BAR_WIDTH = 48;

	private static final int BUTTON_SIZE = 42;

	private final Map<SidebarView, JButton> buttons = new EnumMap<>(SidebarView.class);

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

		addButton(SidebarView.EXPLORER, "folder-open", "Explorer");
		addButton(SidebarView.SEARCH, "search", "Search");
		add(Box.createVerticalGlue());
		addButton(SidebarView.SETTINGS, "settings", "Settings");
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

	private void addButton(SidebarView view, String iconName, String tooltip) {
		JButton button = new JButton(MaterialIcons.forName(iconName, 22));
		button.setToolTipText(tooltip);
		button.getAccessibleContext().setAccessibleName(tooltip);
		button.setFocusable(false);
		button.setContentAreaFilled(true);
		button.setOpaque(true);
		button.setBorder(BorderFactory.createEmptyBorder());
		button.setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
		button.setMinimumSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
		button.setMaximumSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
		button.addActionListener(new ActionNode(event -> selectionListener.accept(view)));
		buttons.put(view, button);
		add(button);
	}

	private void refreshSelection() {
		Color accent = ThemeColors.ui("Component.accentColor", "Component.focusColor");
		Color background = ThemeColors.ui("Button.hoverBackground", "Panel.background");
		Color panelBackground = ThemeColors.ui("Panel.background");
		Color foreground = ThemeColors.ui("Label.foreground");
		for (Map.Entry<SidebarView, JButton> entry : buttons.entrySet()) {
			JButton button = entry.getValue();
			boolean selected = entry.getKey() == selectedView;
			button.setBackground(selected ? background : panelBackground);
			button.setForeground(foreground);
			if (selected) {
				button.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 2, 0, 0, accent == null ? foreground : accent),
						BorderFactory.createEmptyBorder(0, 6, 0, 0)));
			}
			else {
				button.setBorder(BorderFactory.createEmptyBorder());
			}
		}
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

}
