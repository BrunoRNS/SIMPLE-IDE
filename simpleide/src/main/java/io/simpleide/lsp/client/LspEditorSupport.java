package io.simpleide.lsp.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.Timer;
import javax.swing.text.BadLocationException;

import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.ui.autocomplete.AutoCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.core.Document;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.editor.Editor;

public final class LspEditorSupport implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LspEditorSupport.class);

  private final Editor editor;
  private final Document document;
  private final LspClient client;
  private final LspDiagnosticParser diagnosticParser;
  private final LspCompletionProvider completionProvider;
  private final AutoCompletion autoCompletion;
  private final Timer completionTimer;
  private String lastSentText;
  private volatile boolean closed;

  public LspEditorSupport(Editor editor, Document document, LspClient client) {
    this.editor = editor;
    this.document = document;
    this.client = client;

    diagnosticParser = new LspDiagnosticParser(editor);
    editor.addParser(diagnosticParser);

    completionProvider = new LspCompletionProvider();
    autoCompletion = new LspAutoCompletion(editor, completionProvider);
    autoCompletion.setAutoActivationEnabled(true);
    autoCompletion.setAutoActivationDelay(250);
    autoCompletion.setAutoCompleteSingleChoices(false);
    autoCompletion.setShowDescWindow(true);
    autoCompletion.install(editor);
    completionTimer = new Timer(180, ignored -> requestCompletions());
    completionTimer.setRepeats(false);

    lastSentText = editor.getText();
    client.didOpen(new TextDocumentItem(
        document.getUri(),
        document.getLanguageId(),
        document.getVersion(),
        lastSentText));
  }

  public void documentChanged() {
    if (closed) {
      return;
    }

    String currentText = editor.getText();
    client.didChange(document.getUri(), document.getVersion(), lastSentText, currentText);
    lastSentText = currentText;
    completionTimer.restart();
  }

  public void documentSaved() {
    if (!closed) {
      client.didSave(document.getUri(), editor.getText());
    }
  }

  public void publishDiagnostics(PublishDiagnosticsParams params) {
    if (closed || params == null || !document.getUri().equals(params.getUri())) {
      return;
    }
    List<Diagnostic> diagnostics = params.getDiagnostics() == null ? List.of() : params.getDiagnostics();
    diagnosticParser.setDiagnostics(diagnostics);
  }

  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definitionAtCaret() {
    if (closed) {
      return CompletableFuture.failedFuture(new IllegalStateException("Editor support is closed."));
    }

    try {
      Position position = getCaretPosition();
      return client.definition(new DefinitionParams(new TextDocumentIdentifier(document.getUri()), position));
    } catch (BadLocationException error) {
      return CompletableFuture.failedFuture(error);
    }
  }

  private void requestCompletions() {
    if (closed || !editor.isEnabled()) {
      return;
    }

    int version = document.getVersion();
    Position position;
    try {
      position = getCaretPosition();
    } catch (BadLocationException error) {
      LOGGER.warn("Unable to map editor caret to an LSP position for {}.", document.getUri(), error);
      return;
    }

    CompletionParams params = new CompletionParams(
        new TextDocumentIdentifier(document.getUri()),
        position,
        new CompletionContext(CompletionTriggerKind.Invoked));
    client.completion(params).whenComplete((completionResult, error) -> {
      if (error != null) {
        LOGGER.debug("LSP completion request failed for {}.", document.getUri(), error);
        return;
      }
      if (closed) {
        return;
      }

      List<CompletionItem> resolvedItems = getCompletionItems(completionResult);
      ThreadMaster.getInstance().executeOnEDT(() -> {
        if (closed || document.getVersion() != version
            || editor.getCaretPosition() != positionToOffset(position)) {
          return;
        }
        completionProvider.setItems(resolvedItems);
        int caret = editor.getCaretPosition();
        if(caret > 0 && editor.hasFocus() && ! resolvedItems.isEmpty()){
        	try{
        		char lastChar = editor.getText(caret-1,1).charAt(0);
        		if(Character.isLetterOrDigit(lastChar) || lastChar == '.'){
        			autoCompletion.doCompletion();
        		}
        	}catch(BadLocationException i){}
        }
      });
    });
  }

  public CompletableFuture<Void> formatDocument() {
	  if (closed) {
	    return CompletableFuture.completedFuture(null);
	  }
	
	  DocumentFormattingParams params = new DocumentFormattingParams(
	      new TextDocumentIdentifier(document.getUri()),
	      new FormattingOptions(4, true) 
	  );
	
	  return client.formatting(params).thenAcceptAsync(edits -> {
	    if (edits == null || edits.isEmpty() || closed) {
	      return;
	    }
	    
	    ThreadMaster.getInstance().executeOnEDT(() -> applyFormattingEdits(edits));
	  }, ThreadMaster.getInstance()::executeOnEDT);
	}

	private void applyFormattingEdits(List<? extends TextEdit> edits) {
	  for (int i = edits.size() - 1; i >= 0; i--) {
	    TextEdit edit = edits.get(i);
	    try {
	      int start = positionToOffset(edit.getRange().getStart());
	      int end = positionToOffset(edit.getRange().getEnd());
	      editor.replaceRange(edit.getNewText(), start, end);
	    } catch (Exception error) {
	      LOGGER.warn("Erro ao aplicar formatação do LSP em {}.", document.getUri(), error);
	    }
	  }
	}

  private Position getCaretPosition() throws BadLocationException {
    int caret = editor.getCaretPosition();
    int line = editor.getLineOfOffset(caret);
    return new Position(line, caret - editor.getLineStartOffset(line));
  }

  private int positionToOffset(Position position) {
    try {
      return editor.getLineStartOffset(position.getLine()) + position.getCharacter();
    } catch (BadLocationException error) {
      LOGGER.debug("Unable to map LSP position back to editor offset for {}.", document.getUri(), error);
      return -1;
    }
  }

  private List<CompletionItem> getCompletionItems(
      Either<List<CompletionItem>, CompletionList> completionResult) {
    if (completionResult == null) {
      return List.of();
    }
    if (completionResult.isLeft()) {
      List<CompletionItem> items = completionResult.getLeft();
      return items == null ? List.of() : items;
    }
    CompletionList completionList = completionResult.getRight();
    if (completionList == null || completionList.getItems() == null) {
      return List.of();
    }
    return completionList.getItems();
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    completionTimer.stop();
    client.didClose(document.getUri());
    autoCompletion.uninstall();
    editor.removeParser(diagnosticParser);
  }
}
