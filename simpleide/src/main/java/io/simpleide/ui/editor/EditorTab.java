/**
 * File tab showing editable text, an image viewer, or a binary hex editor with shared
 * save tracking.
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
 * @see <a href="https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/editor.md">docs/api/editor.md</a>
 */

package io.simpleide.ui.editor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.ui.autocomplete.CompletionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.api.HexEditor;
import io.simpleide.api.ImageViewer;
import io.simpleide.api.LargeTextViewer;
import io.simpleide.api.MediaPlayback;
import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.thread.nodes.edt.listeners.DocumentNode;
import io.simpleide.ui.treefile.SimpleIDEFile;
import io.simpleide.utils.service.FileService;

/**
 * File tab showing editable text, an image viewer, or a binary hex editor with shared
 * save tracking.
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
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/editor.md">docs/api/editor.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class EditorTab extends JPanel {

	private static final Logger LOGGER = LoggerFactory.getLogger(EditorTab.class);

	private final Document document;

	private final FileService fileService = new FileService();

	private boolean closed;

	private final ButtonExit buttonExit;

	private final SimpleIDEFile file;

	private final String name;

	private boolean isEdit;

	private DocumentNode documentNode;

	private final EditorTabContent content;

	private final EditorTabLanguageSupport languageSupport;

	/**
	 * Creates a tab for a file and its document metadata.
	 * @param file file shown in the tab
	 * @param document document metadata tracked by the language server manager
	 */
	public EditorTab(SimpleIDEFile file, Document document) {
		this.isEdit = false;
		this.file = file;
		this.document = document;
		this.name = file.getName();
		this.buttonExit = new ButtonExit(this);
		this.content = new EditorTabContent(this);
		this.languageSupport = new EditorTabLanguageSupport(this);
		setLayout(new java.awt.BorderLayout());
		if (file.isContentLoaded()) {
			showLoadedContent();
		}
		else {
			content.showLoading();
		}
	}

	/** Returns whether this tab is waiting for its file content to load. */
	public boolean isLoading() {
		return content.isLoading();
	}

	/** Returns whether this tab has already been closed. */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Creates the read-only viewer or editable text component after the file is loaded.
	 */
	public final void showLoadedContent() {
		content.showLoadedContent();
	}

	void showLoadError(Throwable error) {
		content.showLoadError(error);
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
	public SimpleIDEFile getFile() {
		return file;
	}

	/**
	 * Returns the text area of this tab.
	 * @return the editor component, or {@code null} while loading and for image/binary
	 * tabs
	 */
	public Editor getEditor() {
		return content.getEditor();
	}

	/**
	 * Returns the one-based line numbers with a breakpoint in this tab.
	 * @return an immutable snapshot of the breakpoint lines, or an empty list for
	 * non-text tabs
	 */
	public List<Integer> getBreakpoints() {
		Editor editor = content.getEditor();
		return editor == null ? List.of() : editor.getBreakpointLines();
	}

	/**
	 * Toggles a breakpoint on a one-based line of this tab.
	 * @param line one-based line number
	 * @return {@code true} when a breakpoint now exists on the line
	 */
	public boolean toggleBreakpoint(int line) {
		Editor editor = content.getEditor();
		return editor != null && editor.toggleBreakpoint(line);
	}

	/**
	 * Removes every breakpoint from this tab.
	 */
	public void clearBreakpoints() {
		Editor editor = content.getEditor();
		if (editor != null) {
			editor.clearBreakpoints();
		}
	}

	/**
	 * Returns the image viewer when this tab displays an image, otherwise {@code null}.
	 */
	public ImageViewer getImageViewer() {
		return content.getImageViewer();
	}

	/**
	 * Returns the hex editor when this tab displays binary data, otherwise {@code null}.
	 */
	public HexEditor getHexEditor() {
		return content.getHexEditor();
	}

	/** Returns the paged, read-only view for large text files, otherwise {@code null}. */
	public LargeTextViewer getLargeTextViewer() {
		return content.getLargeTextViewer();
	}

	/**
	 * Returns the audio player for this tab, or {@code null} for other content kinds.
	 * @return the audio player, or {@code null} when the tab does not play audio
	 */
	public MediaPlayback getAudioViewer() {
		return content.getAudioViewer();
	}

	/**
	 * Returns the video player for this tab, or {@code null} for other content kinds.
	 * @return the video player, or {@code null} when the tab does not play video
	 */
	public MediaPlayback getVideoViewer() {
		return content.getVideoViewer();
	}

	void showSearchPanel(boolean showReplacement) {
		content.showSearchPanel(showReplacement);
	}

	/**
	 * Connects a language server and replaces local completion with LSP support.
	 * @param client connected language server client
	 */
	public void connectLanguageServer(LspClient client) {
		languageSupport.connectLanguageServer(client);
	}

	/**
	 * Returns the language server connected to this tab, or {@code null} when none is
	 * connected yet.
	 * @return the connected language server client
	 */
	public LspClient getLanguageServer() {
		return languageSupport.getLanguageServer();
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
		languageSupport.enableLocalLanguageSupport();
	}

	boolean hasLocalLanguageSupport() {
		return languageSupport.hasLocalLanguageSupport();
	}

	CompletionProvider getLocalCompletionProvider() {
		return languageSupport.getLocalCompletionProvider();
	}

	/**
	 * Asks the connected language server for the definition at the caret.
	 * @return the definition locations, or a failed future when no server is connected
	 */
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definitionAtCaret() {
		return languageSupport.definitionAtCaret();
	}

	/**
	 * Selects an LSP range in the editor.
	 * @param range range to select, ignored when {@code null}
	 */
	public void goToRange(Range range) {
		languageSupport.goToRange(range);
	}

	/**
	 * Forwards diagnostics for this document to the LSP editor support.
	 * @param params diagnostics published by the server
	 */
	public void publishDiagnostics(PublishDiagnosticsParams params) {
		languageSupport.publishDiagnostics(params);
	}

	/**
	 * Returns the diagnostics most recently published for this document.
	 * @return the current diagnostics, never {@code null}
	 */
	public List<Diagnostic> getDiagnostics() {
		return languageSupport.getDiagnostics();
	}

	/**
	 * Registers a listener notified when diagnostics for this tab change.
	 * @param listener listener invoked with this tab, or {@code null} to clear it
	 */
	public void setDiagnosticsListener(Consumer<EditorTab> listener) {
		languageSupport.setDiagnosticsListener(listener);
	}

	/**
	 * Applies the configurable editor behavior options.
	 * @param tabSize number of spaces used for a tab
	 * @param wordWrap whether long lines wrap
	 * @param lineNumbers whether the line number gutter is shown
	 * @param showWhitespace whether whitespace characters are painted
	 */
	public void applyEditorOptions(int tabSize, boolean wordWrap, boolean lineNumbers, boolean showWhitespace) {
		content.applyEditorOptions(tabSize, wordWrap, lineNumbers, showWhitespace);
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
		if (content.isLoading()) {
			throw new IOException("File is still loading: " + file.getPath());
		}
		LargeTextViewer largeTextViewer = content.getLargeTextViewer();
		if (largeTextViewer != null) {
			throw new IOException("Large text files are open in a read-only paged view: " + file.getPath());
		}
		HexEditor hexEditor = content.getHexEditor();
		if (hexEditor != null) {
			hexEditor.save(file.getPath());
			buttonExit.setNotEdited();
			isEdit = false;
			document.setModified(false);
			return;
		}
		Editor editor = content.getEditor();
		if (editor == null) {
			return;
		}
		String text = editor.getText();
		try {
			fileService.saveFile(file.getPath(), text);
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
		file.getCode().replace(0, file.getCode().length(), text);
		this.buttonExit.setNotEdited();
		languageSupport.documentSaved();
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
		content.close();
		languageSupport.close();
		if (documentNode != null) {
			documentNode.close();
			content.getEditor().getDocument().removeDocumentListener(documentNode);
		}
		document.setOpened(false);
	}

	void initializeEditor() {
		configIsEdit();
		content.applyEditorOptions();
		content.getEditor().attachGutter(content.getScrollPane().getGutter());
		content.installBreakpointMouseListener();
	}

	void onBinaryChanged() {
		if (closed) {
			return;
		}
		isEdit = content.getHexEditor().isModified();
		document.setModified(isEdit);
		if (isEdit) {
			buttonExit.setEdited();
		}
		else {
			buttonExit.setNotEdited();
		}
	}

	private void configIsEdit() {
		documentNode = new DocumentNode(this::onDocumentChanged);
		content.getEditor().getDocument().addDocumentListener(documentNode);
		content.getScrollPane().setLineNumbersEnabled(true);
	}

	private void onDocumentChanged() {
		if (closed) {
			return;
		}
		Editor editor = content.getEditor();
		if (java.util.Objects.equals(file.getCode().toString(), editor.getText())) {
			this.isEdit = false;
			document.setModified(false);
			this.buttonExit.setNotEdited();
		}
		else {
			this.isEdit = true;
			document.setModified(true);
			this.buttonExit.setEdited();
		}
		document.incrementVersion();
		languageSupport.documentChanged();
		languageSupport.notifyDiagnostics();
	}

	int toOffset(Position position) throws BadLocationException {
		Editor editor = content.getEditor();
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
