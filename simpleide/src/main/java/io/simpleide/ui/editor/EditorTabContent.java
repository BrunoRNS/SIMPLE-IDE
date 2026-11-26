package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.IOException;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.api.HexEditor;
import io.simpleide.api.ImageViewer;
import io.simpleide.api.LargeTextViewer;
import io.simpleide.api.MediaPlayback;
import io.simpleide.api.MediaPlaybacks;
import io.simpleide.thread.nodes.edt.listeners.MouseNode;

/**
 * Builds and owns the content components shown by an editor tab.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class EditorTabContent {

	private static final Logger LOGGER = LoggerFactory.getLogger(EditorTabContent.class);

	private final EditorTab tab;

	private Editor editor;

	private ImageViewer imageViewer;

	private HexEditor hexEditor;

	private LargeTextViewer largeTextViewer;

	private MediaPlayback audioViewer;

	private MediaPlayback videoViewer;

	private EditorSearchPanel searchPanel;

	private RTextScrollPane scrollPane;

	private volatile boolean loading;

	private int editorTabSize = 4;

	private boolean editorWordWrap;

	private boolean editorLineNumbers = true;

	private boolean editorShowWhitespace;

	EditorTabContent(EditorTab tab) {
		this.tab = tab;
	}

	boolean isLoading() {
		return loading;
	}

	void showLoadedContent() {
		if (tab.isClosed()) {
			return;
		}
		if (!tab.getFile().isContentLoaded()) {
			throw new IllegalStateException("File content is not loaded.");
		}
		loading = false;
		tab.removeAll();
		editor = null;
		imageViewer = null;
		hexEditor = null;
		largeTextViewer = null;
		audioViewer = null;
		videoViewer = null;
		searchPanel = null;
		scrollPane = null;
		switch (tab.getFile().getContentKind()) {
			case IMAGE -> {
				imageViewer = new ImageViewer(tab.getFile().getImageContent(), tab.getFile().getImageWidth(),
						tab.getFile().getImageHeight());
				tab.add(imageViewer, BorderLayout.CENTER);
			}
			case BINARY -> showHexEditor();
			case AUDIO -> {
				audioViewer = MediaPlaybacks.audio(tab.getFile().getPath());
				if (audioViewer == null) {
					showHexEditor();
				}
				else {
					tab.add(audioViewer.component(), BorderLayout.CENTER);
				}
			}
			case VIDEO -> {
				videoViewer = MediaPlaybacks.video(tab.getFile().getPath());
				if (videoViewer == null) {
					showHexEditor();
				}
				else {
					tab.add(videoViewer.component(), BorderLayout.CENTER);
				}
			}
			case TEXT -> {
				if (tab.getFile().isLargeContent()) {
					largeTextViewer = new LargeTextViewer(tab.getFile().getPath(), tab.getFile().getFileSize());
					tab.add(largeTextViewer, BorderLayout.CENTER);
					break;
				}
				editor = new Editor();
				searchPanel = new EditorSearchPanel(editor);
				scrollPane = new RTextScrollPane(editor);
				tab.add(searchPanel, BorderLayout.NORTH);
				tab.add(scrollPane, BorderLayout.CENTER);
				editor.loadFile(tab.getFile());
				tab.initializeEditor();
			}
		}
		tab.revalidate();
		tab.repaint();
	}

	private void showHexEditor() {
		try {
			hexEditor = new HexEditor(tab.getFile().getPath());
		}
		catch (IOException error) {
			throw new java.io.UncheckedIOException("Unable to open binary file " + tab.getFile().getPath(), error);
		}
		hexEditor.setChangeListener(tab::onBinaryChanged);
		tab.add(hexEditor, BorderLayout.CENTER);
	}

	void showLoadError(Throwable error) {
		if (tab.isClosed()) {
			return;
		}
		loading = false;
		tab.removeAll();
		tab.add(new JLabel("Unable to load " + tab.getFile().getName() + ": " + error.getMessage(),
				SwingConstants.CENTER), BorderLayout.CENTER);
		tab.revalidate();
		tab.repaint();
	}

	void showLoading() {
		loading = true;
		JPanel status = new JPanel(new BorderLayout(0, 8));
		status.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JLabel label = new JLabel("Loading " + tab.getFile().getName() + "...", SwingConstants.CENTER);
		JProgressBar progress = new JProgressBar();
		progress.setIndeterminate(true);
		status.add(label, BorderLayout.CENTER);
		status.add(progress, BorderLayout.SOUTH);
		tab.add(status, BorderLayout.CENTER);
	}

	Editor getEditor() {
		return editor;
	}

	ImageViewer getImageViewer() {
		return imageViewer;
	}

	HexEditor getHexEditor() {
		return hexEditor;
	}

	LargeTextViewer getLargeTextViewer() {
		return largeTextViewer;
	}

	MediaPlayback getAudioViewer() {
		return audioViewer;
	}

	MediaPlayback getVideoViewer() {
		return videoViewer;
	}

	RTextScrollPane getScrollPane() {
		return scrollPane;
	}

	void showSearchPanel(boolean showReplacement) {
		if (searchPanel != null) {
			searchPanel.open(showReplacement);
		}
	}

	void applyEditorOptions(int tabSize, boolean wordWrap, boolean lineNumbers, boolean showWhitespace) {
		editorTabSize = tabSize;
		editorWordWrap = wordWrap;
		editorLineNumbers = lineNumbers;
		editorShowWhitespace = showWhitespace;
		applyEditorOptions();
	}

	void applyEditorOptions() {
		if (editor == null || scrollPane == null) {
			return;
		}
		editor.setTabSize(editorTabSize);
		editor.setLineWrap(editorWordWrap);
		scrollPane.setLineNumbersEnabled(editorLineNumbers);
		editor.setWhitespaceVisible(editorShowWhitespace);
	}

	void installBreakpointMouseListener() {
		if (scrollPane == null || scrollPane.getGutter() == null) {
			return;
		}
		MouseNode clickListener = new MouseNode().onClick(event -> {
			if (!SwingUtilities.isLeftMouseButton(event)) {
				return;
			}
			Integer line = lineAtGutterPoint(event);
			if (line != null) {
				editor.toggleBreakpoint(line);
			}
		}).onExit(event -> {
			editor.setHoverLine(0);
		});
		MouseNode hoverListener = new MouseNode().onMove(event -> {
			Integer line = lineAtGutterPoint(event);
			editor.setHoverLine(line == null ? 0 : line);
		});
		// The breakpoint column is drawn by the gutter's icon row header, which is a
		// child component: mouse events over it never reach the gutter itself, so the
		// listeners are installed on the gutter and on every one of its children.
		installGutterListeners(scrollPane.getGutter(), clickListener, hoverListener);
	}

	private void installGutterListeners(Component component, MouseNode clickListener, MouseNode hoverListener) {
		component.addMouseListener(clickListener);
		component.addMouseMotionListener(hoverListener);
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				installGutterListeners(child, clickListener, hoverListener);
			}
		}
	}

	/**
	 * Maps a gutter mouse position to the one-based editor line under the pointer.
	 * @param event gutter mouse event
	 * @return the one-based line, or {@code null} when the position is not over a line
	 */
	private Integer lineAtGutterPoint(MouseEvent event) {
		if (editor == null) {
			return null;
		}
		Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), editor);
		// The gutter sits to the left of the text area, so the converted x is negative.
		// Clamp it inside the editor so viewToModel2D resolves the vertical position.
		point.x = Math.max(0, Math.min(point.x, Math.max(0, editor.getWidth() - 1)));
		int offset = editor.viewToModel2D(point);
		if (offset < 0 || offset > editor.getDocument().getLength()) {
			return null;
		}
		try {
			return editor.getLineOfOffset(offset) + 1;
		}
		catch (BadLocationException ignored) {
			return null;
		}
	}

	void close() {
		if (largeTextViewer != null) {
			largeTextViewer.close();
		}
		if (audioViewer != null) {
			audioViewer.close();
		}
		if (videoViewer != null) {
			videoViewer.close();
		}
		if (imageViewer != null) {
			imageViewer.releaseImage();
			tab.getFile().releaseImageContent();
		}
		if (hexEditor != null) {
			try {
				hexEditor.close();
			}
			catch (IOException error) {
				LOGGER.warn("Unable to release HEX editor resources for {}.", tab.getFile().getPath(), error);
			}
		}
	}

}
