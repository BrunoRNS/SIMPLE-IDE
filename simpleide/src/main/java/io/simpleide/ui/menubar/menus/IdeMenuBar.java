package io.simpleide.ui.menubar.menus;

import io.simpleide.settings.IdeSettings;
import javax.swing.JMenuBar;

/**
 * Application menu bar containing the File, Settings and Help menus.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class IdeMenuBar extends JMenuBar {

	private FileMenu fileMenu;

	private SettingsMenu settingsMenu;

	private HelpMenu helpMenu;

	/**
	 * Creates the menu bar and its menus.
	 * @param createFile action that creates a new file
	 * @param createFolder action that creates a new folder
	 * @param openFolder action that opens a folder
	 * @param closeFolder action that closes the open folder
	 * @param saveFile action that saves the current file
	 */
	public IdeMenuBar(Runnable createFile, Runnable createFolder, Runnable openFolder, Runnable closeFolder,
			Runnable saveFile) {
		createItems(createFile, createFolder, openFolder, closeFolder, saveFile);
		addMenus();
	}

	/**
	 * Refreshes the shortcut labels shown in the menus.
	 * @param settings settings containing the configured shortcuts
	 */
	public void applyKeybindings(IdeSettings settings) {
		fileMenu.applySettings(settings);
	}

	private void createItems(Runnable createFile, Runnable createFolder, Runnable openFolder, Runnable closeFolder,
			Runnable saveFile) {
		fileMenu = new FileMenu(createFile, createFolder, openFolder, closeFolder, saveFile);
		settingsMenu = new SettingsMenu();
		helpMenu = new HelpMenu();
	}

	private void addMenus() {
		add(fileMenu);
		add(settingsMenu);
		add(helpMenu);
	}

}
