package io.simpleide.lsp.client;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LspCompletionProvider extends DefaultCompletionProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(LspCompletionProvider.class);

  @SuppressWarnings("")
  public void setItems(List<CompletionItem> items) {
    clear();
    List<Completion> completions_ = new ArrayList<>();
    for (CompletionItem item : items) {
      if (item.getLabel() == null || item.getLabel().isBlank()) {
        continue;
      }

      String insertion = getInsertionText(item);
      String inputText = item.getFilterText() == null ? item.getLabel() : item.getFilterText();
      LspCompletion completion =
          new LspCompletion(
              this, insertion, item.getDetail(), getDocumentation(item), item, inputText);
      completions_.add(completion);
    }
    addCompletions(completions_);
  }

  private String getInsertionText(CompletionItem item) {
    String insertion = item.getInsertText();
    Either<TextEdit, InsertReplaceEdit> textEdit = item.getTextEdit();
    if (textEdit != null) {
      if (textEdit.isLeft() && textEdit.getLeft() != null) {
        insertion = textEdit.getLeft().getNewText();
      } else if (textEdit.isRight() && textEdit.getRight() != null) {
        insertion = textEdit.getRight().getNewText();
      }
    }
    if (insertion == null || insertion.isEmpty()) {
      insertion = item.getLabel();
    }
    if (item.getInsertTextFormat() == org.eclipse.lsp4j.InsertTextFormat.Snippet) {
      insertion =
          insertion
              .replaceAll("\\$\\{\\d+:([^}]*)\\}", "$1")
              .replaceAll("\\$\\{\\d+\\}|\\$\\d", "");
    }
    return insertion;
  }

  private static Range getInsertRange(CompletionItem item) {
    Either<TextEdit, InsertReplaceEdit> textEdit = item.getTextEdit();
    if (textEdit == null) {
      return null;
    }
    if (textEdit.isLeft()) {
      return textEdit.getLeft() == null ? null : textEdit.getLeft().getRange();
    }
    return textEdit.getRight() == null ? null : textEdit.getRight().getInsert();
  }

  private static int getOffset(RSyntaxTextArea textArea, Position position)
      throws BadLocationException {
    int lineStart = textArea.getLineStartOffset(position.getLine());
    int offset = lineStart + position.getCharacter();
    if (offset > textArea.getLineEndOffset(position.getLine())) {
      throw new BadLocationException("LSP completion position exceeds line bounds.", offset);
    }
    return offset;
  }

  private String getDocumentation(CompletionItem item) {
    Either<String, org.eclipse.lsp4j.MarkupContent> documentation = item.getDocumentation();
    if (documentation == null) {
      return item.getDetail();
    }
    if (documentation.isLeft()) {
      return documentation.getLeft();
    }
    org.eclipse.lsp4j.MarkupContent markup = documentation.getRight();
    return markup == null ? item.getDetail() : markup.getValue();
  }

  static final class LspCompletion extends BasicCompletion {
    private final CompletionItem item;
    private final String filterText;

    private LspCompletion(
        LspCompletionProvider provider,
        String replacementText,
        String shortDescription,
        String summary,
        CompletionItem item,
        String filterText) {
      super(provider, replacementText, shortDescription, summary);
      this.item = item;
      this.filterText = filterText;
    }

    CompletionItem getItem() {
      return item;
    }

    @Override
    public String getInputText() {
      return filterText;
    }

    @Override
    public String getAlreadyEntered(JTextComponent component) {
      Range editRange = getInsertRange(item);
      if (editRange == null || !(component instanceof RSyntaxTextArea textArea)) {
        return super.getAlreadyEntered(component);
      }

      try {
        int startOffset = getOffset(textArea, editRange.getStart());
        int endOffset = getOffset(textArea, editRange.getEnd());
        int caret = component.getCaretPosition();
        if (startOffset > caret || endOffset != caret) {
          LOGGER.warn("Ignoring invalid LSP completion edit range for {}.", item.getLabel());
          return super.getAlreadyEntered(component);
        }
        return component.getDocument().getText(startOffset, caret - startOffset);
      } catch (BadLocationException error) {
        LOGGER.warn("Unable to apply LSP completion edit range for {}.", item.getLabel(), error);
        return super.getAlreadyEntered(component);
      }
    }
  }
}
