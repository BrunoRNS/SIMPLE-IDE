package io.simpleide.ui.menubar.menus;

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
	 * @param setDirectory action that selects the working directory
	 * @param saveFile action that saves the current file
	 */
	public IdeMenuBar(Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile) {
		createItems(createFile, createFolder, setDirectory, saveFile);
		addMenus();
	}

	private void createItems(Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile) {
		fileMenu = new FileMenu(createFile, createFolder, setDirectory, saveFile);
		settingsMenu = new SettingsMenu();
		helpMenu = new HelpMenu();
	}

	private void addMenus() {
		add(fileMenu);
		add(settingsMenu);
		add(helpMenu);
	}

}
