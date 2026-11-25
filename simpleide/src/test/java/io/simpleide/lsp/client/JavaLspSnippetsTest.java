package io.simpleide.lsp.client;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link JavaLspSnippets}.
 */
class JavaLspSnippetsTest {

	@Test
	void offersTheJavaSnippetsAsCompletionItems() {
		List<CompletionItem> items = JavaLspSnippets.completions("class Main {}\n", 0);

		assertThat(items).extracting(CompletionItem::getLabel)
			.containsExactly("sout", "main", "psvm", "fori", "scanner");
		assertThat(item(items, "sout").getInsertText()).isEqualTo("System.out.println()");
		assertThat(item(items, "main").getInsertText()).isEqualTo("public static void main(String[] args) {}");
		assertThat(item(items, "psvm").getInsertText()).isEqualTo("public static void main(String[] args) {}");
		assertThat(item(items, "fori").getInsertText()).isEqualTo("for (int i = 0; i < 0; i++) {}");
	}

	@Test
	void declaresTheScannerFieldAtTypeLevel() {
		String text = "class Main {\n    \n}\n";
		CompletionItem scanner = item(JavaLspSnippets.completions(text, text.indexOf("\n    ")), "scanner");

		assertThat(scanner.getInsertText()).isEqualTo("final static Scanner SC = new Scanner(System.in);");
	}

	@Test
	void declaresTheScannerVariableInsideAMethod() {
		String text = "class Main {\n    void run() {\n        \n    }\n}\n";
		CompletionItem scanner = item(JavaLspSnippets.completions(text, text.indexOf("\n        ")), "scanner");

		assertThat(scanner.getInsertText()).isEqualTo("final Scanner SC = new Scanner(System.in);");
	}

	@Test
	void importsScannerAfterTheExistingImportSection() {
		String text = "package demo;\n\nimport java.util.List;\n\nclass Main {}\n";
		CompletionItem scanner = item(JavaLspSnippets.completions(text, 0), "scanner");

		TextEdit edit = scanner.getAdditionalTextEdits().get(0);
		assertThat(edit.getNewText()).isEqualTo("import java.util.Scanner;\n");
		assertThat(edit.getRange().getStart()).isEqualTo(new Position(3, 0));
	}

	@Test
	void addsTheScannerImportAtTheTopWhenThereIsNoHeader() {
		CompletionItem scanner = item(JavaLspSnippets.completions("class Main {}\n", 0), "scanner");

		TextEdit edit = scanner.getAdditionalTextEdits().get(0);
		assertThat(edit.getRange().getStart()).isEqualTo(new Position(0, 0));
	}

	@Test
	void doesNotRepeatAnExistingScannerImport() {
		String text = "import java.util.Scanner;\n\nclass Main {}\n";
		CompletionItem scanner = item(JavaLspSnippets.completions(text, 0), "scanner");

		assertThat(scanner.getAdditionalTextEdits()).isNull();
	}

	@Test
	void offersNoSnippetsAfterAMemberAccessDot() {
		assertThat(JavaLspSnippets.completions("class Main {\n    void run() {\n        text.\n    }\n}\n",
				"class Main {\n    void run() {\n        text.".length()))
			.isEmpty();
	}

	@Test
	void onlySupportsJava() {
		assertThat(JavaLspSnippets.supports("java")).isTrue();
		assertThat(JavaLspSnippets.supports("kotlin")).isFalse();
	}

	private static CompletionItem item(List<CompletionItem> items, String label) {
		return items.stream().filter(item -> label.equals(item.getLabel())).findFirst().orElseThrow();
	}

}
