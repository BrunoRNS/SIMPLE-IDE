package io.simpleide.ui.editor;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import io.simpleide.ui.icon.MaterialIcons;

/**
 * Creates and refreshes the custom headers of editor tabs.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class EditorTabHeaders {

	private final EditorManager editorManager;

	EditorTabHeaders(EditorManager editorManager) {
		this.editorManager = editorManager;
	}

	JPanel createTabHeader(EditorTab tab) {
		JPanel header = new JPanel();
		header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.LINE_AXIS));
		header.setOpaque(false);
		JLabel fileLabel = new JLabel(tab.getName(), tab.getFileIcon(), SwingConstants.LEADING);
		fileLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
		fileLabel.setForeground(tabForeground(true));
		tab.getButtonExit().setAlignmentY(Component.CENTER_ALIGNMENT);
		tab.getButtonExit().setForeground(tabForeground(true));
		header.add(fileLabel);
		header.add(tab.getButtonExit());
		return header;
	}

	void refreshTabHeaderColors() {
		int selectedIndex = editorManager.jTabbedPane.getSelectedIndex();
		for (int tabIndex = 0; tabIndex < editorManager.jTabbedPane.getTabCount(); tabIndex++) {
			Component header = editorManager.jTabbedPane.getTabComponentAt(tabIndex);
			if (!(header instanceof JPanel panel)) {
				continue;
			}
			java.awt.Color foreground = tabForeground(tabIndex == selectedIndex);
			for (Component child : panel.getComponents()) {
				if (foreground != null) {
					child.setForeground(foreground);
				}
			}
		}
	}

	void notifySelectedTab() {
		EditorTab selected = editorManager.getSelectedEditorTab();
		for (var listener : editorManager.selectedTabListeners) {
			listener.accept(selected);
		}
	}

	void notifyDiagnostics(EditorTab tab) {
		if (editorManager.diagnosticsListeners.isEmpty()) {
			return;
		}
		try {
			io.simpleide.thread.ThreadMaster.getInstance().executeOnEDT(() -> {
				for (var listener : editorManager.diagnosticsListeners) {
					listener.accept(tab);
				}
			});
		}
		catch (IllegalStateException error) {
			org.slf4j.LoggerFactory.getLogger(EditorTabHeaders.class)
				.debug("Diagnostics listeners were not notified because the thread manager is shut down.", error);
		}
	}

	static void refreshFileIcons(EditorManager editorManager) {
		if (editorManager == null || editorManager.jTabbedPane == null) {
			return;
		}
		java.awt.Color tabBackground = UIManager.getColor("TabbedPane.background");
		java.awt.Color tabForeground = UIManager.getColor("TabbedPane.foreground");
		if (tabBackground != null) {
			editorManager.jTabbedPane.setBackground(tabBackground);
		}
		if (tabForeground != null) {
			editorManager.jTabbedPane.setForeground(tabForeground);
		}
		int selectedIndex = editorManager.jTabbedPane.getSelectedIndex();
		for (int tabIndex = 0; tabIndex < editorManager.jTabbedPane.getTabCount(); tabIndex++) {
			Component page = editorManager.jTabbedPane.getComponentAt(tabIndex);
			if (!(page instanceof EditorTab tab)) {
				if (page instanceof JComponent component && tabBackground != null) {
					component.setBackground(tabBackground);
					component.setOpaque(true);
				}
				continue;
			}
			Icon icon = MaterialIcons.forFile(tab.getFile().getPath());
			if (tab.getEditor() != null) {
				tab.getEditor().refreshUiFont();
			}
			editorManager.jTabbedPane.setIconAt(tabIndex, icon);
			tab.getButtonExit().refreshIconSize();
			Component header = editorManager.jTabbedPane.getTabComponentAt(tabIndex);
			if (header instanceof JPanel panel && panel.getComponentCount() > 0
					&& panel.getComponent(0) instanceof JLabel label) {
				label.setIcon(icon);
				label.setForeground(tabForeground(tabIndex == selectedIndex));
				tab.getButtonExit().setForeground(tabForeground(tabIndex == selectedIndex));
				panel.revalidate();
				panel.repaint();
			}
		}
	}

	private static java.awt.Color tabForeground(boolean selected) {
		java.awt.Color foreground = UIManager
			.getColor(selected ? "TabbedPane.selectedForeground" : "TabbedPane.foreground");
		return foreground != null ? foreground : UIManager.getColor("Label.foreground");
	}

}
