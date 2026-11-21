package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.rsta.ac.LanguageSupport;
import org.fife.rsta.ac.LanguageSupportFactory;
import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.client.LspEditorSupport;
import io.simpleide.lsp.core.Document;
import io.simpleide.thread.nodes.edt.listeners.DocumentNode;
import io.simpleide.ui.treefile.File;
import io.simpleide.utils.service.FileService;

/**
 * Editor tab combining an {@link Editor}, a search panel and save tracking.
 *
 * <p>
 * The tab loads a file into the editor, enables line numbers, marks itself edited when
 * the document changes, saves through {@link FileService}, and switches between local
 * completion and a connected language server. Closing the tab releases local support, the
 * LSP binding and the document listener.
 *
 * <p>
 * Covered by {@code EditorTabTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/editor.md">docs/api/editor.md</a>
 */
public class EditorTab extends JPanel {

	private static final Logger LOGGER = LoggerFactory.getLogger(EditorTab.class);

	private static final AtomicBoolean JAVA_JRE_CLASS_SOURCE_REGISTERED = new AtomicBoolean();

	private final Editor editor;

	private final Document document;

	private final EditorSearchPanel searchPanel;

	private final FileService fileService = new FileService();

	private LspEditorSupport lspSupport;

	private boolean localLanguageSupportRegistered;

	private AutoCompletion localAutoCompletion;

	private CompletionProvider localCompletionProvider;

	private boolean closed;

	private final ButtonExit buttonExit;

	private final File file;

	private final RTextScrollPane scrollPane;

	private final String name;

	private boolean isEdit;

	private DocumentNode documentNode;

	/**
	 * Creates a tab for a file and its LSP document.
	 * @param file file shown in the tab
	 * @param document document metadata tracked by the language server manager
	 */
	public EditorTab(File file, Document document) {
		this.editor = new Editor();
		this.searchPanel = new EditorSearchPanel(editor);
		this.isEdit = false;
		this.file = file;
		this.document = document;
		this.name = file.getName();
		this.scrollPane = new RTextScrollPane(editor);
		this.buttonExit = new ButtonExit(this);
		setLayout(new BorderLayout());
		add(searchPanel, BorderLayout.NORTH);
		add(this.scrollPane, BorderLayout.CENTER);
		editor.loadFile(file);
		configIsEdit();
	}

	/**
	 * Returns the tab display name, which is the file name.
	 * @return the tab name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Returns the LSP document for this tab.
	 * @return the document metadata
	 */
	public Document getDocument() {
		return document;
	}

	/**
	 * Returns the close button shown in the tab header.
	 * @return the close button
	 */
	public ButtonExit getButtonExit() {
		return this.buttonExit;
	}

	/**
	 * Returns the file displayed by this tab.
	 * @return the source file
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Returns the text area of this tab.
	 * @return the editor component
	 */
	public Editor getEditor() {
		return editor;
	}

	void showSearchPanel(boolean showReplacement) {
		searchPanel.open(showReplacement);
	}

	/**
	 * Connects a language server and replaces local completion with LSP support.
	 * @param client connected language server client
	 */
	public void connectLanguageServer(LspClient client) {
		if (closed) {
			client.didClose(document.getUri());
			return;
		}
		unregisterLocalLanguageSupport();
		lspSupport = new LspEditorSupport(editor, document, client);
	}

	/**
	 * Enables offline language support and keyword completion.
	 *
	 * <p>
	 * When a local RSyntaxTextArea language support is available it is registered;
	 * otherwise a keyword completion provider is installed. Calling the method again is a
	 * no-op when support is already active.
	 */
	public void enableLocalLanguageSupport() {
		if (closed || lspSupport != null || hasLocalLanguageSupport()) {
			return;
		}
		if (!GraphicsEnvironment.isHeadless()) {
			String syntaxStyle = editor.getSyntaxEditingStyle();
			if (LanguageSupportFactory.get().getSupportFor(syntaxStyle) != null) {
				try {
					if ("text/java".equals(syntaxStyle)) {
						ensureJreClassesAvailable();
					}
					LanguageSupportFactory.get().register(editor);
					localLanguageSupportRegistered = true;
					return;
				}
				catch (RuntimeException error) {
					LOGGER.warn("Unable to register local language support for {}; using keyword completion.",
							file.getPath(), error);
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

	/**
	 * Asks the connected language server for the definition at the caret.
	 * @return the definition locations, or a failed future when no server is connected
	 */
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definitionAtCaret() {
		if (lspSupport == null) {
			return CompletableFuture.failedFuture(new IllegalStateException("No language server is connected."));
		}
		return lspSupport.definitionAtCaret();
	}

	@SuppressWarnings("")
	/**
	 * Selects an LSP range in the editor.
	 * @param range range to select, ignored when {@code null}
	 */
	public void goToRange(Range range) {
		if (range == null || range.getStart() == null) {
			return;
		}

		try {
			int start = toOffset(range.getStart());
			int end = range.getEnd() == null ? start : toOffset(range.getEnd());
			editor.setCaretPosition(start);
			editor.moveCaretPosition(Math.max(start, end));
			editor.requestFocusInWindow();
		}
		catch (BadLocationException error) {
			LOGGER.warn("Unable to navigate to a location in {}.", file.getPath(), error);
		}
	}

	/**
	 * Forwards diagnostics for this document to the LSP editor support.
	 * @param params diagnostics published by the server
	 */
	public void publishDiagnostics(PublishDiagnosticsParams params) {
		if (lspSupport != null) {
			lspSupport.publishDiagnostics(params);
		}
	}

	/**
	 * Returns the icon associated with the file type.
	 * @return the type icon, or {@code null} when the type has no icon
	 */
	public Icon getFileIcon() {
		return io.simpleide.ui.icon.MaterialIcons.forFile(file.getPath());
	}

	/**
	 * Returns whether the tab has unsaved changes.
	 * @return {@code true} while the document is modified
	 */
	public boolean isEdit() {
		return isEdit;
	}

	/**
	 * Saves the editor content to the tab's file.
	 *
	 * <p>
	 * On failure an error popup is shown and the exception is rethrown so the caller can
	 * keep the tab marked as modified.
	 * @throws IOException when the file cannot be written
	 */
	public void save() throws IOException {
		String content = editor.getText();
		try {
			fileService.saveFile(file.getPath(), content);
		}
		catch (IOException error) {
			LOGGER.error("Unable to save file {}.", file.getPath(), error);
			try {
				new ErrorPopUP("Unable to save " + file.getName() + ": " + error.getMessage(), this,
						ErrorTypePopUp.INTERNAL_ERROR)
					.startManaged();
			}
			catch (RuntimeException popupError) {
				error.addSuppressed(popupError);
				LOGGER.warn("Unable to display the save error popup for {}.", file.getPath(), popupError);
			}
			throw error;
		}
		file.getCode().replace(0, file.getCode().length(), content);
		this.buttonExit.setNotEdited();
		if (lspSupport != null) {
			lspSupport.documentSaved();
		}
		isEdit = false;
		document.setModified(false);
	}

	/**
	 * Releases the language support, document listener and document state. Calling the
	 * method more than once is a no-op.
	 */
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		if (lspSupport != null) {
			lspSupport.close();
		}
		unregisterLocalLanguageSupport();
		if (documentNode != null) {
			documentNode.close();
			editor.getDocument().removeDocumentListener(documentNode);
		}
		document.setOpened(false);
	}

	private void enableKeywordCompletion() {
		CompletionProvider provider = LocalCompletionProviders.forSyntaxStyle(editor.getSyntaxEditingStyle());
		if (provider == null) {
			return;
		}
		AutoCompletion autoCompletion = new io.simpleide.lsp.client.LspAutoCompletion(editor, provider);
		autoCompletion.setAutoActivationEnabled(true);
		autoCompletion.setAutoActivationDelay(300);
		autoCompletion.install(editor);
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
				LanguageSupportFactory.get().unregister(editor);
			}
			catch (RuntimeException error) {
				LOGGER.warn("Unable to unregister local language support for {}.", file.getPath(), error);
			}
			localLanguageSupportRegistered = false;
		}
	}

	private void configIsEdit() {
		documentNode = new DocumentNode(this::onDocumentChanged);
		editor.getDocument().addDocumentListener(documentNode);
		scrollPane.setLineNumbersEnabled(true);
	}

	private void onDocumentChanged() {
		if (closed) {
			return;
		}
		if (java.util.Objects.equals(file.getCode().toString(), editor.getText())) {
			this.isEdit = false;
			document.setModified(false);
			this.buttonExit.setNotEdited();
			return;
		}
		this.isEdit = true;
		document.setModified(true);
		document.incrementVersion();
		this.buttonExit.setEdited();
		if (lspSupport != null) {
			lspSupport.documentChanged();
		}
	}

	private int toOffset(Position position) throws BadLocationException {
		if (position.getLine() < 0 || position.getLine() >= editor.getLineCount() || position.getCharacter() < 0) {
			throw new BadLocationException("LSP position is outside the document.", -1);
		}
		int lineStart = editor.getLineStartOffset(position.getLine());
		int offset = lineStart + position.getCharacter();
		int lineEnd = Math.min(editor.getLineEndOffset(position.getLine()), editor.getDocument().getLength());
		if (offset > lineEnd) {
			throw new BadLocationException("LSP character is outside the line.", offset);
		}
		return offset;
	}

}
