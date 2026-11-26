package io.simpleide.frame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.api.Languages;
import io.simpleide.dap.DapManager;
import io.simpleide.dap.DapSession;
import io.simpleide.dap.JavaDebugBridge;
import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.OutputType;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorTab;
import io.simpleide.ui.menubar.menus.IdeMenuBar;

/**
 * Runs and debugs the program selected in the main window.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class MainFrameExecutionController {

	private static final Logger logger = LoggerFactory.getLogger(MainFrameExecutionController.class);

	private final MainFrame frame;

	private DapManager dapManager;

	private DapSession dapSession;

	private Editor debugEditor;

	private volatile boolean debugAvailable;

	MainFrameExecutionController(MainFrame frame) {
		this.frame = frame;
	}

	void configureDebugAvailability() {
		try {
			dapManager = DapManager.createDefault();
			debugAvailable = dapManager.isSupported();
		}
		catch (DapManager.DapUnavailableException error) {
			dapManager = null;
			debugAvailable = false;
			logger.warn("Debugging is unavailable: {}", error.getMessage());
		}
	}

	boolean isDebugAvailable() {
		return debugAvailable;
	}

	void applyDebugAvailability() {
		if (frame.getJMenuBar() instanceof IdeMenuBar menuBar) {
			menuBar.setDebugEnabled(debugAvailable);
		}
		if (frame.keybindingManager != null) {
			frame.installKeybindings();
		}
	}

	void runProgram() {
		if (frame.ioPanel.isExecutionActive()) {
			showExecutionMessage("A program is already running. Stop it before starting another run.");
			return;
		}
		EditorTab tab = frame.editorManager.getSelectedEditorTab();
		if (tab == null || tab.getEditor() == null) {
			showExecutionMessage("Open a source file before running a program.");
			return;
		}
		if (!saveBeforeExecution(tab)) {
			return;
		}
		frame.outputTabs.setSelectedComponent(frame.ioPanel);
		try {
			AbstractLanguage language = Languages.create(tab.getFile().getPath());
			frame.ioPanel.beginRun(language);
			language.startManaged();
		}
		catch (RuntimeException error) {
			frame.ioPanel.finishExecution();
			String message = "Unable to run " + tab.getFile().getName() + ": " + error.getMessage();
			frame.ioPanel.appendLine(OutputType.ERROR, message);
			logger.error("Unable to run {}.", tab.getFile().getPath(), error);
			JOptionPane.showMessageDialog(frame, message, "Run failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	void debugProgram() {
		if (!debugAvailable || dapManager == null) {
			showExecutionMessage("Debugging is not available: no compatible debug adapter is installed.");
			return;
		}
		if (frame.ioPanel.isExecutionActive()) {
			showExecutionMessage("A program is already running. Stop it before starting a debug session.");
			return;
		}
		EditorTab tab = frame.editorManager.getSelectedEditorTab();
		if (tab == null || tab.getEditor() == null) {
			showExecutionMessage("Open a source file before starting a debug session.");
			return;
		}
		if (!saveBeforeExecution(tab)) {
			return;
		}
		Path program = tab.getFile().getPath();
		if (!dapManager.supports(program)) {
			showExecutionMessage("Debugging is not supported for " + tab.getFile().getName()
					+ ". Open a Java, JavaScript, Python or Go file to debug.");
			return;
		}
		frame.outputTabs.setSelectedComponent(frame.ioPanel);
		frame.ioPanel.beginDebug(null);
		List<Integer> breakpoints = tab.getBreakpoints();
		attachBreakpointListener(tab, program);
		DapSession.Listener listener = new DapSession.Listener() {
			@Override
			public void onTerminated() {
				SwingUtilities.invokeLater(() -> {
					dapSession = null;
					detachBreakpointListener();
					frame.ioPanel.finishExecution();
				});
			}
		};
		RunnableNode startNode = new RunnableNode(() -> {
			try {
				DapSession session = startDebugSession(tab, program, breakpoints, listener);
				dapSession = session;
				session.setBreakpoints(program, tab.getBreakpoints());
				SwingUtilities.invokeLater(() -> frame.ioPanel.setSession(session));
			}
			catch (DapManager.DapUnavailableException error) {
				SwingUtilities.invokeLater(() -> handleDebugFailure(error.getMessage()));
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				SwingUtilities.invokeLater(() -> handleDebugFailure("Debug startup was interrupted."));
			}
			catch (RuntimeException error) {
				SwingUtilities
					.invokeLater(() -> handleDebugFailure("Unable to start the debug session: " + error.getMessage()));
			}
		});
		ThreadMaster.getInstance().register(startNode);
	}

	/**
	 * Starts the debug session for a program, using the JDT language server for Java and
	 * a standalone adapter for the other languages.
	 */
	private DapSession startDebugSession(EditorTab tab, Path program, List<Integer> breakpoints,
			DapSession.Listener listener) throws DapManager.DapUnavailableException, InterruptedException {
		if (!dapManager.isLanguageServerDebug(program)) {
			return dapManager.start(program, breakpoints, listener);
		}
		LspClient client = tab.getLanguageServer();
		if (client == null) {
			try {
				client = frame.editorManager.connectLanguageServer(tab.getDocument())
					.get(30, java.util.concurrent.TimeUnit.SECONDS);
			}
			catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
				throw new DapManager.DapUnavailableException(
						"Unable to reach the Java language server: " + error.getMessage(), error);
			}
		}
		LspClient activeClient = client;
		JavaDebugBridge.JavaDebugClient bridgeClient = (command, arguments) -> activeClient.executeCommand(command,
				arguments);
		return dapManager.startJava(program, bridgeClient, breakpoints, listener);
	}

	private void handleDebugFailure(String message) {
		dapSession = null;
		detachBreakpointListener();
		frame.ioPanel.finishExecution();
		String explanation = message == null || message.isBlank()
				? "Debugging is not available: the debug adapter could not be installed." : message;
		frame.ioPanel.appendLine(OutputType.ERROR, explanation);
		logger.error("Debugging is unavailable: {}", explanation);
		JOptionPane.showMessageDialog(frame, explanation, "Debug unavailable", JOptionPane.ERROR_MESSAGE);
	}

	private boolean saveBeforeExecution(EditorTab tab) {
		if (!tab.isEdit()) {
			return true;
		}
		try {
			tab.save();
			return true;
		}
		catch (IOException error) {
			String message = "Unable to save " + tab.getFile().getName() + " before execution: " + error.getMessage();
			frame.ioPanel.appendLine(OutputType.ERROR, message);
			logger.error("Unable to save {} before execution.", tab.getFile().getPath(), error);
			JOptionPane.showMessageDialog(frame, message, "Execute failed", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private void attachBreakpointListener(EditorTab tab, Path program) {
		detachBreakpointListener();
		debugEditor = tab.getEditor();
		debugEditor.setBreakpointListener(lines -> {
			DapSession activeSession = dapSession;
			if (activeSession != null) {
				activeSession.setBreakpoints(program, lines);
			}
		});
	}

	void detachBreakpointListener() {
		if (debugEditor != null) {
			debugEditor.setBreakpointListener(null);
			debugEditor = null;
		}
	}

	void close() {
		detachBreakpointListener();
		if (dapSession != null) {
			dapSession.close();
			dapSession = null;
		}
	}

	/**
	 * Stops whatever is currently executing: an active debug session, or the language
	 * process of a plain run. Safe to call when nothing is running.
	 */
	void stopProgram() {
		DapSession active = dapSession;
		if (active != null) {
			dapSession = null;
			detachBreakpointListener();
			active.close();
			frame.ioPanel.finishExecution();
			return;
		}
		frame.ioPanel.stopRun();
	}

	private void showExecutionMessage(String message) {
		frame.outputTabs.setSelectedComponent(frame.ioPanel);
		frame.ioPanel.appendLine(OutputType.DEBUG, message);
		JOptionPane.showMessageDialog(frame, message, "Execute", JOptionPane.INFORMATION_MESSAGE);
	}

}
