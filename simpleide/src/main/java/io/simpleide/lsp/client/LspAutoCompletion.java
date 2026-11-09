package io.simpleide.lsp.client;

import java.util.List;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;

import io.simpleide.ui.editor.Editor;

class LspAutoCompletion extends AutoCompletion {
  private final Editor editor;

  LspAutoCompletion(Editor editor, LspCompletionProvider provider) {
    super(provider);
    this.editor = editor;
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
}