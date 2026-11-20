package io.simpleide.ui.treefile;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Directory entry in the project tree.
 *
 * <p>
 * Children are loaded lazily, either immediately at construction or on first expansion.
 * Binary files that cannot be decoded as text are represented as {@link TypeFile#BINARY}
 * entries with empty content. Refreshing re-reads the children from disk so external
 * changes appear in the tree.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class Directory extends TreeFile {

	private final List<TreeFile> children = new ArrayList<>();

	private boolean childrenLoaded;

	private Directory(Path path, String name, boolean loadImmediately) throws IOException {
		super(path, name);
		if (loadImmediately) {
			loadChildren();
		}
	}

	/**
	 * Creates a directory entry and loads its children immediately.
	 * @param path directory path
	 * @param name display name
	 * @throws IOException when the directory cannot be listed
	 */
	public Directory(Path path, String name) throws IOException {
		this(path, name, true);
	}

	/**
	 * Creates a directory entry named after the directory itself.
	 * @param path directory path
	 * @throws IOException when the directory cannot be listed
	 */
	public Directory(Path path) throws IOException {
		this(path, path.getFileName() == null ? path.toString() : path.getFileName().toString(), true);
	}

	/**
	 * Returns the mutable child list.
	 * @return the current children
	 */
	public List<TreeFile> getChildren() {
		return children;
	}

	/**
	 * Appends a child entry.
	 * @param child entry to add
	 */
	public void addChild(TreeFile child) {
		children.add(child);
		sortChildren(children);
	}

	boolean areChildrenLoaded() {
		return childrenLoaded;
	}

	void loadChildren() throws IOException {
		if (childrenLoaded) {
			return;
		}

		children.addAll(readChildren(getPath()));
		childrenLoaded = true;
	}

	void refreshChildren() throws IOException {
		replaceChildren(readChildrenFromDisk());
	}

	List<TreeFile> readChildrenFromDisk() throws IOException {
		return readChildren(getPath());
	}

	void replaceChildren(List<TreeFile> refreshedChildren) {
		children.clear();
		children.addAll(refreshedChildren);
		childrenLoaded = true;
	}

	private List<TreeFile> readChildren(Path path) throws IOException {
		List<TreeFile> loadedChildren = new ArrayList<>();
		try (Stream<Path> entries = Files.list(path)) {
			for (Path entry : entries.toList()) {
				if (Files.isDirectory(entry)) {
					loadedChildren.add(new Directory(entry, entry.getFileName().toString(), false));
				}
				else {
					try {
						loadedChildren.add(new File(entry));
					}
					catch (MalformedInputException exception) {
						loadedChildren
							.add(new File(entry, entry.getFileName().toString(), TypeFile.BINARY, new StringBuffer()));
					}
				}
			}
		}
		sortChildren(loadedChildren);
		return loadedChildren;
	}

	private static void sortChildren(List<TreeFile> entries) {
		entries.sort(Comparator.comparing((TreeFile child) -> !(child instanceof Directory))
			.thenComparing(TreeFile::getName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(TreeFile::getName));
	}

}
