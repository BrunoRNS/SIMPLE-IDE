package io.simpleide.ui.treefile;

/**
 * Selection and working-directory state of a project tree.
 *
 * <p>
 * Selecting a directory makes it the current directory, selecting a file keeps its parent
 * as the current directory, and clearing the selection falls back to the root.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class TreeState {

	private Directory rootDirectory;

	private TreeFile selected;

	private Directory currentDirectory;

	TreeState(Directory rootDirectory) {
		this.rootDirectory = rootDirectory;
		this.currentDirectory = rootDirectory;
	}

	/**
	 * Returns the selected entry.
	 * @return the selected entry, or {@code null} when nothing is selected
	 */
	public TreeFile getSelected() {
		return selected;
	}

	/**
	 * Returns the directory new entries are created in.
	 * @return the current directory
	 */
	public Directory getCurrentDirectory() {
		return currentDirectory;
	}

	void setRootDirectory(Directory rootDirectory) {
		this.rootDirectory = rootDirectory;
		select(rootDirectory, null);
	}

	void select(TreeFile selected, Directory parentDirectory) {
		this.selected = selected;
		if (selected instanceof Directory directory) {
			currentDirectory = directory;
		}
		else if (selected != null) {
			currentDirectory = parentDirectory;
		}
		else {
			currentDirectory = rootDirectory;
		}
	}

}
