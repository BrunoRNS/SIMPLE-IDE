package io.simpleide.ui.editor;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.fife.ui.autocomplete.Completion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.core.Document;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.icon.MaterialIcons;
import io.simpleide.ui.treefile.SimpleIDEFile;

/**
 * Tests {@link io.simpleide.ui.editor.EditorManager}.
 */
class EditorManagerTest {

	@TempDir
	static Path root;

	@AfterAll
	@SuppressWarnings("unused")
	static void closeLanguageServers() throws Exception {
		EditorManager manager = EditorManager.getInstance();
		if (manager != null) {
			SwingUtilities.invokeAndWait(manager::closeAll);
		}
	}

	@Test
	void addsCloseButtonBesideTheTabName() throws Exception {
		Path path = Files.writeString(root.resolve("Main.java"), "class Main {}");

		SwingUtilities.invokeAndWait(() -> {
			try {
				EditorManager manager = EditorManager.getInstance();
				if (manager == null) {
					manager = new EditorManager(() -> {
					}, () -> {
					}, () -> {
					});
				}
				EditorTab tab = new EditorTab(new SimpleIDEFile(path), new Document(path, "java"));

				manager.addNewTab(tab);

				JPanel header = (JPanel) manager.getJTabbedPane().getTabComponentAt(0);
				header.setSize(header.getPreferredSize());
				header.doLayout();
				assertThat(header.getComponent(0)).isInstanceOf(JLabel.class);
				assertThat(((JLabel) header.getComponent(0)).getText()).isEqualTo("Main.java");
				assertThat(header.getComponent(1)).isInstanceOf(ButtonExit.class);
				ButtonExit closeButton = (ButtonExit) header.getComponent(1);
				JLabel fileLabel = (JLabel) header.getComponent(0);
				assertThat(Math.abs((fileLabel.getY() + fileLabel.getHeight() / 2)
						- (closeButton.getY() + closeButton.getHeight() / 2)))
					.as("filename and close control should share a center line")
					.isLessThanOrEqualTo(1);
				int expectedIconSize = MaterialIcons.tabIconSize();
				assertThat(closeButton.getIcon().getIconWidth()).isEqualTo(expectedIconSize);
				closeButton.setEdited();
				assertThat(closeButton.getIcon().getIconHeight()).isEqualTo(expectedIconSize);

				manager.closeTab(tab);
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});
	}

	@Test
	void displaysOpenedLanguageFileBeforeLanguageServerConnects() throws Exception {
		Path path = Files.writeString(root.resolve("Main.java"), "class Main {}");
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;

		SwingUtilities.invokeAndWait(() -> {
			try {
				testManager.openFile(new SimpleIDEFile(path));

				assertThat(testManager.getJTabbedPane().getSelectedComponent()).isInstanceOf(EditorTab.class);
				assertThat(testManager.getSelectedEditor().getText()).isEqualTo("class Main {}");
				assertThat(testManager.getOpenFilePaths()).contains(path.toAbsolutePath().normalize());
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
			finally {
				testManager.closeTab(testManager.getOpenedEditor(path.toAbsolutePath().normalize()));
				assertThat(testManager.getOpenFilePaths()).doesNotContain(path.toAbsolutePath().normalize());
			}
		});
	}

	@Test
	void opensTabImmediatelyAndLoadsFileOffTheEdt() throws Exception {
		Path path = Files.writeString(root.resolve("Deferred.txt"), "file content");
		SimpleIDEFile file = SimpleIDEFile.lazy(path);
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;
		ThreadMaster.getInstance().initialize();

		SwingUtilities.invokeAndWait(() -> {
			testManager.openFile(file);
			EditorTab tab = testManager.getOpenedEditor(path.toAbsolutePath().normalize());
			assertThat(tab).isNotNull();
			assertThat(tab.isLoading()).isTrue();
			assertThat(testManager.getJTabbedPane().getSelectedComponent()).isSameAs(tab);
			assertThat(tab.getComponentCount()).isEqualTo(1);
		});

		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> SwingUtilities.invokeAndWait(() -> {
			EditorTab tab = testManager.getOpenedEditor(path.toAbsolutePath().normalize());
			assertThat(tab.isLoading()).isFalse();
			assertThat(tab.getEditor().getText()).isEqualTo("file content");
		}));
		SwingUtilities
			.invokeAndWait(() -> testManager.closeTab(testManager.getOpenedEditor(path.toAbsolutePath().normalize())));
	}

	@Test
	void opensLargeTextInThePagedReadOnlyViewer() throws Exception {
		Path path = root.resolve("Large.txt");
		String block = "This is a paged text line.\n".repeat(8192);
		try (var writer = Files.newBufferedWriter(path)) {
			for (int index = 0; index < 12; index++) {
				writer.write(block);
			}
		}
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;
		ThreadMaster.getInstance().initialize();

		SwingUtilities.invokeAndWait(() -> testManager.openFile(SimpleIDEFile.lazy(path)));
		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> SwingUtilities.invokeAndWait(() -> {
			EditorTab tab = testManager.getOpenedEditor(path.toAbsolutePath().normalize());
			assertThat(tab.isLoading()).isFalse();
			assertThat(tab.getEditor()).isNull();
			assertThat(tab.getLargeTextViewer()).isNotNull();
		}));
		SwingUtilities
			.invokeAndWait(() -> testManager.closeTab(testManager.getOpenedEditor(path.toAbsolutePath().normalize())));
	}

	@Test
	void opensImagesAndBinaryFilesInTheirDedicatedTabs() throws Exception {
		Path binaryPath = root.resolve("payload.bin");
		Files.write(binaryPath, new byte[] { 0, 0x41, (byte) 0xff });
		Path imagePath = root.resolve("picture.png");
		ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png", imagePath.toFile());
		Path svgPath = root.resolve("picture.svg");
		Files.writeString(svgPath,
				"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"9\" height=\"7\"><rect width=\"9\" height=\"7\" fill=\"red\"/></svg>");
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;

		SwingUtilities.invokeAndWait(() -> {
			try {
				testManager.openFile(new SimpleIDEFile(binaryPath));
				EditorTab binaryTab = testManager.getOpenedEditor(binaryPath.toAbsolutePath().normalize());
				assertThat(binaryTab.getEditor()).isNull();
				assertThat(binaryTab.getHexEditor()).isNotNull();
				binaryTab.getHexEditor().setByte(1, 0x42);
				testManager.saveSelectedFile();
				assertThat(binaryTab.isEdit()).isFalse();

				testManager.openFile(new SimpleIDEFile(imagePath));
				EditorTab imageTab = testManager.getOpenedEditor(imagePath.toAbsolutePath().normalize());
				assertThat(imageTab.getEditor()).isNull();
				assertThat(imageTab.getImageViewer().imageWidth()).isEqualTo(12);
				assertThat(imageTab.getImageViewer().imageHeight()).isEqualTo(8);

				testManager.openFile(new SimpleIDEFile(svgPath));
				EditorTab svgTab = testManager.getOpenedEditor(svgPath.toAbsolutePath().normalize());
				assertThat(svgTab.getImageViewer().imageWidth()).isEqualTo(9);
				assertThat(svgTab.getImageViewer().imageHeight()).isEqualTo(7);

				testManager.closeTab(binaryTab);
				testManager.closeTab(imageTab);
				testManager.closeTab(svgTab);
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});
		assertThat(Files.readAllBytes(binaryPath)).containsExactly((byte) 0, (byte) 0x42, (byte) 0xff);
	}

	@Test
	void opensTabImmediatelyAndLoadsFileOffTheEdt() throws Exception {
		Path path = Files.writeString(root.resolve("Deferred.txt"), "file content");
		File file = File.lazy(path);
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;
		ThreadMaster.getInstance().initialize();

		SwingUtilities.invokeAndWait(() -> {
			testManager.openFile(file);
			EditorTab tab = testManager.getOpenedEditor(path.toAbsolutePath().normalize());
			assertThat(tab).isNotNull();
			assertThat(tab.isLoading()).isTrue();
			assertThat(testManager.getJTabbedPane().getSelectedComponent()).isSameAs(tab);
			assertThat(tab.getComponentCount()).isEqualTo(1);
		});

		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> SwingUtilities.invokeAndWait(() -> {
			EditorTab tab = testManager.getOpenedEditor(path.toAbsolutePath().normalize());
			assertThat(tab.isLoading()).isFalse();
			assertThat(tab.getEditor().getText()).isEqualTo("file content");
		}));
		SwingUtilities
			.invokeAndWait(() -> testManager.closeTab(testManager.getOpenedEditor(path.toAbsolutePath().normalize())));
	}

	@Test
	void opensLargeTextInThePagedReadOnlyViewer() throws Exception {
		Path path = root.resolve("Large.txt");
		String block = "This is a paged text line.\n".repeat(8192);
		try (var writer = Files.newBufferedWriter(path)) {
			for (int index = 0; index < 12; index++) {
				writer.write(block);
			}
		}
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;
		ThreadMaster.getInstance().initialize();

		SwingUtilities.invokeAndWait(() -> testManager.openFile(File.lazy(path)));
		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> SwingUtilities.invokeAndWait(() -> {
			EditorTab tab = testManager.getOpenedEditor(path.toAbsolutePath().normalize());
			assertThat(tab.isLoading()).isFalse();
			assertThat(tab.getEditor()).isNull();
			assertThat(tab.getLargeTextViewer()).isNotNull();
		}));
		SwingUtilities
			.invokeAndWait(() -> testManager.closeTab(testManager.getOpenedEditor(path.toAbsolutePath().normalize())));
	}

	@Test
	void opensImagesAndBinaryFilesInTheirDedicatedTabs() throws Exception {
		Path binaryPath = root.resolve("payload.bin");
		Files.write(binaryPath, new byte[] { 0, 0x41, (byte) 0xff });
		Path imagePath = root.resolve("picture.png");
		ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png", imagePath.toFile());
		Path svgPath = root.resolve("picture.svg");
		Files.writeString(svgPath,
				"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"9\" height=\"7\"><rect width=\"9\" height=\"7\" fill=\"red\"/></svg>");
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;

		SwingUtilities.invokeAndWait(() -> {
			try {
				testManager.openFile(new File(binaryPath));
				EditorTab binaryTab = testManager.getOpenedEditor(binaryPath.toAbsolutePath().normalize());
				assertThat(binaryTab.getEditor()).isNull();
				assertThat(binaryTab.getHexEditor()).isNotNull();
				binaryTab.getHexEditor().setByte(1, 0x42);
				testManager.saveSelectedFile();
				assertThat(binaryTab.isEdit()).isFalse();

				testManager.openFile(new File(imagePath));
				EditorTab imageTab = testManager.getOpenedEditor(imagePath.toAbsolutePath().normalize());
				assertThat(imageTab.getEditor()).isNull();
				assertThat(imageTab.getImageViewer().imageWidth()).isEqualTo(12);
				assertThat(imageTab.getImageViewer().imageHeight()).isEqualTo(8);

				testManager.openFile(new File(svgPath));
				EditorTab svgTab = testManager.getOpenedEditor(svgPath.toAbsolutePath().normalize());
				assertThat(svgTab.getImageViewer().imageWidth()).isEqualTo(9);
				assertThat(svgTab.getImageViewer().imageHeight()).isEqualTo(7);

				testManager.closeTab(binaryTab);
				testManager.closeTab(imageTab);
				testManager.closeTab(svgTab);
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});
		assertThat(Files.readAllBytes(binaryPath)).containsExactly((byte) 0, (byte) 0x42, (byte) 0xff);
	}

	@Test
	void savesEveryModifiedEditorBeforeApplicationShutdown() throws Exception {
		Path path = Files.writeString(root.resolve("Shutdown.java"), "class Shutdown {}\n");
		EditorManager manager = EditorManager.getInstance();
		if (manager == null) {
			manager = new EditorManager(() -> {
			}, () -> {
			}, () -> {
			});
		}
		EditorManager testManager = manager;
		java.util.concurrent.atomic.AtomicReference<EditorTab> opened = new java.util.concurrent.atomic.AtomicReference<>();

		SwingUtilities.invokeAndWait(() -> {
			try {
				testManager.openFile(new SimpleIDEFile(path));
				EditorTab tab = testManager.getOpenedEditor(path.toAbsolutePath().normalize());
				opened.set(tab);
				tab.getEditor().setText("class Shutdown { int saved = 1; }\n");
				assertThat(testManager.saveAllModifiedFiles()).isTrue();
				assertThat(tab.isEdit()).isFalse();
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});
		assertThat(Files.readString(path)).isEqualTo("class Shutdown { int saved = 1; }\n");
		await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(opened.get().isEdit()).isFalse());
		SwingUtilities.invokeAndWait(() -> testManager.closeTab(opened.get()));
	}

	@Test
	void refreshesFilenameAndTabStatusIconsAfterGlobalFontSizeChanges() throws Exception {
		Path path = Files.writeString(root.resolve("Scaled.java"), "class Scaled {}");
		SwingUtilities.invokeAndWait(() -> {
			try {
				EditorManager manager = EditorManager.getInstance();
				if (manager == null) {
					manager = new EditorManager(() -> {
					}, () -> {
					}, () -> {
					});
				}
				EditorTab tab = new EditorTab(new SimpleIDEFile(path), new Document(path, "java"));
				manager.addNewTab(tab);
				JPanel header = (JPanel) manager.getJTabbedPane().getTabComponentAt(0);
				JLabel fileLabel = (JLabel) header.getComponent(0);
				ButtonExit closeButton = tab.getButtonExit();
				Object originalFont = UIManager.get("Menu.font");
				try {
					UIManager.put("Menu.font", new FontUIResource(Font.DIALOG, Font.PLAIN, 21));
					EditorManager.refreshFileIcons();
					assertThat(fileLabel.getIcon().getIconWidth()).isEqualTo(21);
					assertThat(closeButton.getIcon().getIconWidth()).isEqualTo(21);
					closeButton.setEdited();
					EditorManager.refreshFileIcons();
					assertThat(closeButton.getIcon().getIconWidth()).isEqualTo(21);
				}
				finally {
					if (originalFont == null) {
						UIManager.getDefaults().remove("Menu.font");
					}
					else {
						UIManager.put("Menu.font", originalFont);
					}
					EditorManager.refreshFileIcons();
					manager.closeTab(tab);
				}
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});
	}

	@Test
	void usesLocalLanguageSupportWhenNoLanguageServerMatches() throws Exception {
		Path path = Files.writeString(root.resolve("Fallback.java"), "class Fallback {}");
		AtomicReference<EditorTab> tab = new AtomicReference<>();

		SwingUtilities.invokeAndWait(() -> {
			try {
				tab.set(new EditorTab(new SimpleIDEFile(path), new Document(path, "java")));
				EditorManager.completeLanguageServerConnection(tab.get(), null, null);
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});

		await().atMost(10, TimeUnit.SECONDS)
			.untilAsserted(
					() -> SwingUtilities.invokeAndWait(() -> assertThat(tab.get().hasLocalLanguageSupport()).isTrue()));

		SwingUtilities.invokeAndWait(() -> {
			tab.get().close();
			assertThat(tab.get().hasLocalLanguageSupport()).isFalse();
		});
	}

	@Test
	void enablesKeywordCompletionForLanguagesWithoutLocalSupport() throws Exception {
		Path path = Files.writeString(root.resolve("Main.py"), "def main():\n    pass\n");
		AtomicReference<EditorTab> tab = new AtomicReference<>();

		SwingUtilities.invokeAndWait(() -> {
			try {
				tab.set(new EditorTab(new SimpleIDEFile(path), new Document(path, "python")));
				tab.get().enableLocalLanguageSupport();
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});

		await().atMost(10, TimeUnit.SECONDS)
			.untilAsserted(() -> SwingUtilities
				.invokeAndWait(() -> assertThat(tab.get().getLocalCompletionProvider()).isNotNull()));

		SwingUtilities.invokeAndWait(() -> {
			assertThat(tab.get().getLocalCompletionProvider().getCompletions(tab.get().getEditor()))
				.extracting(Completion::getInputText)
				.contains("def", "class", "import");
			tab.get().close();
		});
	}

}
