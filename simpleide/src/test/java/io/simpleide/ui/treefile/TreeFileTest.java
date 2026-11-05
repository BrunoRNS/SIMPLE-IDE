package io.simpleide.ui.treefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
