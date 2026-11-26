package io.simpleide.lsp.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

/**
 * Java snippets offered next to the completions returned by the language server.
 *
 * <p>
 * The items are plain completion entries: they appear in the normal completion popup, are
 * filtered by the text being typed and only insert their text when accepted. They are not
 * expanded automatically. The {@code scanner} item declares a {@link java.util.Scanner}
 * field when it is offered in a type body and a local variable inside a method, adding
 * the {@code java.util.Scanner} import through an additional text edit when it is
 * missing.
 *
 * <p>
 * Covered by {@code JavaLspSnippetsTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class JavaLspSnippets {

	private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(class|interface|enum|record)\\b");

	private static final String MAIN = "public static void main(String[] args) {}";

	private static final String SCANNER_IMPORT = "import java.util.Scanner;";

	private JavaLspSnippets() {
	}

	/**
	 * Returns whether snippets are offered for a language.
	 * @param languageId LSP language identifier
	 * @return {@code true} for {@code java}
	 */
	static boolean supports(String languageId) {
		return "java".equals(languageId);
	}

	/**
	 * Builds the snippet items for a caret position.
	 *
	 * <p>
	 * Items are omitted after member-access dots and when the document or caret is out of
	 * range.
	 * @param text document text
	 * @param caret caret offset in the document
	 * @return the snippet completion items, never {@code null}
	 */
	static List<CompletionItem> completions(String text, int caret) {
		if (text == null || caret < 0 || caret > text.length() || isMemberAccess(text, caret)) {
			return List.of();
		}

		List<CompletionItem> items = new ArrayList<>();
		items.add(completion("sout", "System.out.println()", "Print to standard output"));
		items.add(completion("main", MAIN, "Application entry point"));
		items.add(completion("psvm", MAIN, "Application entry point"));
		items.add(completion("fori", "for (int i = 0; i < 0; i++) {}", "Indexed for loop"));

		boolean staticField = isTypeBody(text, caret);
		CompletionItem scanner = completion("scanner",
				"final " + (staticField ? "static " : "") + "Scanner SC = new Scanner(System.in);",
				"Read from standard input");
		TextEdit importEdit = scannerImport(text);
		if (importEdit != null) {
			scanner.setAdditionalTextEdits(List.of(importEdit));
		}
		items.add(scanner);
		return items;
	}

	private static CompletionItem completion(String label, String insertText, String detail) {
		CompletionItem item = new CompletionItem(label);
		item.setKind(CompletionItemKind.Snippet);
		item.setDetail(detail);
		item.setInsertText(insertText);
		item.setInsertTextFormat(InsertTextFormat.PlainText);
		item.setFilterText(label);
		return item;
	}

	/**
	 * Checks whether the caret follows a member-access dot.
	 */
	private static boolean isMemberAccess(String text, int caret) {
		int start = caret;
		while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
			start--;
		}
		return start > 0 && text.charAt(start - 1) == '.';
	}

	/**
	 * Checks whether the caret is inside a type body rather than a method, constructor or
	 * initializer body.
	 */
	private static boolean isTypeBody(String text, int caret) {
		int depth = 0;
		int open = -1;
		for (int index = caret - 1; index >= 0; index--) {
			char character = text.charAt(index);
			if (character == '}') {
				depth++;
			}
			else if (character == '{') {
				if (depth == 0) {
					open = index;
					break;
				}
				depth--;
			}
		}
		if (open < 0) {
			return true;
		}

		int start = open - 1;
		while (start >= 0 && "{;}".indexOf(text.charAt(start)) < 0) {
			start--;
		}
		return TYPE_DECLARATION.matcher(text.substring(start + 1, open)).find();
	}

	/**
	 * Builds the import edit that makes {@code Scanner} resolve.
	 * @return the edit inserting the import after the package and import section, or
	 * {@code null} when the import is already present
	 */
	private static TextEdit scannerImport(String text) {
		String[] lines = text.split("\n", -1);
		int lastHeaderLine = -1;
		for (int index = 0; index < lines.length; index++) {
			String line = lines[index].strip();
			if (SCANNER_IMPORT.equals(line)) {
				return null;
			}
			if (line.startsWith("package ") || line.startsWith("import ")) {
				lastHeaderLine = index;
			}
		}

		int line = Math.min(lastHeaderLine + 1, lines.length - 1);
		return new TextEdit(new Range(new Position(line, 0), new Position(line, 0)), SCANNER_IMPORT + "\n");
	}

}
