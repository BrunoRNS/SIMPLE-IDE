package io.simpleide.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.swing.JComponent;

import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.lsp.core.LanguageServerManager;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorTab;
import io.simpleide.ui.treefile.SimpleIDEFile;
import io.simpleide.ui.treefile.TypeFile;

/**
 * A standalone editor tab that can be embedded in any Swing container.
 *
 * <p>
 * The tab presents text through the editor, images through {@link ImageViewer}, audio and
 * video through a JavaFX-backed {@link MediaPlayback} when the platform supports it, and
 * binary files through {@link HexEditor}. Media and binary viewers are read-only. On
 * platforms without JavaFX native artifacts, audio and video files open in the hex
 * editor. Local language support and LSP apply only to text files. The server process is
 * stopped when the editor is closed.
 *
 * <p>
 * Covered by {@code SimpleEditorTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/editor.md">docs/api/editor.md</a>
 */
public final class SimpleEditor implements AutoCloseable {

	private final Path path;

	private final Document document;

	private final EditorTab tab;

	private final LanguageServerManager languageServers;

	private CompletableFuture<LspClient> languageServer;

	private volatile boolean closed;

	private SimpleEditor(Path path, Consumer<LspStatus> statusListener) throws IOException {
		this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
		SimpleIDEFile file = new SimpleIDEFile(this.path);
		this.document = new Document(this.path, languageIdFor(file.getTypeFile()));
		try {
			this.tab = new EditorTab(file, document);
		}
		catch (UncheckedIOException error) {
			throw error.getCause();
		}
		this.languageServers = new LanguageServerManager(statusListener);
	}

	/**
	 * Opens a text, image, or binary file in a standalone editor tab.
	 * @param path file to open
	 * @return a new editor tab
	 * @throws IOException when the file cannot be read
	 */
	public static SimpleEditor open(Path path) throws IOException {
		return open(path, ignored -> {
		});
	}

	/**
	 * Opens a text, image, or binary file and reports language-server lifecycle changes.
	 * @param path file to open
	 * @param statusListener language-server status listener
	 * @return a new editor tab
	 * @throws IOException when the file cannot be read
	 */
	public static SimpleEditor open(Path path, Consumer<LspStatus> statusListener) throws IOException {
		return new SimpleEditor(path, Objects.requireNonNull(statusListener, "statusListener"));
	}

	/**
	 * Returns the embeddable Swing component.
	 * @return the editor tab component
	 */
	public JComponent component() {
		return tab;
	}

	/**
	 * Returns the underlying editor tab.
	 * @return the editor tab
	 */
	public EditorTab tab() {
		return tab;
	}

	/**
	 * Returns the RSyntaxTextArea-based editor.
	 * @return the editor
	 */
	public Editor textArea() {
		if (tab.getEditor() == null) {
			throw new IllegalStateException("This file is displayed in a non-editable viewer.");
		}
		return tab.getEditor();
	}

	/** Returns the image viewer for an image file, otherwise {@code null}. */
	public ImageViewer imageViewer() {
		return tab.getImageViewer();
	}

	/** Returns the paged read-only view for a large text file, otherwise {@code null}. */
	public LargeTextViewer largeTextViewer() {
		return tab.getLargeTextViewer();
	}

	/** Returns the hex editor for a binary file, otherwise {@code null}. */
	public HexEditor hexEditor() {
		return tab.getHexEditor();
	}

	/**
	 * Returns the audio player, or {@code null} when the file is not audio or playback is
	 * unavailable on this platform.
	 * @return the audio player, or {@code null}
	 * @author BrunoRNS
	 * @since v1.1.0
	 * @see MediaPlaybacks
	 */
	public MediaPlayback audioViewer() {
		return tab.getAudioViewer();
	}

	/**
	 * Returns the video player, or {@code null} when the file is not video or playback is
	 * unavailable on this platform.
	 * @return the video player, or {@code null}
	 * @author BrunoRNS
	 * @since v1.1.0
	 * @see MediaPlaybacks
	 */
	public MediaPlayback videoViewer() {
		return tab.getVideoViewer();
	}

	/**
	 * Returns the LSP document associated with this editor.
	 * @return the document
	 */
	public Document document() {
		return document;
	}

	/**
	 * Returns the normalized source path.
	 * @return the source path
	 */
	public Path path() {
		return path;
	}

	/**
	 * Enables local completion and syntax support when no language server is connected.
	 */
	public void enableLocalLanguageSupport() {
		tab.enableLocalLanguageSupport();
	}

	/**
	 * Starts the language server for this text file, if one is available for the
	 * platform.
	 *
	 * <p>
	 * The returned future completes when the server has initialized and the editor is
	 * connected. Repeated calls return the same future. The server is closed by
	 * {@link #close()}.
	 * @throws IllegalStateException if this is an image or binary file
	 * @return a future with the connected language-server client
	 */
	public synchronized CompletableFuture<LspClient> startLanguageServer() {
		ensureOpen();
		if (tab.getEditor() == null) {
			throw new IllegalStateException("Language servers are available only for text files.");
		}
		if (languageServer != null) {
			return languageServer;
		}
		Threads.master();
		languageServer = languageServers.connect(document, tab::publishDiagnostics).thenApply(client -> {
			tab.connectLanguageServer(client);
			return client;
		});
		return languageServer;
	}

	/**
	 * Saves the editor content to disk.
	 * @throws IOException when the file cannot be written
	 */
	public void save() throws IOException {
		ensureOpen();
		tab.save();
	}

	/**
	 * Saves the editor content to a different file and loads it in the editor.
	 * @param target destination file
	 * @throws IOException when the file cannot be written or read back
	 */
	public void saveAs(Path target) throws IOException {
		ensureOpen();
		Path destination = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
		Files.writeString(destination, textArea().getText());
		textArea().loadFile(new SimpleIDEFile(destination));
	}

	/**
	 * Returns whether the content has unsaved changes.
	 * @return {@code true} when the editor content is modified
	 */
	public boolean isModified() {
		return tab.isEdit();
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		tab.close();
		languageServers.close();
	}

	private void ensureOpen() {
		if (closed) {
			throw new IllegalStateException("The editor has been closed.");
		}
	}

	private static String languageIdFor(TypeFile type) {
		return switch (type) {
			case JAVA -> "java";
			case CSHARP -> "csharp";
			case KOTLIN -> "kotlin";
			case GROOVY -> "groovy";
			case JAVASCRIPT -> "javascript";
			case GO -> "go";
			case RUST -> "rust";
			case PYTHON -> "python";
			case C -> "c";
			case CPP -> "cpp";
			default -> "plaintext";
		};
	}

}
