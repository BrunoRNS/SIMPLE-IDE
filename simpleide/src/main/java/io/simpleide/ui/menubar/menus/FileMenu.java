package io.simpleide.ui.menubar.menus;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.settings.IdeAction;
import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.SettingsService;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;

/**
 * File menu with new file, new folder, open folder, close folder and save items.
 *
 * <p>
 * Items are ordered by creation, navigation and save actions. Each item forwards to the
 * callback supplied at construction through an EDT node, so menu work is managed by the
 * thread tree. Save is bound to Ctrl+S; the shortcut labels of New File, Open Folder and
 * Close Folder come from the persistent settings and can be refreshed with
 * {@link #applySettings(IdeSettings)}.
 *
 * <p>
 * Covered by {@code FileMenuTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class FileMenu extends JMenu {

	private static final Logger LOGGER = LoggerFactory.getLogger(FileMenu.class);

	private JMenuItem newFile;

	private JMenuItem newFolder;

	private JMenuItem openFolder;

	private JMenuItem closeFolder;

	private JMenuItem saveFile;

	/**
	 * Creates the file menu from the stored settings.
	 * @param createFile action that creates a new file
	 * @param createFolder action that creates a new folder
	 * @param openFolder action that opens a folder
	 * @param closeFolder action that closes the open folder
	 * @param saveFile action that saves the current file
	 * @throws NullPointerException when any action is {@code null}
	 */
	public FileMenu(Runnable createFile, Runnable createFolder, Runnable openFolder, Runnable closeFolder,
			Runnable saveFile) {
		super("File");
		Objects.requireNonNull(createFile, "createFile");
		Objects.requireNonNull(createFolder, "createFolder");
		Objects.requireNonNull(openFolder, "openFolder");
		Objects.requireNonNull(closeFolder, "closeFolder");
		Objects.requireNonNull(saveFile, "saveFile");
		createItems();
		configureMenu();
		configureActions(createFile, createFolder, openFolder, closeFolder, saveFile);
		applySettings(SettingsService.getInstance().getSettings());
		LOGGER.info("Created File menu");
	}

	/**
	 * Refreshes the shortcut labels from the settings.
	 * @param settings settings containing the configured shortcuts
	 */
	public void applySettings(IdeSettings settings) {
		Objects.requireNonNull(settings, "settings");
		newFile.setAccelerator(IdeAction.parseKeyStroke(settings.keybindingOf(IdeAction.NEW_FILE)));
		openFolder.setAccelerator(IdeAction.parseKeyStroke(settings.keybindingOf(IdeAction.OPEN_FOLDER)));
		closeFolder.setAccelerator(IdeAction.parseKeyStroke(settings.keybindingOf(IdeAction.CLOSE_FOLDER)));
	}

	private void configureMenu() {
		add(newFile);
		add(newFolder);
		add(openFolder);
		add(closeFolder);
		add(saveFile);
	}

	private void configureActions(Runnable createFile, Runnable createFolder, Runnable openFolder, Runnable closeFolder,
			Runnable saveFile) {
		newFile.addActionListener(new ActionNode(event -> createFile.run()));
		newFolder.addActionListener(new ActionNode(event -> createFolder.run()));
		this.openFolder.addActionListener(new ActionNode(event -> openFolder.run()));
		this.closeFolder.addActionListener(new ActionNode(event -> closeFolder.run()));
		this.saveFile.addActionListener(new ActionNode(event -> saveFile.run()));
	}

	private void createItems() {
		this.newFile = new JMenuItem("New File");
		this.newFolder = new JMenuItem("New Folder");
		this.openFolder = new JMenuItem("Open Folder");
		this.closeFolder = new JMenuItem("Close Folder");
		this.saveFile = new JMenuItem("Save");
		this.saveFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
	}

}
