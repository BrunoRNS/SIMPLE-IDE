package io.simpleide.frame.menubar.menus;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

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
    DefaultTableModel model = new DefaultTableModel(columns, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    JTable shortcutsTable = new JTable(model);
    shortcutsTable.setFillsViewportHeight(true);

    JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
    panel.add(new JLabel("No keyboard shortcuts are currently configured."));
    panel.add(new JScrollPane(shortcutsTable));

    JOptionPane.showMessageDialog(
        this, panel, "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
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
    JOptionPane.showMessageDialog(
        this, textArea, "Machine Information", JOptionPane.INFORMATION_MESSAGE);
  }

  private void showAbout() {
    JOptionPane.showMessageDialog(
        this,
        "Simple IDE\nA lightweight, extensible development environment.",
        "About Simple IDE",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private String formatBytes(long bytes) {
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }
}
