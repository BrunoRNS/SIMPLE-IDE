package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.rsta.ac.LanguageSupport;
import org.fife.rsta.ac.LanguageSupportFactory;
import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.client.LspEditorSupport;
import io.simpleide.lsp.core.Document;
import io.simpleide.thread.nodes.edt.listeners.DocumentNode;
import io.simpleide.ui.treefile.File;
import io.simpleide.utils.ResourceUtil;
import io.simpleide.utils.service.FileService;

public class EditorTab extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(EditorTab.class);
  private static final AtomicBoolean JAVA_JRE_CLASS_SOURCE_REGISTERED = new AtomicBoolean();

  private final Editor editor;
  private final Document document;
  private final EditorSearchPanel searchPanel;
  private final FileService fileService = new FileService();
  private LspEditorSupport lspSupport;
  private boolean localLanguageSupportRegistered;
  private AutoCompletion localAutoCompletion;
  private CompletionProvider localCompletionProvider;
  private boolean closed;
  private final ButtonExit buttonExit;
  private final File file;
  private final RTextScrollPane scrollPane;
  private final String name;
  private boolean isEdit;
  private DocumentNode documentNode;

  public EditorTab(File file, Document document) {
    this.editor = new Editor();
    this.searchPanel = new EditorSearchPanel(editor);
    this.isEdit = false;
    this.file = file;
    this.document = document;
    this.name = file.getName();
    this.scrollPane = new RTextScrollPane(editor);
    this.buttonExit = new ButtonExit(this);
    setLayout(new BorderLayout());
    add(searchPanel, BorderLayout.NORTH);
    add(this.scrollPane, BorderLayout.CENTER);
    editor.loadFile(file);
    configIsEdit();
  }

  @Override
  public String getName() {
    return name;
  }

  public Document getDocument() {
    return document;
  }

  public ButtonExit getButtonExit() {
    return this.buttonExit;
  }

  public File getFile() {
    return file;
  }

  public Editor getEditor() {
    return editor;
  }

  void showSearchPanel(boolean showReplacement) {
    searchPanel.open(showReplacement);
  }

  public void connectLanguageServer(LspClient client) {
    if (closed) {
      client.didClose(document.getUri());
      return;
    }
    unregisterLocalLanguageSupport();
    lspSupport = new LspEditorSupport(editor, document, client);
  }

  public void enableLocalLanguageSupport() {
    if (closed || lspSupport != null || hasLocalLanguageSupport()) {
      return;
    }
    if (!GraphicsEnvironment.isHeadless()) {
      String syntaxStyle = editor.getSyntaxEditingStyle();
      if (LanguageSupportFactory.get().getSupportFor(syntaxStyle) != null) {
        try {
          if ("text/java".equals(syntaxStyle)) {
            ensureJreClassesAvailable();
          }
          LanguageSupportFactory.get().register(editor);
          localLanguageSupportRegistered = true;
          return;
        } catch (RuntimeException error) {
          LOGGER.warn(
              "Unable to register local language support for {}; using keyword completion.",
              file.getPath(),
              error);
        }
      }
    }
    enableKeywordCompletion();
  }

  /**
   * RSTA's Java completion provider only knows about the classpath it is explicitly given. Without
   * the current JRE it cannot even resolve {@code java.lang.Object}, which produces the
   * "Couldn't find ClassFile for: java.lang.Object" warnings and leaves completion empty.
   */
  private static void ensureJreClassesAvailable() {
    if (!JAVA_JRE_CLASS_SOURCE_REGISTERED.compareAndSet(false, true)) {
      return;
    }
    LanguageSupport support = LanguageSupportFactory.get().getSupportFor("text/java");
    if (!(support instanceof JavaLanguageSupport javaSupport)) {
      return;
    }
    try {
      javaSupport.getJarManager().addCurrentJreClassFileSource();
    } catch (IOException | RuntimeException error) {
      LOGGER.warn("Unable to add the current JRE to local Java completion.", error);
    }
  }

  boolean hasLocalLanguageSupport() {
    return localLanguageSupportRegistered || localAutoCompletion != null;
  }

  CompletionProvider getLocalCompletionProvider() {
    return localCompletionProvider;
  }

  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definitionAtCaret() {
    if (lspSupport == null) {
      return CompletableFuture.failedFuture(new IllegalStateException("No language server is connected."));
    }
    return lspSupport.definitionAtCaret();
  }

  @SuppressWarnings("")
  public void goToRange(Range range) {
    if (range == null || range.getStart() == null) {
      return;
    }

    try {
      int start = toOffset(range.getStart());
      int end = range.getEnd() == null ? start : toOffset(range.getEnd());
      editor.setCaretPosition(start);
      editor.moveCaretPosition(Math.max(start, end));
      editor.requestFocusInWindow();
    } catch (BadLocationException error) {
      LOGGER.warn("Unable to navigate to a location in {}.", file.getPath(), error);
    }
  }

  public void publishDiagnostics(PublishDiagnosticsParams params) {
    if (lspSupport != null) {
      lspSupport.publishDiagnostics(params);
    }
  }

  public Icon getFileIcon() {
    // TODO: Move icon selection into per-language metadata.
    String iconName = switch (file.getTypeFile()) {
      case JAVA -> "java.png";
      case CSHARP -> null;
      case GROOVY -> "groovy.png";
      case KOTLIN -> "kotlin.png";
      case JAVASCRIPT -> "javascript.png";
      case PYTHON -> "python.png";
      case CPP -> "cpp.png";
      case C -> "c.png";
      case TXT, BINARY -> null;
      default -> null;
    };
    if (iconName == null) {
      return null;
    }

    try {
      return new ImageIcon(ResourceUtil.getResourcePath("io.simpleide.icon", iconName).toString());
    } catch (IOException exception) {
      LOGGER.warn("Unable to load file icon {}; using the default icon.", iconName, exception);
      return null;
    }
  }

  public boolean isEdit() {
    return isEdit;
  }

  public void save() throws IOException {
    String content = editor.getText();
    try {
      fileService.saveFile(file.getPath(), content);
    } catch (IOException error) {
      LOGGER.error("Unable to save file {}.", file.getPath(), error);
      try {
        new ErrorPopUP(
            "Unable to save " + file.getName() + ": " + error.getMessage(),
            this,
            ErrorTypePopUp.INTERNAL_ERROR).startManaged();
      } catch (RuntimeException popupError) {
        error.addSuppressed(popupError);
        LOGGER.warn("Unable to display the save error popup for {}.", file.getPath(), popupError);
      }
      throw error;
    }
    file.getCode().replace(0, file.getCode().length(), content);
    this.buttonExit.setNotEdited();
    if (lspSupport != null) {
      lspSupport.documentSaved();
    }
    isEdit = false;
    document.setModified(false);
  }

  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (lspSupport != null) {
      lspSupport.close();
    }
    unregisterLocalLanguageSupport();
    if (documentNode != null) {
      documentNode.close();
      editor.getDocument().removeDocumentListener(documentNode);
    }
    document.setOpened(false);
  }

  private void enableKeywordCompletion() {
    CompletionProvider provider =
        LocalCompletionProviders.forSyntaxStyle(editor.getSyntaxEditingStyle());
    if (provider == null) {
      return;
    }
    AutoCompletion autoCompletion = new AutoCompletion(provider);
    autoCompletion.setAutoActivationEnabled(true);
    autoCompletion.setAutoActivationDelay(300);
    autoCompletion.install(editor);
    localAutoCompletion = autoCompletion;
    localCompletionProvider = provider;
  }

  private void unregisterLocalLanguageSupport() {
    if (localAutoCompletion != null) {
      localAutoCompletion.uninstall();
      localAutoCompletion = null;
      localCompletionProvider = null;
    }
    if (localLanguageSupportRegistered) {
      try {
        LanguageSupportFactory.get().unregister(editor);
      } catch (RuntimeException error) {
        LOGGER.warn("Unable to unregister local language support for {}.", file.getPath(), error);
      }
      localLanguageSupportRegistered = false;
    }
  }

  private void configIsEdit() {
    documentNode = new DocumentNode(this::onDocumentChanged);
    editor.getDocument().addDocumentListener(documentNode);
    scrollPane.setLineNumbersEnabled(true);
  }

  private void onDocumentChanged() {
    if (closed) {
      return;
    }
    this.isEdit = true;
    document.setModified(true);
    document.incrementVersion();
    this.buttonExit.setEdited();
    if (lspSupport != null) {
      lspSupport.documentChanged();
    }
  }

  private int toOffset(Position position) throws BadLocationException {
    if (position.getLine() < 0 || position.getLine() >= editor.getLineCount()
        || position.getCharacter() < 0) {
      throw new BadLocationException("LSP position is outside the document.", -1);
    }
    int lineStart = editor.getLineStartOffset(position.getLine());
    int offset = lineStart + position.getCharacter();
    int lineEnd = Math.min(editor.getLineEndOffset(position.getLine()), editor.getDocument().getLength());
    if (offset > lineEnd) {
      throw new BadLocationException("LSP character is outside the line.", offset);
    }
    return offset;
  }

}
