package io.simpleide.ui.menubar.menus;

import io.simpleide.thread.nodes.edt.listeners.ChangeNode;
import java.awt.Font;
import java.awt.Window;
import java.util.Enumeration;
import javax.swing.JMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

public class SettingsMenu extends JMenu {

  private ThemeMenu themeMenu;
  private JSpinner fontSizeSpinner;

  public SettingsMenu() {
    super("Settings");
    createItems();
    configureActions();
    configureMenu();
  }

  private void configureMenu() {
    add(themeMenu);
    addSeparator();
    add(fontSizeSpinner);
  }

  private void configureActions() {
    fontSizeSpinner.addChangeListener(
        new ChangeNode(
            (e) -> {
              JSpinner source = (JSpinner) e.getSource();

              UIDefaults defaults = UIManager.getDefaults();
              for (Enumeration<?> keys = defaults.keys(); keys.hasMoreElements(); ) {
                Object key = keys.nextElement();
                Object value = defaults.get(key);

                if (value instanceof FontUIResource fontUi) {
                  Font newFont =
                      new FontUIResource(
                          fontUi.getFamily(), fontUi.getStyle(), (int) source.getValue());
                  UIManager.put(key, newFont);
                }
              }
              for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
              }
            }));
  }

  private void createItems() {
    SpinnerNumberModel model_ = new SpinnerNumberModel(12, 1, 72, 1);
    themeMenu = new ThemeMenu();
    fontSizeSpinner = new JSpinner(model_);
  }
}
