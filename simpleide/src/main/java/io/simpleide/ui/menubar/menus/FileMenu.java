package io.simpleide.ui.menubar.menus;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    newFile.addActionListener(e -> {
      // TODO: Implement new file action
      log.info("New file");
    });
    newFolder.addActionListener(e -> {

      // TODO: Implement new folder action
      log.warn("New folder");
      log.error("New folder");
    });
  }

  private void createItems() {
    this.newFile = new JMenuItem("New File");
    this.newFolder = new JMenuItem("New Folder");
  }

}
