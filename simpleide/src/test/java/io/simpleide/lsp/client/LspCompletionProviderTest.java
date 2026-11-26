package io.simpleide.lsp.client;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import io.simpleide.ui.editor.Editor;

/**
 * Tests {@link io.simpleide.lsp.client.LspCompletionProvider}.
 */
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
	void keepsServerCompletionsAlongsideTheJavaSnippets() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			editor.setText("ma");
			editor.setCaretPosition(2);

			LspCompletionProvider provider = new LspCompletionProvider();
			java.util.List<CompletionItem> items = new java.util.ArrayList<>(
					JavaLspSnippets.completions(editor.getText(), editor.getCaretPosition()));
			for (String label : java.util.List.of("Math", "map", "marker")) {
				items.add(new CompletionItem(label));
			}
			provider.setItems(items);

			assertThat(provider.getCompletions(editor)).extracting(completion -> completion.getInputText())
				.containsExactly("main", "map", "marker", "Math");
		});
	}

	@Test
	void keepsServerCompletionsVisibleWhenSnippetsAreMerged() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			editor.setText("class Main {\n    void run() {\n        Str\n    }\n}\n");
			editor.setCaretPosition(editor.getText().indexOf("Str") + 3);

			LspCompletionProvider provider = new LspCompletionProvider();
			provider.setItems(LspEditorSupport.withSnippets("java", editor.getText(), editor.getCaretPosition(),
					java.util.List.of(new CompletionItem("String"), new CompletionItem("StrictMath"))));

			assertThat(provider.getCompletions(editor)).extracting(completion -> completion.getInputText())
				.containsExactlyInAnyOrder("String", "StrictMath");
		});
	}

	@Test
	void keepsEveryCompletionWhenLabelsRepeatWithDifferentDescriptions() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			editor.setText("Ma");
			editor.setCaretPosition(2);

			LspCompletionProvider provider = new LspCompletionProvider();
			java.util.List<CompletionItem> items = new java.util.ArrayList<>();
			for (String detail : java.util.List.of("Application entry point", "A", "java.lang", "")) {
				CompletionItem duplicate = new CompletionItem("Main");
				duplicate.setDetail(detail);
				items.add(duplicate);
			}
			items.add(new CompletionItem("Math"));
			provider.setItems(items);

			assertThat(provider.getCompletions(editor)).extracting(completion -> completion.getInputText())
				.containsExactly("Main", "Main", "Main", "Main", "Math");
		});
	}

	@Test
	void usesLspTextEditRangeWhenReplacingCompletionPrefix() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			editor.setText("prefix suf");
			editor.setCaretPosition(editor.getDocument().getLength());

			LspCompletionProvider provider = new LspCompletionProvider();
			CompletionItem item = new CompletionItem("suggestion");
			item.setTextEdit(Either.forLeft(new TextEdit(
					new Range(new Position(0, 0), new Position(0, editor.getCaretPosition())), "suggestion")));
			provider.setItems(java.util.List.of(item));

			var completion = provider.getCompletionByInputText("suggestion").get(0);

			assertThat(completion.getAlreadyEntered(editor)).isEqualTo("prefix suf");
		});
	}

}
