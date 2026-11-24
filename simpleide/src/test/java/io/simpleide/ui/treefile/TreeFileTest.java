package io.simpleide.ui.treefile;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import io.simpleide.api.LargeTextViewer;
import io.simpleide.thread.ThreadMaster;

/**
 * Tests {@link io.simpleide.ui.treefile.TreeFile}.
 */
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
			SimpleIDEFile file = (SimpleIDEFile) directory.getChildren()
				.stream()
				.filter(child -> child.getName().equals("Main.java"))
				.findFirst()
				.orElseThrow();
			Directory nestedDirectory = (Directory) directory.getChildren()
				.stream()
				.filter(child -> child.getName().equals("nested"))
				.findFirst()
				.orElseThrow();

			assertThat(file.getTypeFile()).isEqualTo(TypeFile.JAVA);
			assertThat(file.isContentLoaded()).isFalse();
			assertThat(file.getCode()).hasToString("");
			assertThat(nestedDirectory.areChildrenLoaded()).isFalse();
			assertThat(nestedDirectory.getChildren()).isEmpty();

			file.loadContent();
			assertThat(file.isContentLoaded()).isTrue();
			assertThat(file.getCode()).hasToString("class Main {}");
			nestedDirectory.loadChildren();

			assertThat(nestedDirectory.getChildren()).extracting(TreeFile::getName).containsExactly("Helper.java");
		}
		finally {
			Files.deleteIfExists(root.resolve("src/nested/Helper.java"));
			Files.deleteIfExists(root.resolve("src/nested"));
			Files.deleteIfExists(root.resolve("src/Main.java"));
			Files.deleteIfExists(root.resolve("src"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void largeTextIsClassifiedWithoutKeepingTheWholeFileInMemory() throws Exception {
		Path path = Files.createTempFile("simpleide-large-text-", ".txt");
		try {
			String block = "This is a large text file.\n".repeat(8192);
			try (var writer = Files.newBufferedWriter(path)) {
				for (int index = 0; index < 12; index++) {
					writer.write(block);
				}
			}
			SimpleIDEFile file = SimpleIDEFile.lazy(path);
			file.loadContent();

			assertThat(file.getContentKind()).isEqualTo(FileContentKind.TEXT);
			assertThat(file.isLargeContent()).isTrue();
			assertThat(file.getCode()).hasToString("");
			assertThat(file.getFileSize()).isGreaterThan(2L * 1024 * 1024);
			try (LargeTextViewer viewer = new LargeTextViewer(path, file.getFileSize())) {
				assertThat(viewer.pageCapacityBytes()).isEqualTo(256 * 1024);
			}
		}
		finally {
			Files.deleteIfExists(path);
		}
	}

	@Test
	void largeImagesAreDecodedAsBoundedPreviews() throws Exception {
		Path path = Files.createTempFile("simpleide-large-image-", ".png");
		try {
			BufferedImage source = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
			ImageIO.write(source, "png", path.toFile());
			SimpleIDEFile file = SimpleIDEFile.lazy(path);
			file.loadContent();

			assertThat(file.getContentKind()).isEqualTo(FileContentKind.IMAGE);
			assertThat(file.getImageWidth()).isEqualTo(1200);
			assertThat(file.getImageHeight()).isEqualTo(900);
			assertThat((long) file.getImageContent().getWidth() * file.getImageContent().getHeight())
				.isLessThanOrEqualTo(1_000_000);
		}
		finally {
			Files.deleteIfExists(path);
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
		}
		finally {
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
		}
		finally {
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
			AtomicReference<Directory> nestedDirectoryReference = new AtomicReference<>();
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
						Directory nestedDirectory = nestedDirectoryReference.get();
						if (nestedDirectory != null && nestedDirectory.areChildrenLoaded()) {
							modelUpdatedOnEdt.set(SwingUtilities.isEventDispatchThread());
							childrenLoaded.countDown();
						}
					}
				});
				DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
				DefaultMutableTreeNode nestedNode = childNamed(rootNode, "nested");
				Directory nestedDirectory = (Directory) nestedNode.getUserObject();
				nestedDirectoryReference.set(nestedDirectory);

				assertThat(nestedDirectory.areChildrenLoaded()).isFalse();
				assertThat(nestedNode.getChildCount()).isEqualTo(1);

				tree.expandPath(new TreePath(nestedNode.getPath()));
			});

			assertThat(childrenLoaded.await(20, TimeUnit.SECONDS)).isTrue();
			assertThat(modelUpdatedOnEdt).isTrue();
			SwingUtilities.invokeAndWait(() -> {
				Tree tree = treeReference.get();
				DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
				DefaultMutableTreeNode nestedNode = childNamed(rootNode, "nested");
				Directory nestedDirectory = (Directory) nestedNode.getUserObject();
				assertThat(nestedDirectory.areChildrenLoaded()).isTrue();
				assertThat(nestedNode.getChildCount()).isEqualTo(2);
				Directory deeperDirectory = (Directory) nestedDirectory.getChildren()
					.stream()
					.filter(child -> child.getName().equals("deeper"))
					.findFirst()
					.orElseThrow();
				assertThat(deeperDirectory.areChildrenLoaded()).isFalse();
				assertThat(deeperDirectory.getChildren()).isEmpty();
			});
		}
		finally {
			Files.deleteIfExists(root.resolve("nested/deeper/Deep.java"));
			Files.deleteIfExists(root.resolve("nested/deeper"));
			Files.deleteIfExists(root.resolve("nested/Nested.java"));
			Files.deleteIfExists(root.resolve("nested"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void loadingDirectoryShowsStatusAndKeepsTheEdtResponsive() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-loading-");
		Path nested = Files.createDirectory(root.resolve("nested"));
		CountDownLatch loadStarted = new CountDownLatch(1);
		CountDownLatch allowLoad = new CountDownLatch(1);
		CountDownLatch childrenLoaded = new CountDownLatch(1);
		AtomicReference<Tree> treeReference = new AtomicReference<>();
		try {
			Files.writeString(nested.resolve("Nested.java"), "class Nested {}");
			Directory rootDirectory = new Directory(root);
			BlockingDirectory blocked = new BlockingDirectory(nested, loadStarted, allowLoad);
			rootDirectory.replaceChildren(List.of(blocked));
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
						if (blocked.areChildrenLoaded()) {
							childrenLoaded.countDown();
						}
					}
				});
				DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
				DefaultMutableTreeNode nestedNode = childNamed(rootNode, "nested");
				tree.expandPath(new TreePath(nestedNode.getPath()));

				assertThat(blocked.isLoading()).isTrue();
				assertThat(((DefaultMutableTreeNode) nestedNode.getChildAt(0)).getUserObject()).isEqualTo("Loading...");
			});

			assertThat(loadStarted.await(10, TimeUnit.SECONDS)).isTrue();
			CountDownLatch edtResponsive = new CountDownLatch(1);
			SwingUtilities.invokeLater(edtResponsive::countDown);
			assertThat(edtResponsive.await(2, TimeUnit.SECONDS)).isTrue();

			allowLoad.countDown();
			assertThat(childrenLoaded.await(10, TimeUnit.SECONDS)).isTrue();
			SwingUtilities.invokeAndWait(() -> {
				assertThat(blocked.isLoading()).isFalse();
				assertThat(blocked.areChildrenLoaded()).isTrue();
				Tree tree = treeReference.get();
				DefaultMutableTreeNode nestedNode = childNamed((DefaultMutableTreeNode) tree.getModel().getRoot(),
						"nested");
				assertThat(nestedNode.getChildCount()).isEqualTo(1);
			});
		}
		finally {
			allowLoad.countDown();
			Files.deleteIfExists(nested.resolve("Nested.java"));
			Files.deleteIfExists(nested);
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
			Tree[] treeHolder = new Tree[1];

			SwingUtilities.invokeAndWait(() -> treeHolder[0] = new Tree(rootDirectory));
			flushEdt();
			Tree tree = treeHolder[0];
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode nestedNode = childNamed(rootNode, "nested");
			DefaultMutableTreeNode fileNode = childNamed(rootNode, "Main.java");

			SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(nestedNode.getPath())));
			flushEdt();
			assertThat(tree.getState().getSelected()).isInstanceOf(Directory.class);
			assertThat(tree.getState().getCurrentDirectory().getName()).isEqualTo("nested");
			assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
				.contains("selected=" + nested, "currentDirectory=" + nested));

			SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(fileNode.getPath())));
			flushEdt();
			assertThat(tree.getState().getSelected()).isInstanceOf(SimpleIDEFile.class);
			assertThat(tree.getState().getCurrentDirectory()).isSameAs(rootDirectory);

			SwingUtilities.invokeAndWait(() -> tree.clearSelection());
			flushEdt();
			assertThat(tree.getState().getSelected()).isNull();
			assertThat(tree.getState().getCurrentDirectory()).isSameAs(rootDirectory);
		}
		finally {
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

			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			assertThat(((TreeFile) rootNode.getUserObject()).getPath()).isEqualTo(root);
			assertThat(rootNode.getChildCount()).isEqualTo(1);
			assertThat(((TreeFile) ((DefaultMutableTreeNode) rootNode.getChildAt(0)).getUserObject()).getName())
				.isEqualTo("Main.java");
		}
		finally {
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

			assertThat(rootUpdated.await(20, TimeUnit.SECONDS)).isTrue();
			assertThat(modelUpdatedOnEdt).isTrue();
			SwingUtilities.invokeAndWait(() -> {
				Tree tree = treeReference.get();
				DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
				assertThat(((TreeFile) rootNode.getUserObject()).getPath()).isEqualTo(newRootPath);
				assertThat(tree.getState().getSelected()).isSameAs(newRoot);
				assertThat(tree.getState().getCurrentDirectory()).isSameAs(newRoot);
				assertThat(rootNode.getChildCount()).isEqualTo(1);
			});
		}
		finally {
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

			assertThatThrownBy(() -> tree.setRoot(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Tree root must not be null.");
		}
		finally {
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
					assertThat(tree.getState().getSelected()).isSameAs(rootDirectory);
				}
				catch (IOException exception) {
					throw new AssertionError(exception);
				}
			});

			assertThat(Files.exists(filePath)).isFalse();
			assertThat(rootDirectory.getChildren()).isEmpty();
			assertThat(tree.getState().getSelected()).isSameAs(rootDirectory);
		}
		finally {
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
				}
				catch (IOException exception) {
					throw new AssertionError(exception);
				}
			});

			DefaultMutableTreeNode updatedRoot = (DefaultMutableTreeNode) tree.getModel().getRoot();
			assertThat(Files.exists(rootPath)).isFalse();
			assertThat(Files.exists(childPath)).isFalse();
			assertThat(((TreeFile) updatedRoot.getUserObject()).getPath()).isEqualTo(parentPath);
			assertThat(tree.getState().getSelected()).isSameAs(tree.getState().getCurrentDirectory());
			assertThat(tree.getState().getCurrentDirectory().getPath()).isEqualTo(parentPath);
			assertThat(tree.getState().getCurrentDirectory().getChildren()).extracting(TreeFile::getPath)
				.doesNotContain(rootPath);
		}
		finally {
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
			Files.write(binaryPath, new byte[] { (byte) 0xC3, (byte) 0x28 });

			Directory directory = new Directory(root);

			assertThat(directory.getChildren()).hasSize(1);
			SimpleIDEFile binary = (SimpleIDEFile) directory.getChildren().get(0);
			assertThat(binary.isContentLoaded()).isFalse();
			binary.loadContent();
			assertThat(binary.isContentLoaded()).isTrue();
			assertThat(binary.getTypeFile()).isEqualTo(TypeFile.BINARY);
			assertThat(binary.getCode()).hasToString("");
		}
		finally {
			Files.deleteIfExists(root.resolve("image.dat"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void fileTypesRecognizeSupportedLanguageExtensions() throws IOException {
		Path root = Files.createTempDirectory("simpleide-treefile-types-");
		try {
			Path pythonPath = Files.writeString(root.resolve("script.PY"), "print('hello')");
			Path javascriptPath = Files.writeString(root.resolve("app.JS"), "console.log('hello')");
			Path kotlinPath = Files.writeString(root.resolve("Main.kt"), "fun main() {}");
			Path cPath = Files.writeString(root.resolve("main.c"), "int main() {}");
			Path cppPath = Files.writeString(root.resolve("main.cpp"), "int main() {}");
			Path rustPath = Files.writeString(root.resolve("main.RS"), "fn main() {}");
			Path javaPath = Files.writeString(root.resolve("Main.java"), "class Main {}");
			Path csharpPath = Files.writeString(root.resolve("Main.cs"), "class Main {}");
			Path groovyPath = Files.writeString(root.resolve("Main.groovy"), "class Main {}");
			Path goPath = Files.writeString(root.resolve("main.go"), "package main");

			assertThat(new SimpleIDEFile(pythonPath).getTypeFile()).isEqualTo(TypeFile.PYTHON);
			assertThat(new SimpleIDEFile(javascriptPath).getTypeFile()).isEqualTo(TypeFile.JAVASCRIPT);
			assertThat(new SimpleIDEFile(kotlinPath).getTypeFile()).isEqualTo(TypeFile.KOTLIN);
			assertThat(new SimpleIDEFile(cPath).getTypeFile()).isEqualTo(TypeFile.C);
			assertThat(new SimpleIDEFile(cppPath).getTypeFile()).isEqualTo(TypeFile.CPP);
			assertThat(new SimpleIDEFile(rustPath).getTypeFile()).isEqualTo(TypeFile.RUST);
			assertThat(new SimpleIDEFile(javaPath).getTypeFile()).isEqualTo(TypeFile.JAVA);
			assertThat(new SimpleIDEFile(csharpPath).getTypeFile()).isEqualTo(TypeFile.CSHARP);
			assertThat(new SimpleIDEFile(groovyPath).getTypeFile()).isEqualTo(TypeFile.GROOVY);
			assertThat(new SimpleIDEFile(goPath).getTypeFile()).isEqualTo(TypeFile.GO);
		}
		finally {
			Files.deleteIfExists(root.resolve("script.PY"));
			Files.deleteIfExists(root.resolve("app.JS"));
			Files.deleteIfExists(root.resolve("Main.kt"));
			Files.deleteIfExists(root.resolve("main.c"));
			Files.deleteIfExists(root.resolve("main.cpp"));
			Files.deleteIfExists(root.resolve("main.go"));
			Files.deleteIfExists(root.resolve("main.rs"));
			Files.deleteIfExists(root.resolve("Main.java"));
			Files.deleteIfExists(root.resolve("Main.cs"));
			Files.deleteIfExists(root.resolve("Main.groovy"));
			Files.deleteIfExists(root.resolve("main.RS"));
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
					new SimpleIDEFile(root.resolve("Main.java"), "Main.java", TypeFile.JAVA, new StringBuffer()));
			DefaultMutableTreeNode pythonNode = new DefaultMutableTreeNode(
					new SimpleIDEFile(root.resolve("main.py"), "main.py", TypeFile.PYTHON, new StringBuffer()));

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
		}
		finally {
			Files.deleteIfExists(root);
		}
	}

	private static void flushEdt() throws Exception {
		// Selection listeners are dispatched through ThreadMaster; flush the EDT queue.
		SwingUtilities.invokeAndWait(() -> {
		});
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

	private static final class BlockingDirectory extends Directory {

		private final CountDownLatch started;

		private final CountDownLatch release;

		private BlockingDirectory(Path path, CountDownLatch started, CountDownLatch release) throws IOException {
			super(path, path.getFileName().toString(), false);
			this.started = started;
			this.release = release;
		}

		@Override
		List<TreeFile> readChildrenFromDisk() throws IOException {
			started.countDown();
			try {
				if (!release.await(10, TimeUnit.SECONDS)) {
					throw new IOException("Timed out waiting to release directory loading.");
				}
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new IOException("Directory loading was interrupted.", error);
			}
			return super.readChildrenFromDisk();
		}

	}

	@Test
	void copyAndPasteFileInSameDirectory() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-copy-");
		try {
			Path filePath = Files.writeString(root.resolve("source.txt"), "content");
			Directory rootDirectory = new Directory(root);
			Tree tree = new Tree(rootDirectory);
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode fileNode = childNamed(rootNode, "source.txt");

			SwingUtilities.invokeAndWait(() -> {
				tree.setSelectionPath(new TreePath(fileNode.getPath()));
				tree.getFileActions().copySelected();
			});
			flushEdt();

			SwingUtilities.invokeAndWait(() -> tree.getFileActions().pasteSelected());
			flushEdt();

			assertThat(rootDirectory.getChildren()).extracting(TreeFile::getName).contains("source.txt", "source.txt");
			assertThat(Files.exists(root.resolve("source.txt"))).isTrue();
			assertThat(Files.exists(root.resolve("source.txt"))).isTrue();
		}
		finally {
			Files.deleteIfExists(root.resolve("source.txt"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void cutAndPasteFileToDifferentDirectory() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-cut-");
		try {
			Path srcDir = Files.createDirectory(root.resolve("src"));
			Path dstDir = Files.createDirectory(root.resolve("dst"));
			Path filePath = Files.writeString(srcDir.resolve("move.txt"), "content");
			Directory rootDirectory = new Directory(root);
			Tree tree = new Tree(rootDirectory);
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode srcNode = childNamed(rootNode, "src");
			DefaultMutableTreeNode fileNode = childNamed((DefaultMutableTreeNode) srcNode.getChildAt(0), "move.txt");
			DefaultMutableTreeNode dstNode = childNamed(rootNode, "dst");

			SwingUtilities.invokeAndWait(() -> {
				tree.setSelectionPath(new TreePath(fileNode.getPath()));
				tree.getFileActions().cutSelected();
			});
			flushEdt();

			SwingUtilities.invokeAndWait(() -> {
				tree.setSelectionPath(new TreePath(dstNode.getPath()));
				tree.getFileActions().pasteSelected();
			});
			flushEdt();

			assertThat(Files.exists(srcDir.resolve("move.txt"))).isFalse();
			assertThat(Files.exists(dstDir.resolve("move.txt"))).isTrue();
			TreeFileClipboard.getInstance().clear();
		}
		finally {
			Files.deleteIfExists(root.resolve("src/move.txt"));
			Files.deleteIfExists(root.resolve("dst/move.txt"));
			Files.deleteIfExists(root.resolve("src"));
			Files.deleteIfExists(root.resolve("dst"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void copyAndPasteDirectory() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-copy-dir-");
		try {
			Path srcDir = Files.createDirectory(root.resolve("src"));
			Files.writeString(srcDir.resolve("file.txt"), "content");
			Directory rootDirectory = new Directory(root);
			Tree tree = new Tree(rootDirectory);
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode srcNode = childNamed(rootNode, "src");

			SwingUtilities.invokeAndWait(() -> {
				tree.setSelectionPath(new TreePath(srcNode.getPath()));
				tree.getFileActions().copySelected();
			});
			flushEdt();

			SwingUtilities.invokeAndWait(() -> tree.getFileActions().pasteSelected());
			flushEdt();

			assertThat(rootDirectory.getChildren()).extracting(TreeFile::getName).contains("src", "src");
			assertThat(Files.exists(root.resolve("src/file.txt"))).isTrue();
			assertThat(Files.exists(root.resolve("src/file.txt"))).isTrue();
		}
		finally {
			Files.deleteIfExists(root.resolve("src/file.txt"));
			Files.deleteIfExists(root.resolve("src"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void pasteIntoNonExistentTargetShowsWarning() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-paste-error-");
		try {
			Path filePath = Files.writeString(root.resolve("source.txt"), "content");
			Directory rootDirectory = new Directory(root);
			Tree tree = new Tree(rootDirectory);
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode fileNode = childNamed(rootNode, "source.txt");

			SwingUtilities.invokeAndWait(() -> {
				tree.setSelectionPath(new TreePath(fileNode.getPath()));
				tree.getFileActions().copySelected();
			});
			flushEdt();

			Files.writeString(root.resolve("source.txt"), "existing");

			SwingUtilities.invokeAndWait(() -> tree.getFileActions().pasteSelected());
			flushEdt();

			assertThat(rootDirectory.getChildren()).extracting(TreeFile::getName).containsExactly("source.txt");
		}
		finally {
			Files.deleteIfExists(root.resolve("source.txt"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void keyboardShortcutsTriggerActions() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-keyboard-");
		try {
			Path filePath = Files.writeString(root.resolve("source.txt"), "content");
			Directory rootDirectory = new Directory(root);
			Tree tree = new Tree(rootDirectory);
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode fileNode = childNamed(rootNode, "source.txt");

			SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(fileNode.getPath())));
			flushEdt();

			Action copyAction = tree.getActionMap().get("copy");
			assertThat(copyAction).isNotNull();
			SwingUtilities.invokeAndWait(() -> copyAction.actionPerformed(null));
			flushEdt();

			Action pasteAction = tree.getActionMap().get("paste");
			assertThat(pasteAction).isNotNull();
			SwingUtilities.invokeAndWait(() -> pasteAction.actionPerformed(null));
			flushEdt();

			assertThat(rootDirectory.getChildren()).extracting(TreeFile::getName).contains("source.txt", "source.txt");
		}
		finally {
			Files.deleteIfExists(root.resolve("source.txt"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void dragAndDropMovesFile() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-dnd-");
		try {
			Path srcDir = Files.createDirectory(root.resolve("src"));
			Path dstDir = Files.createDirectory(root.resolve("dst"));
			Path filePath = Files.writeString(srcDir.resolve("move.txt"), "content");
			Directory rootDirectory = new Directory(root);
			Tree tree = new Tree(rootDirectory);
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode srcNode = childNamed(rootNode, "src");
			DefaultMutableTreeNode fileNode = childNamed((DefaultMutableTreeNode) srcNode.getChildAt(0), "move.txt");
			DefaultMutableTreeNode dstNode = childNamed(rootNode, "dst");

			SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(fileNode.getPath())));
			flushEdt();

			Transferable transferable = tree.getTransferHandler().createTransferable(tree);
			assertThat(transferable).isNotNull();
			assertThat(transferable.isDataFlavorSupported(TreeFileTransferable.TREE_FILE_FLAVOR)).isTrue();

			DropTargetDropEvent dropEvent = createDropEvent(tree, dstNode, transferable, DnDConstants.ACTION_MOVE);
			tree.getDropTarget().drop(dropEvent);
			flushEdt();

			assertThat(Files.exists(srcDir.resolve("move.txt"))).isFalse();
			assertThat(Files.exists(dstDir.resolve("move.txt"))).isTrue();
		}
		finally {
			Files.deleteIfExists(root.resolve("src/move.txt"));
			Files.deleteIfExists(root.resolve("dst/move.txt"));
			Files.deleteIfExists(root.resolve("src"));
			Files.deleteIfExists(root.resolve("dst"));
			Files.deleteIfExists(root);
		}
	}

	@Test
	void dragAndDropCopiesFileWithCtrl() throws Exception {
		Path root = Files.createTempDirectory("simpleide-tree-dnd-copy-");
		try {
			Path filePath = Files.writeString(root.resolve("source.txt"), "content");
			Directory rootDirectory = new Directory(root);
			Tree tree = new Tree(rootDirectory);
			DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
			DefaultMutableTreeNode fileNode = childNamed(rootNode, "source.txt");

			SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(fileNode.getPath())));
			flushEdt();

			Transferable transferable = tree.getTransferHandler().createTransferable(tree);
			assertThat(transferable).isNotNull();

			DropTargetDropEvent dropEvent = createDropEvent(tree, rootNode, transferable, DnDConstants.ACTION_COPY);
			tree.getDropTarget().drop(dropEvent);
			flushEdt();

			assertThat(rootDirectory.getChildren()).extracting(TreeFile::getName).contains("source.txt", "source.txt");
		}
		finally {
			Files.deleteIfExists(root.resolve("source.txt"));
			Files.deleteIfExists(root);
		}
	}

	private DropTargetDropEvent createDropEvent(JTree tree, DefaultMutableTreeNode targetNode,
			Transferable transferable, int action) {
		Point point = new Point();
		SwingUtilities.invokeAndWait(() -> {
			TreePath path = new TreePath(targetNode.getPath());
			Rectangle bounds = tree.getPathBounds(path);
			point.setLocation(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
		});
		return new DropTargetDropEvent(tree.getDropTarget().getDropTargetContext(), point, action, transferable);
	}

}
