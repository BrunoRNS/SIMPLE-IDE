package io.simpleide.ui.menubar;

import javax.swing.JFrame;

import io.simpleide.ui.menubar.menus.IdeMenuBar;

public class MenuBarFactory {
  public static void createMenuBar(JFrame frame, Runnable createFile, Runnable createFolder, Runnable setDirectory) {
    frame.setJMenuBar(new IdeMenuBar(createFile, createFolder, setDirectory));
  }
}
