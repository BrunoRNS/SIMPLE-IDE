package io.simpleide.ui.treefile;

public class TreeState {
  private final Directory rootDirectory;
  private TreeFile selected;
  private Directory currentDirectory;

  TreeState(Directory rootDirectory) {
    this.rootDirectory = rootDirectory;
    this.currentDirectory = rootDirectory;
  }

  public TreeFile getSelected() {
    return selected;
  }

  public Directory getCurrentDirectory() {
    return currentDirectory;
  }

  void select(TreeFile selected, Directory parentDirectory) {
    this.selected = selected;
    if (selected instanceof Directory directory) {
      currentDirectory = directory;
    } else if (selected != null) {
      currentDirectory = parentDirectory;
    } else {
      currentDirectory = rootDirectory;
    }
  }
}
