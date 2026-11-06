package io.simpleide.ui.menubar.menus;

import java.util.Objects;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;

public class FileMenu extends JMenu {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileMenu.class);

  private JMenuItem newFile;
  private JMenuItem newFolder;
  private JMenuItem setDirectory;

  public FileMenu(Runnable createFile, Runnable createFolder, Runnable setDirectory) {
    super("File");
    Objects.requireNonNull(createFile, "createFile");
    Objects.requireNonNull(createFolder, "createFolder");
    Objects.requireNonNull(setDirectory, "setDirectory");
    createItems();
    configureMenu();
    configureActions(createFile, createFolder, setDirectory);
    LOGGER.info("Created File menu");
  }

  private void configureMenu() {
    add(newFile);
    add(newFolder);
    add(setDirectory);
  }

  private void configureActions(Runnable createFile, Runnable createFolder, Runnable setDirectory) {
    newFile.addActionListener(new ActionNode(e -> createFile.run()));
    newFolder.addActionListener(new ActionNode(e -> createFolder.run()));
    this.setDirectory.addActionListener(new ActionNode(e -> setDirectory.run()));
  }

  private void createItems() {
    this.newFile = new JMenuItem("New File");
    this.newFolder = new JMenuItem("New Folder");
    this.setDirectory = new JMenuItem("Set directory");
  }

}
