package io.simpleide.ui.menubar.menus;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

import io.simpleide.frame.popup.InfoPopUP;

public class HelpMenu extends JMenu {

  public HelpMenu() {
    super("Help");
    add(createMenuItem("Keyboard Shortcuts", this::showKeyboardShortcuts));
    add(createMenuItem("Machine Information", this::showMachineInformation));
    addSeparator();
    add(createMenuItem("About Simple IDE", this::showAbout));
  }

  private JMenuItem createMenuItem(String label, Runnable action) {
    return new JMenuItem(new AbstractAction(label) {
      @Override
      public void actionPerformed(ActionEvent event) {
        action.run();
      }
    });
  }

  private void showKeyboardShortcuts() {
    String[] columns = {"Shortcut", "Action"};
    DefaultTableModel model_ = new DefaultTableModel(columns, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    JTable shortcutsTable = new JTable(model_);
    shortcutsTable.setFillsViewportHeight(true);

    JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
    panel.add(new JLabel("No keyboard shortcuts are currently configured."));
    panel.add(new JScrollPane(shortcutsTable));

    new InfoPopUP("Keyboard Shortcuts", panel, this).startManaged();
  }

  private void showMachineInformation() {
    Runtime runtime = Runtime.getRuntime();
    String information = String.format(
        "Operating system: %s %s (%s)%n"
            + "Java: %s (%s)%n"
            + "Available processors: %d%n"
            + "Maximum JVM memory: %s",
        System.getProperty("os.name"),
        System.getProperty("os.version"),
        System.getProperty("os.arch"),
        System.getProperty("java.version"),
        System.getProperty("java.vendor"),
        runtime.availableProcessors(),
        formatBytes(runtime.maxMemory()));

    JTextArea textArea = new JTextArea(information);
    textArea.setEditable(false);
    textArea.setOpaque(false);
    textArea.setFocusable(false);
    new InfoPopUP("Machine Information", textArea, this).startManaged();
  }

  private void showAbout() {
    new InfoPopUP(
        "About Simple IDE",
        "Simple IDE\nA lightweight, extensible development environment.",
        this).startManaged();
  }

  private String formatBytes(long bytes) {
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }
}
