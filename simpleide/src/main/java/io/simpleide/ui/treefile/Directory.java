package io.simpleide.ui.treefile;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class Directory extends TreeFile {
  private final List<TreeFile> children = new ArrayList<>();
  private boolean childrenLoaded;

  private Directory(Path path, String name, boolean loadImmediately) throws IOException {
    super(path, name);
    if (loadImmediately) {
      loadChildren();
    }
  }

  public Directory(Path path, String name) throws IOException {
    this(path, name, true);
  }

  public Directory(Path path) throws IOException {
    this(path, path.getFileName() == null ? path.toString() : path.getFileName().toString(), true);
  }

  public List<TreeFile> getChildren() {
    return children;
  }

  public void addChild(TreeFile child) {
    children.add(child);
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
        } else {
          try {
            loadedChildren.add(new File(entry));
          } catch (MalformedInputException exception) {
            loadedChildren.add(
                new File(
                    entry, entry.getFileName().toString(), TypeFile.BINARY, new StringBuffer()));
          }
        }
      }
    }
    return loadedChildren;
  }
}
