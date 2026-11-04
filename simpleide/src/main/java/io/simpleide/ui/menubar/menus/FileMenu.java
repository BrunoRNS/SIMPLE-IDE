package io.simpleide.ui.menubar.menus;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;

public class FileMenu extends JMenu {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileMenu.class);

  private JMenuItem newFile;
  private JMenuItem newFolder;

  public FileMenu() {
    super("File");
    createItems();
    configureMenu();
    configureActions();
    LOGGER.info("Created File menu");
  }

  private void configureMenu() {
    add(newFile);
    add(newFolder);
  }

  private void configureActions() {
    newFile.addActionListener(new ActionNode(e -> LOGGER.info("Create new file")));
    newFolder.addActionListener(new ActionNode(e -> LOGGER.info("Create new folder")));
  }

  private void createItems() {
    this.newFile = new JMenuItem("New File");
    this.newFolder = new JMenuItem("New Folder");
  }

}
