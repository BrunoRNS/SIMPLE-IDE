package io.simpleide.ui.menubar.menus;

import javax.swing.JMenuBar;

import io.simpleide.settings.IdeSettings;

/**
 * Application menu bar containing the File, Settings, Execute and Help menus.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public class IdeMenuBar extends JMenuBar {

	private FileMenu fileMenu;

	private SettingsMenu settingsMenu;

	private ExecuteMenu executeMenu;

	private HelpMenu helpMenu;

	/**
	 * Creates the legacy menu bar without an Execute menu.
	 * @param createFile action that creates a new file
	 * @param createFolder action that creates a new folder
	 * @param openFolder action that opens a folder
	 * @param closeFolder action that closes the open folder
	 * @param saveFile action that saves the current file
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public IdeMenuBar(Runnable createFile, Runnable createFolder, Runnable openFolder, Runnable closeFolder,
			Runnable saveFile) {
		createItems(createFile, createFolder, openFolder, closeFolder, saveFile, () -> {
		}, () -> {
		});
		addMenus(false);
	}

	/**
	 * Creates the menu bar and its menus.
	 * @param createFile action that creates a new file
	 * @param createFolder action that creates a new folder
	 * @param openFolder action that opens a folder
	 * @param closeFolder action that closes the open folder
	 * @param saveFile action that saves the current file
	 * @param run action that runs the selected program
	 * @param debug action that starts a debug session
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public IdeMenuBar(Runnable createFile, Runnable createFolder, Runnable openFolder, Runnable closeFolder,
			Runnable saveFile, Runnable run, Runnable debug) {
		createItems(createFile, createFolder, openFolder, closeFolder, saveFile, run, debug);
		addMenus(true);
	}

	/**
	 * Refreshes the shortcut labels shown in the menus.
	 * @param settings settings containing the configured shortcuts
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void applyKeybindings(IdeSettings settings) {
		fileMenu.applySettings(settings);
		executeMenu.applySettings(settings);
	}

	/**
	 * Enables or disables the Debug command.
	 * @param enabled {@code false} when no debug adapter is available
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void setDebugEnabled(boolean enabled) {
		executeMenu.setDebugEnabled(enabled);
	}

	/**
	 * Returns the Execute menu.
	 * @return the Execute menu
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public ExecuteMenu getExecuteMenu() {
		return executeMenu;
	}

	private void createItems(Runnable createFile, Runnable createFolder, Runnable openFolder, Runnable closeFolder,
			Runnable saveFile, Runnable run, Runnable debug) {
		fileMenu = new FileMenu(createFile, createFolder, openFolder, closeFolder, saveFile);
		settingsMenu = new SettingsMenu();
		executeMenu = new ExecuteMenu(run, debug);
		helpMenu = new HelpMenu();
	}

	private void addMenus(boolean includeExecute) {
		add(fileMenu);
		add(settingsMenu);
		if (includeExecute) {
			add(executeMenu);
		}
		add(helpMenu);
	}

}
