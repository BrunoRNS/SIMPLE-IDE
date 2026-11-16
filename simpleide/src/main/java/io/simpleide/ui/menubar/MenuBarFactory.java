package io.simpleide.ui.menubar;

import io.simpleide.ui.menubar.menus.IdeMenuBar;
import javax.swing.JFrame;

/**
 * Installs the application menu bar on a frame.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class MenuBarFactory {

	/**
	 * Creates the menu bar and assigns it to a frame.
	 * @param frame frame that receives the menu bar
	 * @param createFile action that creates a new file
	 * @param createFolder action that creates a new folder
	 * @param setDirectory action that selects the working directory
	 * @param saveFile action that saves the current file
	 */
	public static void createMenuBar(JFrame frame, Runnable createFile, Runnable createFolder, Runnable setDirectory,
			Runnable saveFile) {
		frame.setJMenuBar(new IdeMenuBar(createFile, createFolder, setDirectory, saveFile));
	}

}
