package io.simpleide.lsp.client;

import io.simpleide.ui.editor.Editor;
import java.awt.Color;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.JList;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;

public class LspAutoCompletion extends AutoCompletion {
  private final Editor editor;
  private final PropertyChangeListener editorThemeListener;

  public LspAutoCompletion(Editor editor, LspCompletionProvider provider) {
    super(provider);
    this.editor = editor;
    this.editorThemeListener =
        event -> {
          if (event.getNewValue() instanceof Color background) {
            setDescriptionWindowColor(background);
          }
        };
    setListCellRenderer(new EditorThemeCompletionRenderer(editor));
    setDescriptionWindowColor(editor.getBackground());
    editor.addPropertyChangeListener("background", editorThemeListener);
  }

  @Override
  public void uninstall() {
    editor.removePropertyChangeListener("background", editorThemeListener);
    super.uninstall();
  }

  @Override
  protected void insertCompletion(Completion completion, boolean typedParamListStart) {
    if (!(completion instanceof LspCompletionProvider.LspCompletion lspCompletion)) {
      super.insertCompletion(completion, typedParamListStart);
      return;
    }

    int primaryEnd = editor.getCaretPosition();
    String alreadyEntered = completion.getAlreadyEntered(editor);
    if (alreadyEntered == null) {
      alreadyEntered = "";
    }
    int primaryStart = primaryEnd - alreadyEntered.length();
    int originalLength = editor.getDocument().getLength();
    List<LspCompletionEditApplier.ResolvedTextEdit> additionalEdits =
        LspCompletionEditApplier.captureAdditionalEdits(
            editor, lspCompletion.getItem(), primaryStart, primaryEnd);

    super.insertCompletion(completion, typedParamListStart);

    int primaryDelta = editor.getDocument().getLength() - originalLength;
    LspCompletionEditApplier.applyAdditionalEdits(
        editor, additionalEdits, primaryStart, primaryEnd, primaryDelta);
  }

  private static final class EditorThemeCompletionRenderer extends CompletionCellRenderer {
    private final Editor editor;

    EditorThemeCompletionRenderer(Editor editor) {
      this.editor = editor;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Component getListCellRendererComponent(
        JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
      Color background = editor.getBackground();
      Color foreground = editor.getForeground();
      Color selectionBackground = editor.getSelectionColor();
      Color selectionForeground = editor.getSelectedTextColor();
      list.setBackground(background);
      list.setForeground(foreground);
      list.setSelectionBackground(selectionBackground);
      list.setSelectionForeground(selectionForeground);

      Component component =
          super.getListCellRendererComponent(list, value, index, selected, cellHasFocus);
      component.setBackground(selected ? selectionBackground : background);
      component.setForeground(selected ? selectionForeground : foreground);
      return component;
    }
  }
}
