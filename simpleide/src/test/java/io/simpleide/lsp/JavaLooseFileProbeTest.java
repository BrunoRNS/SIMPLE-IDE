package io.simpleide.lsp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import static org.awaitility.Awaitility.await;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.dap.JavaDebugBridge;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.client.LspCompletionProvider;
import io.simpleide.lsp.dowload.LspManager;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.editor.Editor;

@Tag("debug")
class JavaLooseFileProbeTest {

	private static final String SOURCE = """
			public class Main {
			    public static void main(String[] args) {
			        p
			    }
			}
			""";

	@BeforeAll
	@SuppressWarnings("unused")
	static void initializeThreadManager() {
		ThreadMaster.getInstance().initialize();
	}

	@TempDir
	Path tempDirectory;

	@Test
	void probesLooseFileSupport() throws Exception {
		Path program = Files.writeString(tempDirectory.resolve("Main.java"), SOURCE);
		List<String> diagnostics = new CopyOnWriteArrayList<>();
		List<String> command = LspManager.getComandRun("java", tempDirectory);
		LspClient client = new LspClient(command, tempDirectory, (params) -> record(params, diagnostics), Map.of(),
				null, Map.of());
		try {
			client.getInitialized().get(180, TimeUnit.SECONDS);
			System.out.println("PROBE initialized");
			String uri = program.toUri().toString();
			client.didOpen(new TextDocumentItem(uri, "java", 1, SOURCE));

			CompletionParams params = new CompletionParams(new TextDocumentIdentifier(uri), new Position(2, 9));
			Either<List<CompletionItem>, CompletionList> completion = client.completion(params)
				.get(60, TimeUnit.SECONDS);
			List<CompletionItem> items = completion == null ? null
					: completion.isLeft() ? completion.getLeft() : completion.getRight().getItems();
			System.out.println("PROBE completion count=" + (items == null ? "null" : items.size()));
			if (items != null) {
				items.stream()
					.limit(12)
					.forEach(item -> System.out
						.println("PROBE item=" + item.getLabel() + " filter=" + item.getFilterText() + " edit="
								+ item.getTextEdit() + " insert=" + item.getInsertText()));
				List<CompletionItem> withSnippets = new ArrayList<>(JavaLspSnippetsAccess.completions(SOURCE, 9));
				withSnippets.addAll(items);
				SwingUtilities.invokeAndWait(() -> {
					Editor editor = new Editor();
					editor.setText(SOURCE);
					editor.setCaretPosition(SOURCE.indexOf("        p") + 9);
					LspCompletionProvider provider = new LspCompletionProvider();
					provider.setItems(withSnippets);
					List<String> shown = provider.getCompletions(editor)
						.stream()
						.map(completion_ -> completion_.getInputText())
						.toList();
					System.out.println("PROBE popup count=" + shown.size() + " shown=" + shown);
				});
			}

			Object main = client.executeCommand(JavaDebugBridge.RESOLVE_MAIN_CLASS, List.of(uri))
				.get(60, TimeUnit.SECONDS);
			System.out.println("PROBE resolveMainClass=" + main);
		}
		finally {
			client.close();
		}
	}

	private static void record(PublishDiagnosticsParams params, List<String> diagnostics) {
		params.getDiagnostics()
			.forEach(diagnostic -> diagnostics.add(diagnostic.getSeverity() + ":" + diagnostic.getMessage()));
	}

	/**
	 * Reaches the package-private snippet helper from the LSP test package.
	 */
	private static final class JavaLspSnippetsAccess {

		private static List<CompletionItem> completions(String text, int caret) throws Exception {
			Class<?> type = Class.forName("io.simpleide.lsp.client.JavaLspSnippets");
			var method = type.getDeclaredMethod("completions", String.class, int.class);
			method.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<CompletionItem> result = (List<CompletionItem>) method.invoke(null, text, caret);
			return result;
		}

	}

}
