package io.simpleide.ui.menubar.menus;

import javax.swing.JMenuBar;

public class IdeMenuBar extends JMenuBar {

  private FileMenu fileMenu;
  private SettingsMenu settingsMenu;
  private HelpMenu helpMenu;

  public IdeMenuBar(Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile) {
    createItems(createFile, createFolder, setDirectory, saveFile);
    addMenus();
  }

  private void createItems(Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile) {
    fileMenu = new FileMenu(createFile, createFolder, setDirectory, saveFile);
    settingsMenu = new SettingsMenu();
    helpMenu = new HelpMenu();
  }

  private void addMenus() {
    add(fileMenu);
    add(settingsMenu);
    add(helpMenu);
  }
}
