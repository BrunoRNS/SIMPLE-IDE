package io.simpleide.ui.menubar;

import io.simpleide.ui.menubar.menus.IdeMenuBar;
import javax.swing.JFrame;

public class MenuBarFactory {
  public static void createMenuBar(
      JFrame frame,
      Runnable createFile,
      Runnable createFolder,
      Runnable setDirectory,
      Runnable saveFile) {
    frame.setJMenuBar(new IdeMenuBar(createFile, createFolder, setDirectory, saveFile));
  }
}
