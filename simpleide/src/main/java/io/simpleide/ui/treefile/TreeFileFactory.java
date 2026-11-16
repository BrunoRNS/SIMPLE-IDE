package io.simpleide.ui.treefile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Creates project trees rooted at the working directory or an explicit path.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class TreeFileFactory {

	/**
	 * Creates a tree for the current working directory.
	 * @return a tree rooted at the {@code user.dir} directory
	 * @throws java.io.UncheckedIOException when the directory cannot be listed
	 */
	public static Tree createDirectory() {
		try {
			return createDirectory(Path.of(System.getProperty("user.dir")));
		}
		catch (IOException e) {
			throw new UncheckedIOException("Unable to load the current project directory.", e);
		}
	}

	/**
	 * Loads the current working directory as a directory entry.
	 * @return the loaded root directory
	 * @throws IOException when the directory cannot be listed
	 */
	public static Directory loadRootDirectory() throws IOException {
		return new Directory(Path.of(System.getProperty("user.dir")));
	}

	/**
	 * Creates a tree rooted at a path.
	 * @param path root directory
	 * @return a tree for the directory
	 * @throws IOException when the directory cannot be listed
	 */
	public static Tree createDirectory(Path path) throws IOException {
		return new Tree(new Directory(path));
	}

}
