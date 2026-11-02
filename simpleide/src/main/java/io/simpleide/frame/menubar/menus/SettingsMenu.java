package io.simpleide.frame.menubar.menus;

import javax.swing.JMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import io.simpleide.frame.menubar.actions.FontSizeAction;

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
    fontSizeSpinner.addChangeListener(new FontSizeAction());
  }

  private void createItems() {
    SpinnerNumberModel model = new SpinnerNumberModel(12, 1, 72, 1);
    themeMenu = new ThemeMenu();
    fontSizeSpinner = new JSpinner(model);
  }
}
