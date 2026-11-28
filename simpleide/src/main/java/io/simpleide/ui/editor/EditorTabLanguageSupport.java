package io.simpleide.ui.editor;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.text.BadLocationException;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.rsta.ac.LanguageSupport;
import org.fife.rsta.ac.LanguageSupportFactory;
import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.client.LspEditorSupport;

/**
 * Connects an editor tab to a language server or to offline language support.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class EditorTabLanguageSupport {

	private static final Logger LOGGER = LoggerFactory.getLogger(EditorTabLanguageSupport.class);

	private static final AtomicBoolean JAVA_JRE_CLASS_SOURCE_REGISTERED = new AtomicBoolean();

	private final EditorTab tab;

	private LspEditorSupport lspSupport;

	private volatile LspClient languageServer;

	private boolean localLanguageSupportRegistered;

	private AutoCompletion localAutoCompletion;

	private CompletionProvider localCompletionProvider;

	private Consumer<EditorTab> diagnosticsListener = editorTab -> {
	};

	EditorTabLanguageSupport(EditorTab tab) {
		this.tab = tab;
	}

	/**
	 * Connects a language server and replaces local completion with LSP support.
	 * @param client connected language server client
	 */
	void connectLanguageServer(LspClient client) {
		if (tab.isClosed()) {
			client.didClose(tab.getDocument().getUri());
			return;
		}
		unregisterLocalLanguageSupport();
		languageServer = client;
		lspSupport = new LspEditorSupport(tab.getEditor(), tab.getDocument(), client);
	}

	LspClient getLanguageServer() {
		return languageServer;
	}

	/**
	 * Enables offline language support and keyword completion.
	 *
	 * <p>
	 * When a local RSyntaxTextArea language support is available it is registered;
	 * otherwise a keyword completion provider is installed. Calling the method again is a
	 * no-op when support is already active.
	 */
	void enableLocalLanguageSupport() {
		if (tab.isClosed() || tab.getEditor() == null || lspSupport != null || hasLocalLanguageSupport()) {
			return;
		}
		if (!GraphicsEnvironment.isHeadless()) {
			String syntaxStyle = tab.getEditor().getSyntaxEditingStyle();
			if (LanguageSupportFactory.get().getSupportFor(syntaxStyle) != null) {
				try {
					if ("text/java".equals(syntaxStyle)) {
						ensureJreClassesAvailable();
					}
					LanguageSupportFactory.get().register(tab.getEditor());
					localLanguageSupportRegistered = true;
					return;
				}
				catch (RuntimeException error) {
					LOGGER.warn("Unable to register local language support for {}; using keyword completion.",
							tab.getFile().getPath(), error);
				}
			}
		}
		enableKeywordCompletion();
	}

	/**
	 * RSTA's Java completion provider only knows about the classpath it is explicitly
	 * given. Without the current JRE it cannot even resolve {@code java.lang.Object},
	 * which produces the "Couldn't find ClassFile for: java.lang.Object" warnings and
	 * leaves completion empty.
	 */
	private static void ensureJreClassesAvailable() {
		if (!JAVA_JRE_CLASS_SOURCE_REGISTERED.compareAndSet(false, true)) {
			return;
		}
		LanguageSupport support = LanguageSupportFactory.get().getSupportFor("text/java");
		if (!(support instanceof JavaLanguageSupport javaSupport)) {
			return;
		}
		try {
			javaSupport.getJarManager().addCurrentJreClassFileSource();
		}
		catch (IOException | RuntimeException error) {
			LOGGER.warn("Unable to add the current JRE to local Java completion.", error);
		}
	}

	boolean hasLocalLanguageSupport() {
		return localLanguageSupportRegistered || localAutoCompletion != null;
	}

	CompletionProvider getLocalCompletionProvider() {
		return localCompletionProvider;
	}

	CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definitionAtCaret() {
		if (lspSupport == null) {
			return CompletableFuture.failedFuture(new IllegalStateException("No language server is connected."));
		}
		return lspSupport.definitionAtCaret();
	}

	void goToRange(Range range) {
		if (range == null || range.getStart() == null || tab.getEditor() == null) {
			return;
		}

		try {
			int start = tab.toOffset(range.getStart());
			int end = range.getEnd() == null ? start : tab.toOffset(range.getEnd());
			tab.getEditor().setCaretPosition(start);
			tab.getEditor().moveCaretPosition(Math.max(start, end));
			tab.getEditor().requestFocusInWindow();
		}
		catch (BadLocationException error) {
			LOGGER.warn("Unable to navigate to a location in {}.", tab.getFile().getPath(), error);
		}
	}

	void publishDiagnostics(PublishDiagnosticsParams params) {
		if (lspSupport != null) {
			lspSupport.publishDiagnostics(params);
			diagnosticsListener.accept(tab);
		}
	}

	List<Diagnostic> getDiagnostics() {
		return lspSupport == null ? List.of() : lspSupport.getDiagnostics();
	}

	void setDiagnosticsListener(Consumer<EditorTab> listener) {
		diagnosticsListener = listener == null ? editorTab -> {
		} : listener;
	}

	void notifyDiagnostics() {
		diagnosticsListener.accept(tab);
	}

	void documentSaved() {
		if (lspSupport != null) {
			lspSupport.documentSaved();
		}
	}

	void documentChanged() {
		if (lspSupport != null) {
			lspSupport.documentChanged();
		}
	}

	void close() {
		if (lspSupport != null) {
			lspSupport.close();
		}
		languageServer = null;
		unregisterLocalLanguageSupport();
	}

	private void enableKeywordCompletion() {
		CompletionProvider provider = LocalCompletionProviders.forSyntaxStyle(tab.getEditor().getSyntaxEditingStyle());
		if (provider == null) {
			return;
		}
		AutoCompletion autoCompletion = new io.simpleide.lsp.client.LspAutoCompletion(tab.getEditor(), provider);
		autoCompletion.setAutoActivationEnabled(true);
		autoCompletion.setAutoActivationDelay(300);
		autoCompletion.install(tab.getEditor());
		localAutoCompletion = autoCompletion;
		localCompletionProvider = provider;
	}

	private void unregisterLocalLanguageSupport() {
		if (localAutoCompletion != null) {
			localAutoCompletion.uninstall();
			localAutoCompletion = null;
			localCompletionProvider = null;
		}
		if (localLanguageSupportRegistered) {
			try {
				LanguageSupportFactory.get().unregister(tab.getEditor());
			}
			catch (RuntimeException error) {
				LOGGER.warn("Unable to unregister local language support for {}.", tab.getFile().getPath(), error);
			}
			localLanguageSupportRegistered = false;
		}
	}

}
