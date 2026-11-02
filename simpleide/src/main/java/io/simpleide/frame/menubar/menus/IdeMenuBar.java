package io.simpleide.frame.menubar.menus;

import javax.swing.JMenuBar;

public class IdeMenuBar extends JMenuBar {

  private FileMenu fileMenu;
  private SettingsMenu settingsMenu;
  private HelpMenu helpMenu;

  public IdeMenuBar() {
    createItems();
    addMenus();
  }

  private void createItems() {
    fileMenu = new FileMenu();
    settingsMenu = new SettingsMenu();
    helpMenu = new HelpMenu();
  }

  private void addMenus() {
    add(fileMenu);
    add(settingsMenu);
    add(helpMenu);
  }
}
