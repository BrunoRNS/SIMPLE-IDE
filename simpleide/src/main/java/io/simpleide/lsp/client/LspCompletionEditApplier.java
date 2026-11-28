package io.simpleide.lsp.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures and applies the additional text edits attached to an LSP completion item.
 *
 * <p>
 * Completion items may request edits outside the text inserted at the caret. This utility
 * validates those edits against the document, rejects overlapping or invalid ranges and
 * applies them after the primary insertion while accounting for the text length change.
 * Invalid edits are logged and ignored so completion still succeeds.
 *
 * <p>
 * Covered by {@code LspCompletionEditApplierTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class LspCompletionEditApplier {

	private static final Logger LOGGER = LoggerFactory.getLogger(LspCompletionEditApplier.class);

	private LspCompletionEditApplier() {
	}

	/**
	 * Resolves the additional text edits of a completion item to document offsets.
	 *
	 * <p>
	 * The returned edits are sorted from the end of the document toward the start so
	 * applying them does not invalidate the offsets of the remaining edits. Edits that
	 * are incomplete, overlap the primary completion range or overlap each other are
	 * discarded.
	 * @param editor editor whose document receives the completion
	 * @param item completion item that may carry additional edits
	 * @param primaryStart start offset of the text replaced by the completion
	 * @param primaryEnd end offset of the text replaced by the completion
	 * @return the resolved edits, or an empty list when none are valid
	 */
	@SuppressWarnings("")
	public static List<ResolvedTextEdit> captureAdditionalEdits(RSyntaxTextArea editor, CompletionItem item,
			int primaryStart, int primaryEnd) {
		List<TextEdit> additionalEdits = item.getAdditionalTextEdits();
		if (additionalEdits == null || additionalEdits.isEmpty()) {
			return List.of();
		}

		List<ResolvedTextEdit> resolved = new ArrayList<>();
		try {
			for (TextEdit edit : additionalEdits) {
				if (edit == null || edit.getRange() == null || edit.getNewText() == null) {
					return invalidEdits(item, "an edit is incomplete");
				}
				int start = toOffset(editor, edit.getRange().getStart());
				int end = toOffset(editor, edit.getRange().getEnd());
				if (start > end || overlaps(start, end, primaryStart, primaryEnd)) {
					return invalidEdits(item, "an edit overlaps the completion");
				}
				resolved.add(new ResolvedTextEdit(start, end, edit.getNewText()));
			}
		}
		catch (BadLocationException error) {
			LOGGER.warn("Ignoring invalid LSP additional edits for {}.", item.getLabel(), error);
			return List.of();
		}

		resolved.sort(Comparator.comparingInt(ResolvedTextEdit::start));
		for (int index = 1; index < resolved.size(); index++) {
			ResolvedTextEdit previous = resolved.get(index - 1);
			ResolvedTextEdit current = resolved.get(index);
			if (overlaps(previous.start(), previous.end(), current.start(), current.end())) {
				return invalidEdits(item, "additional edits overlap each other");
			}
		}
		resolved.sort(Comparator.comparingInt(ResolvedTextEdit::start)
			.reversed()
			.thenComparing(Comparator.comparingInt(ResolvedTextEdit::end).reversed()));
		return List.copyOf(resolved);
	}

	/**
	 * Applies previously captured additional edits to the editor document.
	 *
	 * <p>
	 * Offsets at or after the primary completion range are shifted by the length change
	 * caused by the primary insertion. A failed edit is logged and stops the remaining
	 * edits.
	 * @param editor editor whose document is modified
	 * @param edits resolved edits in descending offset order
	 * @param primaryStart start offset of the primary completion range
	 * @param primaryEnd end offset of the primary completion range
	 * @param primaryDelta length change produced by the primary insertion
	 */
	public static void applyAdditionalEdits(RSyntaxTextArea editor, List<ResolvedTextEdit> edits, int primaryStart,
			int primaryEnd, int primaryDelta) {
		Document document = editor.getDocument();
		for (ResolvedTextEdit edit : edits) {
			int start = adjustOffset(edit.start(), primaryStart, primaryEnd, primaryDelta);
			int end = adjustOffset(edit.end(), primaryStart, primaryEnd, primaryDelta);
			try {
				if (document instanceof AbstractDocument abstractDocument) {
					abstractDocument.replace(start, end - start, edit.newText(), null);
				}
				else {
					if (document != null) {
						document.remove(start, end - start);
						document.insertString(start, edit.newText(), null);
					}
					else {
						LOGGER.warn("[LSP] Failed to remove/insertString in document, document is NULL");
					}
				}
			}
			catch (BadLocationException error) {
				LOGGER.error("Unable to apply LSP additional completion edit.", error);
				return;
			}
		}
	}

	private static int toOffset(RSyntaxTextArea editor, Position position) throws BadLocationException {
		if (position == null || position.getLine() < 0 || position.getLine() >= editor.getLineCount()
				|| position.getCharacter() < 0) {
			throw new BadLocationException("LSP position is outside the document.", -1);
		}

		int lineStart = editor.getLineStartOffset(position.getLine());
		int lineEnd = editor.getLineEndOffset(position.getLine());
		String lineText = editor.getText(lineStart, lineEnd - lineStart);
		int contentLength = lineText.length();
		while (contentLength > 0
				&& (lineText.charAt(contentLength - 1) == '\n' || lineText.charAt(contentLength - 1) == '\r')) {
			contentLength--;
		}
		if (position.getCharacter() > contentLength) {
			throw new BadLocationException("LSP character is outside the line.", lineStart + position.getCharacter());
		}
		return lineStart + position.getCharacter();
	}

	private static int adjustOffset(int offset, int primaryStart, int primaryEnd, int primaryDelta) {
		if (offset <= primaryStart) {
			return offset;
		}
		return offset >= primaryEnd ? offset + primaryDelta : offset;
	}

	private static boolean overlaps(int start, int end, int otherStart, int otherEnd) {
		if (start == end && otherStart == otherEnd) {
			return start == otherStart;
		}
		if (start == end) {
			return start > otherStart && start < otherEnd;
		}
		if (otherStart == otherEnd) {
			return otherStart > start && otherStart < end;
		}
		return start < otherEnd && end > otherStart;
	}

	private static List<ResolvedTextEdit> invalidEdits(CompletionItem item, String reason) {
		LOGGER.warn("Ignoring LSP additional edits for {}: {}.", item.getLabel(), reason);
		return List.of();
	}

	/**
	 * Additional edit resolved to absolute document offsets.
	 *
	 * @param start start offset in the document
	 * @param end end offset in the document
	 * @param newText replacement text
	 * @author LuisOtavio13
	 * @since v1.0.0beta
	 */
	public record ResolvedTextEdit(int start, int end, String newText) {
	}

}
