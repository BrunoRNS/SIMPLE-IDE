package io.simpleide.ui.menubar.menus;

import java.awt.GridLayout;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

import io.simpleide.SimpleIDEVersion;
import io.simpleide.frame.popup.InfoPopUP;
import io.simpleide.thread.nodes.edt.listeners.SwingActionNode;

/**
 * Help menu with keyboard shortcuts, machine information and the about dialog.
 *
 * <p>
 * All three entries open informational popups. Machine information reports the operating
 * system, Java runtime, processor count and maximum heap, and the about dialog reads the
 * packaged application version.
 *
 * <p>
 * Covered by {@code HelpMenuTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class HelpMenu extends JMenu {

	/**
	 * Creates the help menu and its items.
	 */
	public HelpMenu() {
		super("Help");
		add(createMenuItem("Keyboard Shortcuts", this::showKeyboardShortcuts));
		add(createMenuItem("Machine Information", this::showMachineInformation));
		addSeparator();
		add(createMenuItem("About Simple IDE", this::showAbout));
	}

	private JMenuItem createMenuItem(String label, Runnable action) {
		return new JMenuItem(new SwingActionNode(label, action));
	}

	private void showKeyboardShortcuts() {
		String[] columns = { "Shortcut", "Action" };
		DefaultTableModel model_ = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		JTable shortcutsTable = new JTable(model_);
		shortcutsTable.setFillsViewportHeight(true);
		model_.addRow(new Object[] { "Ctrl+S", "Save the current file" });
		model_.addRow(new Object[] { "Ctrl+F / Ctrl+H", "Find and replace text" });
		model_.addRow(new Object[] { "Ctrl+Click / Ctrl+B / F12", "Go to definition" });

		JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
		panel.add(new JScrollPane(shortcutsTable));

		new InfoPopUP("Keyboard Shortcuts", panel, this).startManaged();
	}

	private void showMachineInformation() {
		Runtime runtime = Runtime.getRuntime();
		String information = String.format(
				"Operating system: %s %s (%s)%n" + "Java: %s (%s)%n" + "Available processors: %d%n"
						+ "Maximum JVM memory: %s",
				System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
				System.getProperty("java.version"), System.getProperty("java.vendor"), runtime.availableProcessors(),
				formatBytes(runtime.maxMemory()));

		JTextArea textArea = new JTextArea(information);
		textArea.setEditable(false);
		textArea.setOpaque(false);
		textArea.setFocusable(false);
		new InfoPopUP("Machine Information", textArea, this).startManaged();
	}

	private void showAbout() {
		new InfoPopUP("About Simple IDE",
				"Simple IDE " + SimpleIDEVersion.VERSION + "\nA lightweight, extensible development environment.", this)
			.startManaged();
	}

	private String formatBytes(long bytes) {
		return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
	}

}
