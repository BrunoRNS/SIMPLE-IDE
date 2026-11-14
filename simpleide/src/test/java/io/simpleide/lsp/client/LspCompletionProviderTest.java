package io.simpleide.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.simpleide.ui.editor.Editor;
import javax.swing.SwingUtilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class LspCompletionProviderTest {

  @Test
  void mapsLspSnippetCompletionsIntoRsyntaxCompletions() {
    LspCompletionProvider provider = new LspCompletionProvider();
    CompletionItem item = new CompletionItem("println");
    item.setFilterText("print");
    item.setInsertText("println(${1:value})$0");
    item.setInsertTextFormat(InsertTextFormat.Snippet);
    item.setDetail("Prints a value");
    provider.setItems(java.util.List.of(item));

    var completion = provider.getCompletionByInputText("print").get(0);

    assertThat(completion.getReplacementText()).isEqualTo("println(value)");
    assertThat(completion.getInputText()).isEqualTo("print");
  }

  @Test
  void usesLspTextEditRangeWhenReplacingCompletionPrefix() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Editor editor = new Editor();
          editor.setText("prefix suf");
          editor.setCaretPosition(editor.getDocument().getLength());

          LspCompletionProvider provider = new LspCompletionProvider();
          CompletionItem item = new CompletionItem("suggestion");
          item.setTextEdit(
              Either.forLeft(
                  new TextEdit(
                      new Range(new Position(0, 0), new Position(0, editor.getCaretPosition())),
                      "suggestion")));
          provider.setItems(java.util.List.of(item));

          var completion = provider.getCompletionByInputText("suggestion").get(0);

          assertThat(completion.getAlreadyEntered(editor)).isEqualTo("prefix suf");
        });
  }
}
