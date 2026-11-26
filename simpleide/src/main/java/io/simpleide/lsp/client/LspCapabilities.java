package io.simpleide.lsp.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DefinitionOptions;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.SimpleIDEVersion;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * Builds LSP client capabilities and interprets the server capabilities response.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LspCapabilities {

	private LspCapabilities() {
	}

	static TextDocumentSyncKind getSynchronizationKind(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null) {
			return TextDocumentSyncKind.None;
		}
		if (sync.isLeft()) {
			return sync.getLeft();
		}
		if (sync.getRight() == null) {
			return TextDocumentSyncKind.None;
		}
		TextDocumentSyncKind change = sync.getRight().getChange();
		return change == null ? TextDocumentSyncKind.None : change;
	}

	private static boolean hasOpenCloseSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null) {
			return false;
		}
		return sync.isLeft() || sync.getRight() != null && Boolean.TRUE.equals(sync.getRight().getOpenClose());
	}

	private static boolean hasSaveSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null || sync.isLeft() || sync.getRight() == null) {
			return false;
		}

		Either<Boolean, SaveOptions> save = sync.getRight().getSave();
		return save != null && (save.isLeft() ? Boolean.TRUE.equals(save.getLeft()) : save.getRight() != null);
	}

	private static boolean hasFormattingSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<Boolean, org.eclipse.lsp4j.DocumentFormattingOptions> provider = result.getCapabilities()
			.getDocumentFormattingProvider();
		return provider != null
				&& (provider.isLeft() ? Boolean.TRUE.equals(provider.getLeft()) : provider.getRight() != null);
	}

	private static boolean shouldIncludeTextOnSave(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null || sync.isLeft() || sync.getRight() == null) {
			return false;
		}

		Either<Boolean, SaveOptions> save = sync.getRight().getSave();
		return save != null && save.isRight() && save.getRight() != null
				&& Boolean.TRUE.equals(save.getRight().getIncludeText());
	}

	private static boolean hasDefinitionSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<Boolean, DefinitionOptions> provider = result.getCapabilities().getDefinitionProvider();
		return provider != null
				&& (provider.isLeft() ? Boolean.TRUE.equals(provider.getLeft()) : provider.getRight() != null);
	}

	record ServerCapabilitiesSnapshot(TextDocumentSyncKind synchronizationKind, boolean openCloseSupported,
			boolean completionSupported, boolean definitionSupported, boolean saveSupported, boolean saveIncludeText,
			boolean formattingSupported) {

		static final ServerCapabilitiesSnapshot NONE = new ServerCapabilitiesSnapshot(TextDocumentSyncKind.None, false,
				false, false, false, false, false);

	}

	static Position getEndPosition(String text) {
		int line = 0;
		int character = 0;
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			switch (current) {
				case '\n' -> {
					line++;
					character = 0;
				}
				case '\r' -> {
					if (index + 1 == text.length() || text.charAt(index + 1) != '\n') {
						line++;
						character = 0;
					}
				}
				default -> character++;
			}
		}
		return new Position(line, character);
	}

	static ClientCapabilities clientCapabilities() {
		CompletionCapabilities completionCapabilities = new CompletionCapabilities();
		completionCapabilities.setContextSupport(true);
		CompletionItemCapabilities completionItemCapabilities = new CompletionItemCapabilities();
		completionItemCapabilities.setSnippetSupport(false);
		completionCapabilities.setCompletionItem(completionItemCapabilities);

		TextDocumentClientCapabilities textDocumentCapabilities = new TextDocumentClientCapabilities();
		textDocumentCapabilities.setCompletion(completionCapabilities);

		DefinitionCapabilities definitionCapabilities = new DefinitionCapabilities();
		definitionCapabilities.setLinkSupport(true);
		textDocumentCapabilities.setDefinition(definitionCapabilities);

		FormattingCapabilities formattingCapabilities = new FormattingCapabilities();
		formattingCapabilities.setDynamicRegistration(false);
		textDocumentCapabilities.setFormatting(formattingCapabilities);

		SynchronizationCapabilities synchronizationCapabilities = new SynchronizationCapabilities();
		synchronizationCapabilities.setDidSave(true);
		textDocumentCapabilities.setSynchronization(synchronizationCapabilities);
		textDocumentCapabilities.setPublishDiagnostics(new PublishDiagnosticsCapabilities(true));

		ClientCapabilities capabilities = new ClientCapabilities();
		capabilities.setTextDocument(textDocumentCapabilities);

		WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
		workspaceCapabilities.setWorkspaceFolders(true);
		capabilities.setWorkspace(workspaceCapabilities);
		return capabilities;
	}

	static ServerCapabilitiesSnapshot snapshot(org.eclipse.lsp4j.InitializeResult result) {
		return new ServerCapabilitiesSnapshot(getSynchronizationKind(result), hasOpenCloseSupport(result),
				result.getCapabilities().getCompletionProvider() != null, hasDefinitionSupport(result),
				hasSaveSupport(result), shouldIncludeTextOnSave(result), hasFormattingSupport(result));
	}

}
