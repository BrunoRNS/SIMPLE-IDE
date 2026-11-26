package io.simpleide.lsp.client;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.lsp4j.CompletionItem;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link LspEditorSupport} combines the IDE's Java snippets with the
 * completions returned by the language server.
 */
class LspEditorSupportSnippetsTest {

	private static final String MAIN = "class Main {\n    \n}\n";

	@Test
	void addsSnippetsToARealServerResponse() {
		CompletionItem serverItem = new CompletionItem("Math");

		List<CompletionItem> merged = LspEditorSupport.withSnippets("java", MAIN, MAIN.indexOf("\n    ") + 5,
				List.of(serverItem));

		assertThat(merged).extracting(CompletionItem::getLabel)
			.contains("psvm", "sout", "fori", "scanner", "main", "Math");
	}

	@Test
	void hidesSnippetsWhenTheServerReturnedNothing() {
		assertThat(LspEditorSupport.withSnippets("java", MAIN, MAIN.indexOf("\n    ") + 5, List.of())).isEmpty();
	}

	@Test
	void hidesSnippetsWhenTheServerResponseIsNull() {
		assertThat(LspEditorSupport.withSnippets("java", MAIN, MAIN.indexOf("\n    ") + 5, null)).isNull();
	}

	@Test
	void keepsOnlyTheServerItemsForOtherLanguages() {
		CompletionItem serverItem = new CompletionItem("println");

		assertThat(LspEditorSupport.withSnippets("kotlin", "fun main() {\n    \n}\n", 18, List.of(serverItem)))
			.containsExactly(serverItem);
	}

	@Test
	void hidesSnippetsAfterAMemberAccessDot() {
		String text = "class Main {\n    void run() {\n        text.\n    }\n}\n";
		CompletionItem serverItem = new CompletionItem("length");

		assertThat(LspEditorSupport.withSnippets("java", text, text.indexOf("text.") + 5, List.of(serverItem)))
			.containsExactly(serverItem);
	}

}
