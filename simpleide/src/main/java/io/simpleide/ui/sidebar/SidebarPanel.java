package io.simpleide.ui.sidebar;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.JPanel;

/**
 * Left sidebar that swaps between Explorer, Search and Settings.
 *
 * <p>
 * The panel uses a card layout and remembers the last view. It does not decide when the
 * sidebar is visible; the frame toggles the panel with {@code Ctrl+B} and keeps the
 * activity bar selection in sync.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class SidebarPanel extends JPanel {

	private final CardLayout cards = new CardLayout();

	private final Map<SidebarView, java.awt.Component> views = new EnumMap<>(SidebarView.class);

	private SidebarView currentView = SidebarView.EXPLORER;

	/**
	 * Creates an empty sidebar.
	 */
	public SidebarPanel() {
		setLayout(cards);
		setPreferredSize(new Dimension(300, 0));
		setMinimumSize(new Dimension(180, 0));
	}

	/**
	 * Adds a view to the sidebar.
	 * @param view view identifier
	 * @param component component shown for the view
	 */
	public void addView(SidebarView view, Component component) {
		SidebarView key = Objects.requireNonNull(view, "view");
		Component value = Objects.requireNonNull(component, "component");
		views.put(key, value);
		add(value, key.name());
	}

	/**
	 * Shows a view and remembers it as the current view.
	 * @param view view to show
	 */
	public void showView(SidebarView view) {
		SidebarView key = Objects.requireNonNull(view, "view");
		if (!views.containsKey(key)) {
			throw new IllegalArgumentException("No sidebar view registered for " + key + ".");
		}
		currentView = key;
		cards.show(this, key.name());
	}

	/**
	 * Returns the view currently selected in the card layout.
	 * @return the current view
	 */
	public SidebarView getCurrentView() {
		return currentView;
	}

}
