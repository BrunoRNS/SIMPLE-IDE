package io.simpleide.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.simpleide.ui.editor.Editor;
import java.util.List;
import javax.swing.SwingUtilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.fife.ui.autocomplete.Completion;
import org.junit.jupiter.api.Test;

class LspCompletionEditApplierTest {

  @Test
  void appliesAutoImportEditAlongsideCompletion() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Editor editor = new Editor();
          editor.setText("class Example {\n  Lis\n}\n");
          editor.setCaretPosition(editor.getText().indexOf("Lis") + "Lis".length());

          CompletionItem item = new CompletionItem("List");
          item.setFilterText("Lis");
          item.setAdditionalTextEdits(
              List.of(
                  new TextEdit(
                      new Range(new Position(0, 0), new Position(0, 0)),
                      "import java.util.List;\n\n")));

          LspCompletionProvider provider = new LspCompletionProvider();
          provider.setItems(List.of(item));
          TestAutoCompletion autoCompletion = new TestAutoCompletion(editor, provider);
          autoCompletion.install(editor);
          try {
            Completion completion = provider.getCompletionByInputText("Lis").get(0);
            autoCompletion.insert(completion);

            assertThat(editor.getText())
                .isEqualTo("import java.util.List;\n\nclass Example {\n  List\n}\n");
          } finally {
            autoCompletion.uninstall();
          }
        });
  }

  private static final class TestAutoCompletion extends LspAutoCompletion {
    private TestAutoCompletion(Editor editor, LspCompletionProvider provider) {
      super(editor, provider);
    }

    private void insert(Completion completion) {
      insertCompletion(completion, false);
    }
  }
}
