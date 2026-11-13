package io.simpleide.ui.editor;

import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.lsp.core.DocumentManager;
import io.simpleide.lsp.core.LanguageServerManager;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;

public class EditorManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(EditorManager.class);

  private final Map<Path, EditorTab> openedEditors = new HashMap<>();
  private static EditorManager instance = null;
  private final DocumentManager manager;
  private final LanguageServerManager languageServers;
  private final EditorActions editorActions;
  private JTabbedPane jTabbedPane;
  private Runnable createFile;
  @SuppressWarnings("unused")
  private Runnable createFolder;
  private Runnable openFolder;

  public EditorManager(Runnable createFile, Runnable createFolder, Runnable openFolder) {
    this(createFile, createFolder, openFolder, ignored -> {
    });
  }

  public EditorManager(
      Runnable createFile,
      Runnable createFolder,
      Runnable openFolder,
      Consumer<LspStatus> statusListener) {
    if (instance == null) {
      instance = this;
      this.createFile = createFile;
      this.createFolder = createFolder;
      this.openFolder = openFolder;
      this.jTabbedPane = new JTabbedPane();
      configJTabbedPane();
      showWelcomeScreen();
    }
    this.manager = new DocumentManager();
    this.languageServers = new LanguageServerManager(statusListener);
    this.editorActions = new EditorActions(this);
  }

  public static EditorManager getInstance() {
    return instance;
  }

  public JTabbedPane getJTabbedPane() {
    return jTabbedPane;
  }

  public Editor getSelectedEditor() {
    if (jTabbedPane.getSelectedComponent() instanceof EditorTab tab) {
      return tab.getEditor();
    }
    return null;
  }

  @SuppressWarnings("")
  public void openFile(File file) {
    if (file == null) {
      return;
    }

    Path path = file.getPath().toAbsolutePath().normalize();

    EditorTab existing = openedEditors.get(path);

    if (existing != null) {
      jTabbedPane.setSelectedComponent(existing);
      return;
    }

    Document document = manager.open(
        path,
        getLanguageId(file.getTypeFile()));

    EditorTab editorTab = new EditorTab(file, document);
    editorActions.configure(editorTab);
    removeWelcomeScreen();

    jTabbedPane.addTab(
        file.getName(),
        editorTab.getFileIcon(),
        editorTab);
    jTabbedPane.setTabComponentAt(
        jTabbedPane.indexOfComponent(editorTab),
        createTabHeader(editorTab));

    openedEditors.put(path, editorTab);
    jTabbedPane.setSelectedComponent(editorTab);

    try {
      editorTab.enableLocalLanguageSupport();
    } catch (RuntimeException | LinkageError error) {
      LOGGER.error(
          "Unable to initialize local language support for {}; the file remains open.",
          path,
          error);
    }
    try {
      ThemeManager.getInstance().register(editorTab.getEditor());
    } catch (RuntimeException error) {
      LOGGER.error("Unable to apply the editor theme to {}; the file remains open.", path, error);
    }

    languageServers.connect(document, editorTab::publishDiagnostics)
        .whenComplete((client, error) -> completeLanguageServerConnection(editorTab, client, error));
  }

  public void addNewTab(EditorTab tab) {
    if (tab == null) {
      return;
    }

    tab.enableLocalLanguageSupport();
    editorActions.configure(tab);
    if (jTabbedPane.getTabCount() == 1 && "Welcome".equals(jTabbedPane.getTitleAt(0))) {
      jTabbedPane.removeAll();
    }
    ThemeManager.getInstance().register(tab.getEditor());
    jTabbedPane.addTab(tab.getName(), tab.getFileIcon(), tab);
    jTabbedPane.setTabComponentAt(
        jTabbedPane.indexOfComponent(tab),
        createTabHeader(tab));
    jTabbedPane.setSelectedComponent(tab);
  }

  public void closeAll() {
    try (languageServers) {
      openedEditors.values().forEach(tab -> {
        tab.close();
        manager.close(tab.getDocument().getUri());
        languageServers.disconnect(tab.getDocument());
        ThemeManager.getInstance().unregister(tab.getEditor());
      });
      openedEditors.clear();
    }
  }

  public void closeTab(EditorTab tab) {
    if (tab == null || jTabbedPane.indexOfComponent(tab) < 0) {
      return;
    }

    if (tab.isEdit()) {
      int choice = JOptionPane.showConfirmDialog(
          tab,
          "Save changes to " + tab.getFile().getName() + " before closing?",
          "Unsaved changes",
          JOptionPane.YES_NO_CANCEL_OPTION,
          JOptionPane.WARNING_MESSAGE);
      if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
        return;
      }
      if (choice == JOptionPane.YES_OPTION) {
        saveTab(tab);
        if (tab.isEdit()) {
          return;
        }
      }
    }

    int tabIndex = jTabbedPane.indexOfComponent(tab);
    Path path = tab.getFile().getPath().toAbsolutePath().normalize();
    tab.close();
    manager.close(tab.getDocument().getUri());
    languageServers.disconnect(tab.getDocument());
    ThemeManager.getInstance().unregister(tab.getEditor());
    openedEditors.remove(path, tab);
    jTabbedPane.removeTabAt(tabIndex);
    if (jTabbedPane.getTabCount() == 0) {
      showWelcomeScreen();
    }
  }

  public void saveSelectedFile() {
    EditorTab selectedTab = getSelectedTab();
    if (selectedTab != null) {
      saveTab(selectedTab);
    }
  }

  public EditorTab getOpenedEditor(Path path) {
    return openedEditors.get(path);
  }

  public void selectEditorTab(EditorTab tab) {
    jTabbedPane.setSelectedComponent(tab);
  }

  private JPanel createTabHeader(EditorTab tab) {
    JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
    header.setOpaque(false);
    header.add(new JLabel(tab.getName(), tab.getFileIcon(), SwingConstants.LEADING));
    header.add(tab.getButtonExit());
    return header;
  }

  void saveTab(EditorTab tab) {
    if (!tab.isEdit()) {
      return;
    }
    try {
      tab.save();
    } catch (IOException error) {
      LOGGER.warn("Save failed; keeping unsaved changes for {}.", tab.getFile().getPath());
    }
  }

  private EditorTab getSelectedTab() {
    return jTabbedPane.getSelectedComponent() instanceof EditorTab tab ? tab : null;
  }

  private void showWelcomeScreen() {
    jTabbedPane.addTab("Welcome", EditorWelcomeScreen.create(openFolder, createFile));
  }

  private String getLanguageId(TypeFile type) {
    return switch (type) {
      case JAVA -> "java";
      case CSHARP -> "csharp";
      case KOTLIN -> "kotlin";
      case GROOVY -> "groovy";
      case JAVASCRIPT -> "javascript";
      case GO -> "go";
      case RUST -> "rust";
      case PYTHON -> "python";
      case C -> "c";
      case CPP -> "cpp";
      default -> "plaintext";
    };
  }

  static void completeLanguageServerConnection(EditorTab tab, LspClient client, Throwable error) {
    if (error != null) {
      Throwable cause = error;

      if (error instanceof CompletionException && error.getCause() != null) {
        cause = error.getCause();
      }

      LOGGER.error(
          "LSP connection failed for {}.",
          tab.getFile().getPath(),
          cause);

      if (client != null) {
        try {
          client.close();
        } catch (Exception closeError) {
          LOGGER.warn("Failed to close LSP client.", closeError);
        }
      }
    }

    ThreadMaster.getInstance().executeOnEDT(() -> {
      if (client == null || error != null) {
        tab.enableLocalLanguageSupport();
      } else {
        tab.connectLanguageServer(client);
      }
    });
  }

  private void configJTabbedPane() {
    this.jTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
  }

  private void removeWelcomeScreen() {
    if (jTabbedPane.getTabCount() == 1
        && "Welcome".equals(jTabbedPane.getTitleAt(0))) {
      jTabbedPane.removeAll();
    }
  }
}
