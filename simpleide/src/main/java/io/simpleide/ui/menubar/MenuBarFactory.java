package io.simpleide.ui.menubar;

import javax.swing.JFrame;

import io.simpleide.ui.menubar.menus.IdeMenuBar;

/**
 * Installs the application menu bar on a frame.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public class MenuBarFactory {

	/**
	 * Creates the legacy menu bar without an Execute menu and assigns it to a frame.
	 * @param frame frame that receives the menu bar
	 * @param createFile action that creates a new file
	 * @param createFolder action that creates a new folder
	 * @param openFolder action that opens a folder
	 * @param closeFolder action that closes the open folder
	 * @param saveFile action that saves the current file
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static void createMenuBar(JFrame frame, Runnable createFile, Runnable createFolder, Runnable openFolder,
			Runnable closeFolder, Runnable saveFile) {
		frame.setJMenuBar(new IdeMenuBar(createFile, createFolder, openFolder, closeFolder, saveFile));
	}

	/**
	 * Creates the menu bar and assigns it to a frame.
	 * @param frame frame that receives the menu bar
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
	public static void createMenuBar(JFrame frame, Runnable createFile, Runnable createFolder, Runnable openFolder,
			Runnable closeFolder, Runnable saveFile, Runnable run, Runnable debug) {
		frame.setJMenuBar(new IdeMenuBar(createFile, createFolder, openFolder, closeFolder, saveFile, run, debug));
	}

}
