package io.simpleide.ui.io;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.dap.DapSession;
import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.exceptions.ProcessFatalException;
import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.edt.listeners.SwingActionNode;
import io.simpleide.ui.ThemeColors;

/**
 * Terminal-area tab with program input, coloured program output and debug controls.
 *
 * <p>
 * The upper text area sends every submitted line through the shared
 * {@link io.simpleide.language.io.Input} queue while the program runs. The lower text
 * pane displays output events from the shared {@link Output} queue, prefixing each line
 * with {@code [OUTPUT]}, {@code [ERROR]} or {@code [DEBUG]} and colouring it with the
 * active theme. The pane is read-only but selectable, so its text can be copied with
 * {@code Ctrl+C} or the context menu.
 *
 * <p>
 * When a program runs without debugging the panel shows a single Stop button that
 * terminates the language process. When a debug session is running it also shows
 * continue, pause, step and stop buttons that drive the {@link DapSession}.
 *
 * <p>
 * Covered by {@code IOPanelTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class IOPanel extends JPanel {

	private static final Logger LOGGER = LoggerFactory.getLogger(IOPanel.class);

	private static final ManagedThreadPool STOP_POOL = ThreadMaster.getInstance().createThreadPool("io-panel-stop", 2);

	private final JTextArea inputArea = new JTextArea();

	private final JTextPane outputPane = new JTextPane();

	private final JPanel debugControls = new JPanel(new GridLayout(1, 0, 6, 0));

	private final JPanel runControls = new JPanel(new GridLayout(1, 0, 6, 0));

	private final JButton continueButton = controlButton("Continue", DapSession::resume);

	private final JButton pauseButton = controlButton("Pause", DapSession::pause);

	private final JButton stepOverButton = controlButton("Step Over", DapSession::stepOver);

	private final JButton stepIntoButton = controlButton("Step Into", DapSession::stepInto);

	private final JButton stepOutButton = controlButton("Step Out", DapSession::stepOut);

	private final JButton stopButton = controlButton("Stop", DapSession::close);

	private final JButton runStopButton = new JButton("Stop");

	private InputForwarder forwarder;

	private final Consumer<Output.OutputLine> outputListener = this::append;

	private AbstractLanguage language;

	private Runnable executionListener;

	private DapSession session;

	private boolean debugMode;

	private boolean outputSubscribed;

	private Consumer<String> inputHistoryListener = ignored -> {
	};

	/**
	 * Creates an empty I/O tab with the application theme.
	 */
	public IOPanel() {
		super(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createInputSection(), createOutputSection());
		split.setResizeWeight(0.32);
		split.setContinuousLayout(true);
		split.setBorder(BorderFactory.createEmptyBorder());
		add(split, BorderLayout.CENTER);
		add(createControls(), BorderLayout.SOUTH);

		inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "simpleide.io.submit");
		inputArea.getActionMap().put("simpleide.io.submit", new SwingActionNode("Submit", this::submitInput));
		IOOutputStyler.applyTheme(inputArea, outputPane);
		setDebugMode(false);
		runControls.setVisible(false);
	}

	private JPanel createInputSection() {
		JPanel section = new JPanel(new BorderLayout(0, 4));
		section.setOpaque(false);
		section.add(sectionTitle("INPUT"), BorderLayout.NORTH);

		inputArea.setLineWrap(true);
		inputArea.setWrapStyleWord(true);
		inputArea.setRows(4);
		inputArea.setToolTipText("Type program input and press Enter to send it");
		inputArea.getAccessibleContext().setAccessibleName("Program input");
		JScrollPane scroll = new JScrollPane(inputArea);
		scroll.setBorder(BorderFactory.createLineBorder(ThemeColors.ui("Component.borderColor", "Separator.foreground"),
				1, true));
		section.add(scroll, BorderLayout.CENTER);
		return section;
	}

	private JPanel createOutputSection() {
		JPanel section = new JPanel(new BorderLayout(0, 4));
		section.setOpaque(false);
		section.add(sectionTitle("OUTPUT"), BorderLayout.NORTH);

		outputPane.setEditable(false);
		outputPane.setFocusable(true);
		outputPane.setToolTipText("Program output (select text and press Ctrl+C to copy)");
		outputPane.getAccessibleContext().setAccessibleName("Program output");
		installOutputCopySupport();
		JScrollPane scroll = new JScrollPane(outputPane);
		scroll.setBorder(BorderFactory.createLineBorder(ThemeColors.ui("Component.borderColor", "Separator.foreground"),
				1, true));
		section.add(scroll, BorderLayout.CENTER);
		return section;
	}

	private JPanel createControls() {
		debugControls.setOpaque(false);
		debugControls.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		debugControls.add(continueButton);
		debugControls.add(pauseButton);
		debugControls.add(stepOverButton);
		debugControls.add(stepIntoButton);
		debugControls.add(stepOutButton);
		debugControls.add(stopButton);
		debugControls.setPreferredSize(new Dimension(0, 32));

		runControls.setOpaque(false);
		runControls.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		runStopButton.setToolTipText("Stop the running program");
		runStopButton.setActionCommand("stop-run");
		runStopButton.addActionListener(new ActionNode(event -> stopLanguage()));
		runControls.add(runStopButton);
		runControls.setPreferredSize(new Dimension(0, 32));

		JPanel host = new JPanel(new BorderLayout());
		host.setOpaque(false);
		host.add(debugControls, BorderLayout.CENTER);
		host.add(runControls, BorderLayout.CENTER);
		return host;
	}

	/**
	 * Makes the read-only output pane selectable and copyable from the keyboard, the
	 * context menu and the platform copy shortcut.
	 */
	private void installOutputCopySupport() {
		outputPane.getInputMap().put(KeyStroke.getKeyStroke("control C"), DefaultEditorKit.copyAction);
		outputPane.getInputMap().put(KeyStroke.getKeyStroke("meta C"), DefaultEditorKit.copyAction);
		outputPane.getActionMap().put(DefaultEditorKit.copyAction, new SwingActionNode("Copy", outputPane::copy));
		JPopupMenu menu = new JPopupMenu();
		menu.add(new JMenuItem(new SwingActionNode("Copy", outputPane::copy)));
		menu.add(new JMenuItem(new SwingActionNode("Select All", outputPane::selectAll)));
		outputPane.setComponentPopupMenu(menu);
	}

	private JLabel sectionTitle(String text) {
		JLabel title = new JLabel(text);
		Font font = UIManager.getFont("Label.font");
		if (font != null) {
			title.setFont(font.deriveFont(Font.BOLD, Math.max(10f, font.getSize2D() - 1f)));
		}
		return title;
	}

	private JButton controlButton(String label, Consumer<DapSession> action) {
		JButton button = new JButton(label);
		button.addActionListener(new ActionNode(event -> {
			DapSession active = session;
			if (active != null) {
				action.accept(active);
			}
		}));
		return button;
	}

	/**
	 * Starts a plain run: clears the output and forwards program output and input.
	 * @param runningLanguage language process that will own the input forwarder
	 */
	public void beginRun(AbstractLanguage runningLanguage) {
		beginExecution(runningLanguage, false);
	}

	/**
	 * Starts a debug run: clears the output, forwards output and input and shows the
	 * debug controls.
	 * @param runningLanguage language process that will own the input forwarder, may be
	 * {@code null} when the adapter launches the program itself
	 */
	public void beginDebug(AbstractLanguage runningLanguage) {
		beginExecution(runningLanguage, true);
	}

	private void beginExecution(AbstractLanguage runningLanguage, boolean debug) {
		finishExecution();
		clearOutput();
		debugMode = debug;
		language = runningLanguage;
		forwarder = new InputForwarder();
		// Consome o conteudo ja digitado na area de input como stdin inicial
		// do programa. O usuario ainda pode digitar mais inputs e enviar com Enter.
		String initialInput = inputArea.getText();
		if (initialInput != null && !initialInput.isBlank()) {
			forwarder.submit(initialInput);
			inputArea.setText("");
		}
		Output.getInstance().addListener(outputListener);
		outputSubscribed = true;
		if (language != null) {
			executionListener = () -> {
				if (SwingUtilities.isEventDispatchThread()) {
					finishExecution();
				}
				else {
					SwingUtilities.invokeLater(this::finishExecution);
				}
			};
			language.addExecutionListener(executionListener);
		}
		setDebugMode(debug);
		runControls.setVisible(!debug);
		inputArea.requestFocusInWindow();
	}

	/**
	 * Releases the input forwarder and output subscription after the program ends.
	 *
	 * <p>
	 * Calling the method more than once is safe; it is registered as the language
	 * execution listener and is also called when a new run starts.
	 */
	public void finishExecution() {
		InputForwarder activeForwarder = forwarder;
		forwarder = null;
		if (activeForwarder != null) {
			activeForwarder.close();
		}
		if (outputSubscribed) {
			Output.getInstance().removeListener(outputListener);
			outputSubscribed = false;
		}
		if (language != null && executionListener != null) {
			language.removeExecutionListener(executionListener);
		}
		language = null;
		executionListener = null;
		DapSession activeSession = session;
		session = null;
		if (activeSession != null) {
			activeSession.close();
		}
		if (debugMode) {
			setDebugMode(false);
		}
		runControls.setVisible(false);
	}

	/**
	 * Stops the language process launched without debugging.
	 *
	 * <p>
	 * The language waits for the process to die, so the termination runs off the event
	 * dispatch thread; the language execution listener then resets the panel.
	 */
	private void stopLanguage() {
		AbstractLanguage active = language;
		if (active == null) {
			return;
		}
		// forceStop blocks until the process dies, so it runs on a dedicated
		// ThreadMaster pool rather than the event dispatch thread or shared workers.
		STOP_POOL.execute(() -> {
			try {
				active.forceStop();
			}
			catch (ProcessFatalException error) {
				LOGGER.warn("Unable to stop the running language process.", error);
				Output.getInstance()
					.publish(OutputType.ERROR, "Unable to stop the running program: " + error.getMessage());
			}
		});
	}

	/**
	 * Binds the debug session used by the control buttons.
	 * @param debugSession active debug session, or {@code null} to clear it
	 */
	public void setSession(DapSession debugSession) {
		this.session = debugSession;
	}

	/**
	 * Shows or hides the debug control buttons.
	 * @param debug {@code true} for debug mode
	 */
	public void setDebugMode(boolean debug) {
		debugMode = debug;
		debugControls.setVisible(debug);
		revalidate();
		repaint();
	}

	/**
	 * Returns whether the panel is currently in debug mode.
	 * @return {@code true} when the debug controls are expected
	 */
	public boolean isDebugMode() {
		return debugMode;
	}

	/**
	 * Returns whether an execution is currently forwarding input and output.
	 * @return {@code true} while a run or debug session is active
	 */
	public boolean isExecutionActive() {
		return forwarder != null;
	}

	/**
	 * Sends the current input area content to the running process.
	 * @return {@code true} when at least one line was queued
	 */
	public boolean submitInput() {
		InputForwarder active = forwarder;
		if (active == null) {
			return false;
		}
		String text = inputArea.getText();
		boolean submitted = active.submit(text);
		if (submitted) {
			inputArea.setText("");
			inputHistoryListener.accept(text.strip());
		}
		return submitted;
	}

	/**
	 * Registers a listener notified with the text of every submitted input.
	 * @param listener listener to invoke, or {@code null} to clear it
	 */
	public void setInputHistoryListener(Consumer<String> listener) {
		inputHistoryListener = listener == null ? ignored -> {
		} : listener;
	}

	/**
	 * Appends a typed output line to the pane.
	 * @param line line published by the language process or debug adapter
	 */
	private void append(Output.OutputLine line) {
		IOOutputStyler.appendLine(outputPane, line.type(), line.text());
	}

	/**
	 * Appends a typed output line to the pane, colouring it with the active theme.
	 * @param type classification of the line
	 * @param text line text
	 */
	public void appendLine(OutputType type, String text) {
		IOOutputStyler.appendLine(outputPane, type, text);
	}

	/**
	 * Removes every line from the output pane.
	 */
	public void clearOutput() {
		outputPane.setText("");
	}

	/**
	 * Returns the output text currently displayed.
	 * @return the plain output text
	 */
	public String getOutputText() {
		return outputPane.getText();
	}

	/**
	 * Returns the input text currently typed.
	 * @return the input text
	 */
	public String getInputText() {
		return inputArea.getText();
	}

	/**
	 * Replaces the input text.
	 * @param text text to show in the input area
	 */
	public void setInputText(String text) {
		inputArea.setText(text == null ? "" : text);
	}

	/**
	 * Returns the component used to enter program input.
	 * @return the input text area
	 */
	public JTextArea getInputArea() {
		return inputArea;
	}

	/**
	 * Returns the component that displays program output.
	 * @return the output text pane
	 */
	public JTextPane getOutputPane() {
		return outputPane;
	}

	/**
	 * Returns the debug control panel.
	 * @return the panel holding the debug buttons
	 */
	public JPanel getDebugControls() {
		return debugControls;
	}

	/**
	 * Returns the controls shown while a program runs without debugging.
	 * @return the panel holding the Stop button used by plain runs
	 */
	public JPanel getRunControls() {
		return runControls;
	}

	/**
	 * Refreshes the panel colors and output styles after a theme change.
	 */
	@Override
	public void updateUI() {
		super.updateUI();
		if (inputArea != null) {
			IOOutputStyler.applyTheme(inputArea, outputPane);
			IOOutputStyler.refreshOutputStyles(outputPane);
		}
	}

}
