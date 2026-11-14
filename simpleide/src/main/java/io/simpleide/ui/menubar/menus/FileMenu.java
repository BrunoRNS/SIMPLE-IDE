package io.simpleide.ui.menubar.menus;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileMenu extends JMenu {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileMenu.class);

  private JMenuItem newFile;
  private JMenuItem saveFile;
  private JMenuItem newFolder;
  private JMenuItem setDirectory;

  public FileMenu(
      Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile) {
    super("File");
    Objects.requireNonNull(createFile, "createFile");
    Objects.requireNonNull(createFolder, "createFolder");
    Objects.requireNonNull(setDirectory, "setDirectory");
    Objects.requireNonNull(saveFile, "saveFile");
    createItems();
    configureMenu();
    configureActions(createFile, createFolder, setDirectory, saveFile);
    LOGGER.info("Created File menu");
  }

  private void configureMenu() {
    add(newFile);
    add(saveFile);
    add(newFolder);
    add(setDirectory);
  }

  private void configureActions(
      Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile) {
    newFile.addActionListener(new ActionNode(e -> createFile.run()));
    this.saveFile.addActionListener(new ActionNode(e -> saveFile.run()));
    newFolder.addActionListener(new ActionNode(e -> createFolder.run()));
    this.setDirectory.addActionListener(new ActionNode(e -> setDirectory.run()));
  }

  private void createItems() {
    this.newFile = new JMenuItem("New File");
    this.saveFile = new JMenuItem("Save");
    this.saveFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
    this.newFolder = new JMenuItem("New Folder");
    this.setDirectory = new JMenuItem("Set directory");
  }
}
