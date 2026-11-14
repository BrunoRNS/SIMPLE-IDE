package io.simpleide.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.simpleide.ui.editor.Editor;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.eclipse.lsp4j.CompletionItem;
import org.junit.jupiter.api.Test;

class LspAutoCompletionTest {

  @SuppressWarnings("unchecked")
  @Test
  void completionListUsesTheEditorThemeColors() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Editor editor = new Editor();
          Color background = new Color(0x282A36);
          Color foreground = new Color(0xF8F8F2);
          Color selectionBackground = new Color(0x44475A);
          Color selectionForeground = new Color(0xF8F8F2);
          editor.setBackground(background);
          editor.setForeground(foreground);
          editor.setSelectionColor(selectionBackground);
          editor.setSelectedTextColor(selectionForeground);

          LspCompletionProvider provider = new LspCompletionProvider();
          provider.setItems(java.util.List.of(new CompletionItem("println")));
          var completion = provider.getCompletionByInputText("println").get(0);
          LspAutoCompletion autoCompletion = new LspAutoCompletion(editor, provider);
          JList<Object> list = new JList<>();

          Component row =
              autoCompletion
                  .getListCellRenderer()
                  .getListCellRendererComponent(list, completion, 0, true, false);

          assertThat(list.getBackground()).isEqualTo(background);
          assertThat(list.getForeground()).isEqualTo(foreground);
          assertThat(list.getSelectionBackground()).isEqualTo(selectionBackground);
          assertThat(list.getSelectionForeground()).isEqualTo(selectionForeground);
          assertThat(row.getBackground()).isEqualTo(selectionBackground);
          assertThat(row.getForeground()).isEqualTo(selectionForeground);

          Color updatedBackground = new Color(0xFBF1C7);
          editor.setBackground(updatedBackground);
          assertThat(autoCompletion.getDescWindowColor()).isEqualTo(updatedBackground);
        });
  }
}
