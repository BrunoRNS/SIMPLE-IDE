package io.simpleide.frame.menubar;

import javax.swing.JFrame;

import io.simpleide.frame.menubar.menus.IdeMenuBar;

public class MenuBarFactory {
  public static void createMenuBar(JFrame frame) {
    frame.setJMenuBar(new IdeMenuBar());
  }
}
