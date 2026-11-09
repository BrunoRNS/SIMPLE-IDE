package io.simpleide.ui.treefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.simpleide.thread.ThreadMaster;

class TreeFileTest {

  @Test
  void directoryLoadsOnlyImmediateChildrenOnConstruction() throws IOException {
    Path root = Files.createTempDirectory("simpleide-treefile-");
    try {
      Path source = Files.createDirectory(root.resolve("src"));
      Path nested = Files.createDirectory(source.resolve("nested"));
      Files.writeString(source.resolve("Main.java"), "class Main {}");
      Files.writeString(nested.resolve("Helper.java"), "class Helper {}");

      Directory directory = new Directory(source);

      assertThat(directory.getChildren()).hasSize(2);
      File file = (File) directory.getChildren().stream()
          .filter(child -> child.getName().equals("Main.java"))
          .findFirst()
          .orElseThrow();
      Directory nestedDirectory = (Directory) directory.getChildren().stream()
          .filter(child -> child.getName().equals("nested"))
          .findFirst()
          .orElseThrow();

      assertThat(file.getTypeFile()).isEqualTo(TypeFile.JAVA);
      assertThat(file.getCode()).hasToString("class Main {}");
      assertThat(nestedDirectory.areChildrenLoaded()).isFalse();
      assertThat(nestedDirectory.getChildren()).isEmpty();

      nestedDirectory.loadChildren();

      assertThat(nestedDirectory.getChildren())
          .extracting(TreeFile::getName)
          .containsExactly("Helper.java");
    } finally {
      Files.deleteIfExists(root.resolve("src/nested/Helper.java"));
      Files.deleteIfExists(root.resolve("src/nested"));
      Files.deleteIfExists(root.resolve("src/Main.java"));
      Files.deleteIfExists(root.resolve("src"));
      Files.deleteIfExists(root);
    }
  }

  @Test
  void refreshChildrenReflectsFilesystemChanges() throws IOException {
    Path root = Files.createTempDirectory("simpleide-treefile-refresh-");
    try {
      Path originalFile = Files.createFile(root.resolve("original.txt"));
      Directory directory = new Directory(root);
      Files.delete(originalFile);
      Files.createFile(root.resolve("created.txt"));

      directory.refreshChildren();

      assertThat(directory.getChildren()).extracting(TreeFile::getName).containsExactly("created.txt");
      assertThat(directory.areChildrenLoaded()).isTrue();
    } finally {
      Files.deleteIfExists(root.resolve("original.txt"));
      Files.deleteIfExists(root.resolve("created.txt"));
      Files.deleteIfExists(root);
    }
  }

  @Test
  void failedRefreshKeepsPreviouslyLoadedChildren() throws IOException {
    Path root = Files.createTempDirectory("simpleide-treefile-refresh-failure-");
    Path directoryPath = Files.createDirectory(root.resolve("directory"));
    try {
      Files.createFile(directoryPath.resolve("original.txt"));
      Directory directory = new Directory(directoryPath);
      Files.delete(directoryPath.resolve("original.txt"));
      Files.delete(directoryPath);

      assertThatThrownBy(directory::refreshChildren).isInstanceOf(IOException.class);

      assertThat(directory.getChildren()).extracting(TreeFile::getName).containsExactly("original.txt");
      assertThat(directory.areChildrenLoaded()).isTrue();
    } finally {
      Files.deleteIfExists(directoryPath.resolve("original.txt"));
      Files.deleteIfExists(directoryPath);
      Files.deleteIfExists(root);
    }
  }

  @Test
  void treeLoadsDirectoryContentsOnlyWhenExpanded() throws Exception {
    Path root = Files.createTempDirectory("simpleide-tree-lazy-");
    try {
      Path nested = Files.createDirectory(root.resolve("nested"));
      Path deeper = Files.createDirectory(nested.resolve("deeper"));
      Files.writeString(nested.resolve("Nested.java"), "class Nested {}");
      Files.writeString(deeper.resolve("Deep.java"), "class Deep {}");
      Directory rootDirectory = new Directory(root);
      CountDownLatch childrenLoaded = new CountDownLatch(1);
      AtomicBoolean modelUpdatedOnEdt = new AtomicBoolean();
      AtomicReference<Tree> treeReference = new AtomicReference<>();
      ThreadMaster.getInstance().initialize();

      SwingUtilities.invokeAndWait(() -> {
        Tree tree = new Tree(rootDirectory);
        treeReference.set(tree);
        tree.getModel().addTreeModelListener(new TreeModelListener() {
          @Override
          public void treeNodesChanged(TreeModelEvent event) {
          }

          @Override
          public void treeNodesInserted(TreeModelEvent event) {
          }

          @Override
          public void treeNodesRemoved(TreeModelEvent event) {
          }

          @Override
          public void treeStructureChanged(TreeModelEvent event) {
            modelUpdatedOnEdt.set(SwingUtilities.isEventDispatchThread());
            childrenLoaded.countDown();
          }
        });
        DefaultMutableTreeNode rootNode =
            (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode nestedNode = childNamed(rootNode, "nested");
        Directory nestedDirectory = (Directory) nestedNode.getUserObject();

        assertThat(nestedDirectory.areChildrenLoaded()).isFalse();
        assertThat(nestedNode.getChildCount()).isEqualTo(1);

        tree.expandPath(new TreePath(nestedNode.getPath()));
      });

      assertThat(childrenLoaded.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(modelUpdatedOnEdt).isTrue();
      SwingUtilities.invokeAndWait(() -> {
        Tree tree = treeReference.get();
        DefaultMutableTreeNode rootNode =
            (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode nestedNode = childNamed(rootNode, "nested");
        Directory nestedDirectory = (Directory) nestedNode.getUserObject();
        assertThat(nestedDirectory.areChildrenLoaded()).isTrue();
        assertThat(nestedNode.getChildCount()).isEqualTo(2);
        Directory deeperDirectory = (Directory) nestedDirectory.getChildren().stream()
            .filter(child -> child.getName().equals("deeper"))
            .findFirst()
            .orElseThrow();
        assertThat(deeperDirectory.areChildrenLoaded()).isFalse();
        assertThat(deeperDirectory.getChildren()).isEmpty();
      });
    } finally {
      Files.deleteIfExists(root.resolve("nested/deeper/Deep.java"));
      Files.deleteIfExists(root.resolve("nested/deeper"));
      Files.deleteIfExists(root.resolve("nested/Nested.java"));
      Files.deleteIfExists(root.resolve("nested"));
      Files.deleteIfExists(root);
    }
  }

  @Test
  void treeStateTracksSelectedAndCurrentDirectory() throws Exception {
    Path root = Files.createTempDirectory("simpleide-tree-state-");
    Logger treeLogger = (Logger) LoggerFactory.getLogger(Tree.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(treeLogger.getLoggerContext());
    appender.start();
    treeLogger.addAppender(appender);
    try {
      Path nested = Files.createDirectory(root.resolve("nested"));
      Files.writeString(root.resolve("Main.java"), "class Main {}");
      Files.writeString(nested.resolve("Helper.java"), "class Helper {}");
      Directory rootDirectory = new Directory(root);

      SwingUtilities.invokeAndWait(() -> {
        Tree tree = new Tree(rootDirectory);
        DefaultMutableTreeNode rootNode =
            (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode nestedNode = childNamed(rootNode, "nested");
        DefaultMutableTreeNode fileNode = childNamed(rootNode, "Main.java");

        tree.setSelectionPath(new TreePath(nestedNode.getPath()));
        assertThat(tree.getState().getSelected()).isInstanceOf(Directory.class);
        assertThat(tree.getState().getCurrentDirectory().getName()).isEqualTo("nested");
        assertThat(appender.list)
            .anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("selected=" + nested, "currentDirectory=" + nested));

        tree.setSelectionPath(new TreePath(fileNode.getPath()));
        assertThat(tree.getState().getSelected()).isInstanceOf(File.class);
        assertThat(tree.getState().getCurrentDirectory()).isSameAs(rootDirectory);

        tree.clearSelection();
        assertThat(tree.getState().getSelected()).isNull();
        assertThat(tree.getState().getCurrentDirectory()).isSameAs(rootDirectory);
      });
    } finally {
      Files.deleteIfExists(root.resolve("nested/Helper.java"));
      Files.deleteIfExists(root.resolve("nested"));
      Files.deleteIfExists(root.resolve("Main.java"));
      Files.deleteIfExists(root);
      treeLogger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void factoryCreatesTreeForProvidedDirectory() throws IOException {
    Path root = Files.createTempDirectory("simpleide-tree-factory-");
    try {
      Files.writeString(root.resolve("Main.java"), "class Main {}");

      Tree tree = TreeFileFactory.createDirectory(root);

      DefaultMutableTreeNode rootNode =
          (DefaultMutableTreeNode) tree.getModel().getRoot();
      assertThat(((TreeFile) rootNode.getUserObject()).getPath()).isEqualTo(root);
      assertThat(rootNode.getChildCount()).isEqualTo(1);
      assertThat(((TreeFile) ((DefaultMutableTreeNode) rootNode.getChildAt(0))
          .getUserObject()).getName()).isEqualTo("Main.java");
    } finally {
      Files.deleteIfExists(root.resolve("Main.java"));
      Files.deleteIfExists(root);
    }
  }

  @Test
  void settingNewRootUpdatesTreeModelAndState() throws Exception {
    Path currentRoot = Files.createTempDirectory("simpleide-tree-current-root-");
    Path newRootPath = Files.createDirectory(currentRoot.resolve("new-root"));
    try {
      Files.writeString(newRootPath.resolve("Main.java"), "class Main {}");
      Directory newRoot = new Directory(newRootPath);
      Directory currentDirectory = new Directory(currentRoot);
      CountDownLatch rootUpdated = new CountDownLatch(1);
      AtomicBoolean modelUpdatedOnEdt = new AtomicBoolean();
      AtomicReference<Tree> treeReference = new AtomicReference<>();

      SwingUtilities.invokeAndWait(() -> {
        Tree tree = new Tree(currentDirectory);
        treeReference.set(tree);
        tree.addPropertyChangeListener("model", event -> {
          modelUpdatedOnEdt.set(SwingUtilities.isEventDispatchThread());
          rootUpdated.countDown();
        });
      });

      treeReference.get().setRoot(newRoot);

      assertThat(rootUpdated.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(modelUpdatedOnEdt).isTrue();
      SwingUtilities.invokeAndWait(() -> {
        Tree tree = treeReference.get();
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
        assertThat(((TreeFile) rootNode.getUserObject()).getPath()).isEqualTo(newRootPath);
        assertThat(tree.getState().getSelected()).isSameAs(newRoot);
        assertThat(tree.getState().getCurrentDirectory()).isSameAs(newRoot);
        assertThat(rootNode.getChildCount()).isEqualTo(1);
      });
    } finally {
      Files.deleteIfExists(newRootPath.resolve("Main.java"));
      Files.deleteIfExists(newRootPath);
      Files.deleteIfExists(currentRoot);
    }
  }

  @Test
  void settingNullRootIsRejected() throws IOException {
    Path root = Files.createTempDirectory("simpleide-tree-null-root-");
    try {
      Tree tree = new Tree(new Directory(root));

      assertThatThrownBy(() -> tree.setRoot(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Tree root must not be null.");
    } finally {
      Files.deleteIfExists(root);
    }
  }

  @Test
  void deletingSelectedFileRefreshesParentAndSelectsIt() throws Exception {
    Path root = Files.createTempDirectory("simpleide-tree-delete-file-");
    Path filePath = Files.writeString(root.resolve("remove.txt"), "remove me");
    try {
      Directory rootDirectory = new Directory(root);
      Tree tree = new Tree(rootDirectory);
      DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
      DefaultMutableTreeNode fileNode = childNamed(rootNode, "remove.txt");
      TreeFileActions actions = new TreeFileActions(tree, tree.getState());

      SwingUtilities.invokeAndWait(() -> {
        try {
          actions.deleteSelected(new TreePath(fileNode.getPath()), (TreeFile) fileNode.getUserObject());
        } catch (IOException exception) {
          throw new AssertionError(exception);
        }
      });

      assertThat(Files.exists(filePath)).isFalse();
      assertThat(rootDirectory.getChildren()).isEmpty();
      assertThat(tree.getState().getSelected()).isSameAs(rootDirectory);
    } finally {
      Files.deleteIfExists(filePath);
      Files.deleteIfExists(root);
    }
  }

  @Test
  void deletingSelectedRootMovesTreeToParentDirectory() throws Exception {
    Path parentPath = Files.createTempDirectory("simpleide-tree-delete-root-");
    Path rootPath = Files.createDirectory(parentPath.resolve("project"));
    Path childPath = Files.writeString(rootPath.resolve("Main.java"), "class Main {}");
    try {
      Directory rootDirectory = new Directory(rootPath);
      Tree tree = new Tree(rootDirectory);
      TreePath rootTreePath = new TreePath(((DefaultMutableTreeNode) tree.getModel().getRoot()).getPath());
      TreeFileActions actions = new TreeFileActions(tree, tree.getState());

      SwingUtilities.invokeAndWait(() -> {
        try {
          actions.deleteSelected(rootTreePath, rootDirectory);
        } catch (IOException exception) {
          throw new AssertionError(exception);
        }
      });

      DefaultMutableTreeNode updatedRoot = (DefaultMutableTreeNode) tree.getModel().getRoot();
      assertThat(Files.exists(rootPath)).isFalse();
      assertThat(Files.exists(childPath)).isFalse();
      assertThat(((TreeFile) updatedRoot.getUserObject()).getPath()).isEqualTo(parentPath);
      assertThat(tree.getState().getSelected()).isSameAs(tree.getState().getCurrentDirectory());
      assertThat(tree.getState().getCurrentDirectory().getPath()).isEqualTo(parentPath);
      assertThat(tree.getState().getCurrentDirectory().getChildren())
          .extracting(TreeFile::getPath)
          .doesNotContain(rootPath);
    } finally {
      Files.deleteIfExists(childPath);
      Files.deleteIfExists(rootPath);
      Files.deleteIfExists(parentPath);
    }
  }

  @Test
  void directoryKeepsBinaryFilesWithoutTryingToDecodeThemAsText() throws IOException {
    Path root = Files.createTempDirectory("simpleide-tree-binary-");
    try {
      Path binaryPath = root.resolve("image.dat");
      Files.write(binaryPath, new byte[] {(byte) 0xC3, (byte) 0x28});

      Directory directory = new Directory(root);

      assertThat(directory.getChildren()).hasSize(1);
      File binary = (File) directory.getChildren().get(0);
      assertThat(binary.getTypeFile()).isEqualTo(TypeFile.BINARY);
      assertThat(binary.getCode()).hasToString("");
    } finally {
      Files.deleteIfExists(root.resolve("image.dat"));
      Files.deleteIfExists(root);
    }
  }

  @Test
  void fileTypesRecognizeSupportedExtensionsAndTreatAssemblyAsText() throws IOException {
    Path root = Files.createTempDirectory("simpleide-treefile-types-");
    try {
      Path pythonPath = Files.writeString(root.resolve("script.PY"), "print('hello')");
      Path javascriptPath = Files.writeString(root.resolve("app.JS"), "console.log('hello')");
      Path kotlinPath = Files.writeString(root.resolve("Main.kt"), "fun main() {}");
      Path cPath = Files.writeString(root.resolve("main.c"), "int main() {}");
      Path cppPath = Files.writeString(root.resolve("main.cpp"), "int main() {}");
      Path unsupportedAssemblyPath = Files.writeString(root.resolve("main.asm"), "lda #$00");

      assertThat(new File(pythonPath).getTypeFile()).isEqualTo(TypeFile.PYTHON);
      assertThat(new File(javascriptPath).getTypeFile()).isEqualTo(TypeFile.JAVASCRIPT);
      assertThat(new File(kotlinPath).getTypeFile()).isEqualTo(TypeFile.KOTLIN);
      assertThat(new File(cPath).getTypeFile()).isEqualTo(TypeFile.C);
      assertThat(new File(cppPath).getTypeFile()).isEqualTo(TypeFile.CPP);
      assertThat(new File(unsupportedAssemblyPath).getTypeFile()).isEqualTo(TypeFile.TXT);
    } finally {
      Files.deleteIfExists(root.resolve("script.PY"));
      Files.deleteIfExists(root.resolve("app.JS"));
      Files.deleteIfExists(root.resolve("Main.kt"));
      Files.deleteIfExists(root.resolve("main.c"));
      Files.deleteIfExists(root.resolve("main.cpp"));
      Files.deleteIfExists(root.resolve("main.asm"));
      Files.deleteIfExists(root);
    }
  }

  @Test
  void rendererUsesLanguageAndExpandedFolderIcons() throws IOException {
    Path root = Files.createTempDirectory("simpleide-treefile-icons-");
    try {
      Directory directory = new Directory(root, "folder");
      TreeFileCellRenderer renderer = new TreeFileCellRenderer();
      JTree tree = new JTree();
      DefaultMutableTreeNode directoryNode = new DefaultMutableTreeNode(directory);
      DefaultMutableTreeNode javaNode = new DefaultMutableTreeNode(
          new File(root.resolve("Main.java"), "Main.java", TypeFile.JAVA, new StringBuffer()));
      DefaultMutableTreeNode pythonNode = new DefaultMutableTreeNode(
          new File(root.resolve("main.py"), "main.py", TypeFile.PYTHON, new StringBuffer()));

      renderer.getTreeCellRendererComponent(tree, directoryNode, false, false, false, 0, false);
      Icon closedFolderIcon = renderer.getIcon();
      renderer.getTreeCellRendererComponent(tree, directoryNode, false, true, false, 0, false);
      Icon openFolderIcon = renderer.getIcon();
      renderer.getTreeCellRendererComponent(tree, javaNode, false, false, true, 1, false);
      Icon javaIcon = renderer.getIcon();
      renderer.getTreeCellRendererComponent(tree, pythonNode, false, false, true, 2, false);
      Icon pythonIcon = renderer.getIcon();

      assertThat(closedFolderIcon).isNotNull().isNotSameAs(openFolderIcon);
      assertThat(openFolderIcon).isNotNull();
      assertThat(javaIcon).isNotNull();
      assertThat(pythonIcon).isNotNull().isNotSameAs(javaIcon);
    } finally {
      Files.deleteIfExists(root);
    }
  }

  private DefaultMutableTreeNode childNamed(DefaultMutableTreeNode parent, String name) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
      if (name.equals(((TreeFile) child.getUserObject()).getName())) {
        return child;
      }
    }
    throw new AssertionError("Tree node not found: " + name);
  }
}
