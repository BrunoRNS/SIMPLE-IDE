package io.simpleide.ui.treefile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
public class Directory extends TreeFile {

	private final List<TreeFile> children = new ArrayList<>();

	private final AtomicReference<DirectorySnapshot> state = new AtomicReference<>(new DirectorySnapshot(false, false));

	Directory(Path path, String name, boolean loadImmediately) throws IOException {
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
		return state.get().childrenLoaded();
	}

	/** Returns whether this directory's child listing is currently being loaded. */
	public boolean isLoading() {
		return state.get().loading();
	}

	void setLoading(boolean loading) {
		state.updateAndGet(snapshot -> new DirectorySnapshot(snapshot.childrenLoaded(), loading));
	}

	final void loadChildren() throws IOException {
		if (state.get().childrenLoaded()) {
			return;
		}

		children.addAll(readChildrenFromDisk());
		state.updateAndGet(snapshot -> new DirectorySnapshot(true, snapshot.loading()));
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
		state.updateAndGet(snapshot -> new DirectorySnapshot(true, snapshot.loading()));
	}

	private record DirectorySnapshot(boolean childrenLoaded, boolean loading) {
	}

	private List<TreeFile> readChildren(Path path) throws IOException {
		List<TreeFile> loadedChildren = new ArrayList<>();
		try (Stream<Path> entries = Files.list(path)) {
			for (Path entry : entries.toList()) {
				if (Files.isDirectory(entry)) {
					loadedChildren.add(new Directory(entry, entry.getFileName().toString(), false));
				}
				else {
					loadedChildren.add(SimpleIDEFile.lazy(entry));
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
