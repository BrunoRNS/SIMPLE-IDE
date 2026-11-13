package io.simpleide.ui.treefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
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
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.thread.ThreadMaster;

class WatcherTest {

  @TempDir
  Path root;

  @Test
  void refreshesLoadedDirectoriesWhenFilesAreCreatedAndDeletedExternally() throws Exception {
    Path nestedPath = Files.createDirectory(root.resolve("src"));
    Directory rootDirectory = new Directory(root);
    CountDownLatch nestedLoaded = new CountDownLatch(1);
    CountDownLatch externalFileLoaded = new CountDownLatch(1);
    CountDownLatch externalFileRemoved = new CountDownLatch(1);
    AtomicBoolean fileWasLoaded = new AtomicBoolean();
    AtomicReference<CompletableFuture<Void>> watcherStarted = new AtomicReference<>();
    AtomicReference<Watcher> watcherReference = new AtomicReference<>();
    AtomicReference<Directory> nestedDirectoryReference = new AtomicReference<>();

    ThreadMaster.getInstance().initialize();
    SwingUtilities.invokeAndWait(() -> {
      Tree tree = new Tree(rootDirectory);
      DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
      DefaultMutableTreeNode nestedNode = childNamed(rootNode, "src");
      Directory nestedDirectory = (Directory) nestedNode.getUserObject();
      nestedDirectoryReference.set(nestedDirectory);
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
          if (event.getTreePath().getLastPathComponent() == nestedNode) {
            boolean fileExists = nestedDirectory.getChildren().stream()
                .anyMatch(child -> child.getName().equals("external.txt"));
            if (fileExists) {
              fileWasLoaded.set(true);
              externalFileLoaded.countDown();
            } else {
              nestedLoaded.countDown();
              if (fileWasLoaded.get()) {
                externalFileRemoved.countDown();
              }
            }
          }
        }
      });

      Watcher watcher;
      try {
        watcher = new Watcher(tree);
      } catch (IOException error) {
        throw new AssertionError(error);
      }
      watcherReference.set(watcher);
      watcherStarted.set(watcher.start(root));
      tree.expandPath(new TreePath(nestedNode.getPath()));
    });

    try {
      watcherStarted.get().get(20, TimeUnit.SECONDS);
      assertThat(nestedLoaded.await(20, TimeUnit.SECONDS)).isTrue();
      Files.writeString(nestedPath.resolve("external.txt"), "created outside the IDE");
      assertThat(externalFileLoaded.await(20, TimeUnit.SECONDS)).isTrue();
      assertThat(nestedDirectoryReference.get().getChildren())
          .extracting(TreeFile::getName)
          .contains("external.txt");
      Files.delete(nestedPath.resolve("external.txt"));
      assertThat(externalFileRemoved.await(20, TimeUnit.SECONDS)).isTrue();
      assertThat(nestedDirectoryReference.get().getChildren())
          .extracting(TreeFile::getName)
          .doesNotContain("external.txt");
    } finally {
      Watcher watcher = watcherReference.get();
      if (watcher != null) {
        watcher.close();
      }
      Files.deleteIfExists(nestedPath.resolve("external.txt"));
      Files.deleteIfExists(nestedPath);
    }
  }

  private DefaultMutableTreeNode childNamed(DefaultMutableTreeNode parent, String name) {
    for (int index = 0; index < parent.getChildCount(); index++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(index);
      if (child.getUserObject() instanceof TreeFile file && file.getName().equals(name)) {
        return child;
      }
    }
    throw new AssertionError("Tree node not found: " + name);
  }
}
