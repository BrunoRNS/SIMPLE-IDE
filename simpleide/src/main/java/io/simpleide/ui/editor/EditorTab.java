package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

  private final Editor editor;
  private final Document document;
  private final FileService fileService = new FileService();
  private LspEditorSupport lspSupport;
  private boolean closed;
  private ButtonExit buttonExit;
  private File file;
  private RTextScrollPane scrollPane;
  private String name;
  private boolean isEdit;
  private DocumentNode documentNode;

  public EditorTab(File file, Document document) {
    this.editor = new Editor();
    this.isEdit = false;
    this.file = file;
    this.document = document;
    this.name = file.getName();
    this.scrollPane = new RTextScrollPane(editor);
    this.buttonExit = new ButtonExit(this);
    setLayout(new BorderLayout());
    add(this.scrollPane, BorderLayout.CENTER);
    editor.loadFile(file);
    configIsEdit();
  }

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

  public void connectLanguageServer(LspClient client) {
    if (closed) {
      client.didClose(document.getUri());
      return;
    }
    lspSupport = new LspEditorSupport(editor, document, client);
  }

  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definitionAtCaret() {
    if (lspSupport == null) {
      return CompletableFuture.failedFuture(new IllegalStateException("No language server is connected."));
    }
    return lspSupport.definitionAtCaret();
  }

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
      case ASM -> "assembly.png";
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
    if (documentNode != null) {
      documentNode.close();
      editor.getDocument().removeDocumentListener(documentNode);
    }
    document.setOpened(false);
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
