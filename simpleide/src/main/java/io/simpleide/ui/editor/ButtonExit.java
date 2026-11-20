package io.simpleide.ui.editor;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.icon.MaterialIcons;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

/**
 * Close button shown in an editor tab header.
 *
 * <p>
 * The button paints either a close cross or an edited dot and routes clicks through an
 * EDT node to {@link EditorManager#closeTab(EditorTab)}, so the tab's unsaved-changes
 * prompt is honored.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 */
public class ButtonExit extends JButton {

	@SuppressWarnings("unused")
	private final EditorTab tab;

	private Icon iconX;

	private Icon iconDot;

	private boolean edited;

	/**
	 * Creates a close button for a tab.
	 * @param tab tab closed when the button is pressed
	 */
	public ButtonExit(EditorTab tab) {
		super();
		this.tab = tab;
		refreshIconSize();
		setIcon(iconX);
		setToolTipText("Close " + tab.getName());
		addActionListener(new ActionNode(e -> {
			EditorManager editorManager = EditorManager.getInstance();
			if (editorManager != null) {
				editorManager.closeTab(tab);
			}
		}));
		setBorder(BorderFactory.createEmptyBorder());
		setContentAreaFilled(false);
		setFocusable(false);
		applyTheme();
	}

	@Override
	public void updateUI() {
		super.updateUI();
		applyTheme();
	}

	private void applyTheme() {
		Color foreground = UIManager.getColor("TabbedPane.foreground");
		if (foreground != null) {
			setForeground(foreground);
		}
		refreshIconSize();
	}

	/**
	 * Switches the icon to the edited marker.
	 */
	public void setEdited() {
		edited = true;
		setIcon(iconDot);
	}

	/**
	 * Switches the icon back to the close cross.
	 */
	public void setNotEdited() {
		edited = false;
		setIcon(iconX);
	}

	/** Rebuilds close and edited icons using the current application font size. */
	public void refreshIconSize() {
		iconX = new TabIcon(false, MaterialIcons.tabIconSize());
		iconDot = new TabIcon(true, MaterialIcons.tabIconSize());
		setIcon(edited ? iconDot : iconX);
	}

	private static final class TabIcon implements Icon {

		private final boolean edited;

		private final int size;

		private TabIcon(boolean edited, int size) {
			this.edited = edited;
			this.size = size;
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D g2 = (Graphics2D) graphics.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				if (edited) {
					Color accent = UIManager.getColor("Component.accentColor");
					if (accent == null) {
						accent = UIManager.getColor("Actions.Yellow");
					}
					g2.setColor(accent == null ? new Color(0xD9A441) : accent);
					int diameter = Math.max(6, size / 2);
					g2.fillOval(x + (size - diameter) / 2, y + (size - diameter) / 2, diameter, diameter);
				}
				else {
					Color foreground = component.getForeground();
					g2.setColor(foreground != null ? foreground : Color.GRAY);
					g2.setStroke(new BasicStroke(Math.max(1.5f, size / 10.0f), BasicStroke.CAP_ROUND,
							BasicStroke.JOIN_ROUND));
					int inset = Math.max(3, size / 4);
					g2.drawLine(x + inset, y + inset, x + size - inset, y + size - inset);
					g2.drawLine(x + size - inset, y + inset, x + inset, y + size - inset);
				}
			}
			finally {
				g2.dispose();
			}
		}

	}

}
