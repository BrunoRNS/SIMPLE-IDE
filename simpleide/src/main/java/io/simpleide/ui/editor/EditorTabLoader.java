package io.simpleide.ui.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.client.LspClient;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.treefile.FileContentKind;
import io.simpleide.ui.treefile.TypeFile;

/**
 * Loads editor tab content in the background and connects language support.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class EditorTabLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(EditorTabLoader.class);

	private final EditorManager editorManager;

	EditorTabLoader(EditorManager editorManager) {
		this.editorManager = editorManager;
	}

	void loadFileInBackground(EditorTab tab, Path path, boolean connectLanguageServer) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					tab.getFile().loadContent();
					threadMaster.executeOnEDT(() -> {
						if (tab.isClosed() || !isTabAttached(tab, path)) {
							return;
						}
						try {
							tab.showLoadedContent();
							configureLoadedTab(tab, path, connectLanguageServer);
						}
						catch (RuntimeException | LinkageError error) {
							LOGGER.error("Unable to initialize the content viewer for {}.", path, error);
							tab.showLoadError(error);
						}
					});
				}
				catch (IOException error) {
					LOGGER.error("Unable to load file {}.", path, error);
					threadMaster.executeOnEDT(() -> {
						if (!tab.isClosed() && isTabAttached(tab, path)) {
							tab.showLoadError(error);
						}
					});
				}
			}));
		}
		catch (RuntimeException error) {
			LOGGER.error("Unable to schedule loading file {}.", path, error);
			tab.showLoadError(error);
		}
	}

	void configureLoadedTab(EditorTab tab, Path path, boolean connectLanguageServer) {
		editorManager.editorActions.configure(tab);
		try {
			tab.enableLocalLanguageSupport();
		}
		catch (RuntimeException | LinkageError error) {
			LOGGER.error("Unable to initialize local language support for {}; the file remains open.", path, error);
		}
		try {
			if (tab.getEditor() != null) {
				ThemeManager.getInstance().register(tab.getEditor());
			}
			if (tab.getHexEditor() != null) {
				ThemeManager.getInstance().register(tab.getHexEditor());
			}
		}
		catch (RuntimeException error) {
			LOGGER.error("Unable to apply the current theme to {}; the file remains open.", path, error);
		}
		if (connectLanguageServer && tab.getEditor() != null
				&& tab.getFile().getContentKind() == FileContentKind.TEXT) {
			editorManager.languageServers.connect(tab.getDocument(), tab::publishDiagnostics)
				.whenComplete((client, error) -> completeLanguageServerConnection(tab, client, error));
		}
	}

	boolean isTabAttached(EditorTab tab, Path path) {
		return editorManager.openedEditors.get(path) == tab || editorManager.jTabbedPane.indexOfComponent(tab) >= 0;
	}

	static void completeLanguageServerConnection(EditorTab tab, LspClient client, Throwable error) {
		if (error != null) {
			Throwable cause = error;

			if (error instanceof CompletionException && error.getCause() != null) {
				cause = error.getCause();
			}
			if (cause instanceof CancellationException) {
				return;
			}

			LOGGER.error("LSP connection failed for {}.", tab.getFile().getPath(), cause);

			if (client != null) {
				try {
					client.close();
				}
				catch (Exception closeError) {
					LOGGER.warn("Failed to close LSP client.", closeError);
				}
			}
		}

		ThreadMaster.getInstance().executeOnEDT(() -> {
			if (client == null || error != null) {
				tab.enableLocalLanguageSupport();
			}
			else {
				tab.connectLanguageServer(client);
			}
		});
	}

	static String getLanguageId(TypeFile type) {
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
