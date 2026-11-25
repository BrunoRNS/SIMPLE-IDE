package io.simpleide.lsp.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.dowload.LspManager;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.editor.Editor;

/**
 * Checks the Java completion popup against a real Eclipse JDT LS response.
 *
 * <p>
 * The file is a loose {@code .java} file in a folder with no project, which is the case
 * where JDT LS has no build output. The test asserts that the server answers with real
 * completions, that the IDE's snippets join that answer and that the popup still shows
 * the server items, so the snippets never hide the language server.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("language-tools")
@Tag("lsp")
class JavaLspSnippetsPopupIntegrationTest {

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
	Path workspace;

	@Test
	void showsSnippetsAndServerCompletionsInThePopup() throws Exception {
		assumeTrue(javaAvailable(), "Java is required by JDT LS");
		Path file = Files.writeString(workspace.resolve("Main.java"), SOURCE);
		List<String> command = LspManager.getComandRun("java", workspace);
		assumeTrue(!command.isEmpty(), "No Java language server is registered for this platform");

		String uri = file.toUri().toString();
		try (LspClient client = new LspClient(command, workspace, null, java.util.Map.of(), null, java.util.Map.of())) {
			client.getInitialized().get(180, TimeUnit.SECONDS);
			client.didOpen(new TextDocumentItem(uri, "java", 1, SOURCE));

			CompletionParams params = new CompletionParams(new TextDocumentIdentifier(uri), new Position(2, 9));
			List<CompletionItem> serverItems = new ArrayList<>();
			await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
				Either<List<CompletionItem>, CompletionList> completion = client.completion(params)
					.get(30, TimeUnit.SECONDS);
				assertThat(completion).as("JDT completion response").isNotNull();
				List<CompletionItem> items = completion.isLeft() ? completion.getLeft()
						: completion.getRight().getItems();
				assertThat(items).as("JDT completion items").isNotNull().hasSizeGreaterThan(5);
				serverItems.clear();
				serverItems.addAll(items);
			});

			List<CompletionItem> merged = LspEditorSupport.withSnippets("java", SOURCE, SOURCE.indexOf("        p") + 9,
					serverItems);
			assertThat(merged).as("snippets joined to the server response")
				.extracting(CompletionItem::getLabel)
				.contains("psvm", "sout", "fori", "scanner");
			assertThat(merged).hasSize(serverItems.size() + 5);

			SwingUtilities.invokeAndWait(() -> {
				Editor editor = new Editor();
				editor.setText(SOURCE);
				editor.setCaretPosition(SOURCE.indexOf("        p") + 9);
				LspCompletionProvider provider = new LspCompletionProvider();
				provider.setItems(merged);

				List<String> shown = provider.getCompletions(editor)
					.stream()
					.map(completion -> completion.getInputText())
					.toList();
				assertThat(shown).as("popup entries").contains("psvm");
				assertThat(shown).as("server completions visible next to the snippets")
					.anyMatch(label -> !List.of("psvm", "main", "sout", "fori", "scanner").contains(label));
			});
		}
	}

	private static boolean javaAvailable() {
		try {
			Process process = new ProcessBuilder("java", "-version").redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
			return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
		}
		catch (java.io.IOException error) {
			return false;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
