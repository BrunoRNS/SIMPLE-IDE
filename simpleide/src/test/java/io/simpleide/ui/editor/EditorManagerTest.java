package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.core.Document;
import io.simpleide.ui.treefile.File;

class EditorManagerTest {

  @TempDir
  Path root;

  @Test
  void addsCloseButtonBesideTheTabName() throws Exception {
    Path path = Files.writeString(root.resolve("Main.java"), "class Main {}");

    SwingUtilities.invokeAndWait(() -> {
      try {
        EditorManager manager = new EditorManager(() -> {}, () -> {}, () -> {});
        EditorTab tab = new EditorTab(new File(path), new Document(path, "java"));

        manager.addNewTab(tab);

        JPanel header = (JPanel) manager.getJTabbedPane().getTabComponentAt(0);
        assertThat(header.getComponent(0)).isInstanceOf(JLabel.class);
        assertThat(((JLabel) header.getComponent(0)).getText()).isEqualTo("Main.java");
        assertThat(header.getComponent(1)).isInstanceOf(ButtonExit.class);
        ButtonExit closeButton = (ButtonExit) header.getComponent(1);
        assertThat(closeButton.getIcon().getIconWidth()).isEqualTo(12);
        closeButton.setEdited();
        assertThat(closeButton.getIcon().getIconHeight()).isEqualTo(12);

        manager.closeTab(tab);
      } catch (IOException error) {
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
        tab.set(new EditorTab(new File(path), new Document(path, "java")));
        EditorManager.completeLanguageServerConnection(tab.get(), null, null);
      } catch (IOException error) {
        throw new AssertionError(error);
      }
    });

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
      SwingUtilities.invokeAndWait(() ->
        assertThat(tab.get().getEditor().getParserCount()).isGreaterThan(0)));

    SwingUtilities.invokeAndWait(() -> {
      tab.get().close();
      assertThat(tab.get().getEditor().getParserCount()).isZero();
    });
  }
}
