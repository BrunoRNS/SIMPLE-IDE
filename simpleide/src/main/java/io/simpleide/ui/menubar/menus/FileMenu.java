package io.simpleide.ui.menubar.menus;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.ui.menubar.actions.NewFileAction;
import io.simpleide.ui.menubar.actions.NewFolderAction;

public class FileMenu extends JMenu {

  private static final Logger log = LoggerFactory.getLogger(FileMenu.class);

  private JMenuItem newFile;
  private JMenuItem newFolder;

  public FileMenu() {
    super("File");
    createItems();
    configureMenu();
    configureActions();
    log.info("Created File menu");
  }

  private void configureMenu() {
    add(newFile);
    add(newFolder);
  }

  private void configureActions() {
    newFile.addActionListener(e -> new NewFileAction());
    newFolder.addActionListener(e -> new NewFolderAction());
  }

  private void createItems() {
    this.newFile = new JMenuItem("New File");
    this.newFolder = new JMenuItem("New Folder");
  }

}
