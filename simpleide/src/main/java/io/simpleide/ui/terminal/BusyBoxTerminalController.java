package io.simpleide.ui.terminal;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.language.config.LangPlatform;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.WatchDead;

/**
 * Lifecycle controller for the embedded BusyBox terminal.
 *
 * <p>
 * Creates terminal tabs on demand, launches one session per tab on a dedicated managed
 * pool, tracks process exit, reports failures and coordinates shutdown when the window
 * closes. Each session is limited by {@link TerminalPanel#MAX_TABS} and by platform
 * support.
 *
 * <p>
 * Covered by {@code BusyBoxTerminalControllerTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/terminal.md">docs/api/terminal.md</a>
 */
public final class BusyBoxTerminalController {

	private static final Logger LOGGER = LoggerFactory.getLogger(BusyBoxTerminalController.class);

	private final ThreadMaster threads = ThreadMaster.getInstance();

	private final TerminalPanel panel;

	private final LangPlatform platform;

	private final ManagedThreadPool terminalWorkers;

	private final Object tabsLock = new Object();

	private final Map<BigInteger, TerminalTab> tabs = new LinkedHashMap<>();

	private BigInteger nextSessionId = BigInteger.ZERO;

	private boolean windowClosing;

	private Runnable onAllTabsClosed;

	/**
	 * Creates a controller for the current platform.
	 * @param panel panel whose terminals are managed
	 */
	public BusyBoxTerminalController(TerminalPanel panel) {
		this(panel, LangPlatform.getCurrentPlatform());
	}

	BusyBoxTerminalController(TerminalPanel panel, LangPlatform platform) {
		this.panel = panel;
		this.platform = platform;
		terminalWorkers = threads.createThreadPool("terminal", TerminalPanel.MAX_TABS);
		panel.setActions(this::addTerminal, this::closeSelectedTerminal);
		schedulePanelUpdate();
	}

	/**
	 * Starts an orderly shutdown of every terminal session.
	 *
	 * <p>
	 * The callback runs after all sessions have closed, immediately when no tab is open.
	 * Launching a new terminal is disabled from this point on.
	 * @param onClosed callback invoked when no terminal session remains
	 */
	public void onWindowClosing(Runnable onClosed) {
		List<TerminalTab> openTabs;
		synchronized (tabsLock) {
			windowClosing = true;
			onAllTabsClosed = onClosed;
			openTabs = new ArrayList<>(tabs.values());
			openTabs.forEach(tab -> tab.setCloseRequested(true));
		}
		schedulePanelUpdate();
		if (openTabs.isEmpty()) {
			runCloseCallback();
			return;
		}
		openTabs.stream().filter(TerminalTab::isLaunchComplete).forEach(this::closeSession);
	}

	private void addTerminal() {
		TerminalTab tab;
		synchronized (tabsLock) {
			if (windowClosing || platform == LangPlatform.UNKNOWN || tabs.size() >= TerminalPanel.MAX_TABS) {
				return;
			}
			nextSessionId = nextSessionId.add(BigInteger.ONE);
			tab = new TerminalTab(nextSessionId);
			tabs.put(tab.id, tab);
		}

		panel.addTerminalTab(tab.id);
		updatePanelState();

		executeTerminalTask("launch session " + tab.id, () -> {
			try {
				launch(tab);
			}
			finally {
				onLaunchTaskClosed(tab);
			}
		});
	}

	@SuppressWarnings("resource")
	private void launch(TerminalTab tab) {
		BusyBoxTerminalSession session = new BusyBoxTerminalSession(platform);
		tab.setSession(session);
		try {
			session.start();
			if (!session.isAlive()) {
				tab.setLaunchFailure(new IllegalStateException(
						"The terminal process exited immediately after startup. " + session.processDiagnostics()));
				return;
			}
			tab.setLaunchSucceeded(true);
			session.onExit()
				.whenComplete((process, failure) -> scheduleOnClose(tab, processExitFailure(tab, process, failure)));

			if (isClosing(tab)) {
				return;
			}
			schedulePanelUpdate();
			executeOnEDT("show terminal session " + tab.id, () -> showTerminal(tab));
		}
		catch (IOException | RuntimeException | LinkageError exception) {
			tab.setLaunchFailure(exception);
		}
	}

	private Throwable processExitFailure(TerminalTab tab, Process process, Throwable processError) {
		TerminalTabSnapshot snapshot = tab.snapshot();
		BusyBoxTerminalSession session = snapshot.session();
		boolean terminationRequested = snapshot.closeRequested()
				|| (session != null && session.isTerminationRequested());
		if (processError != null) {
			return terminationRequested ? null : processError;
		}
		if (process.exitValue() != 0 && !terminationRequested) {
			String diagnostics = session == null ? "" : " " + session.processDiagnostics();
			return new IllegalStateException(
					"Terminal session " + tab.id + " exited with status " + process.exitValue() + "." + diagnostics);
		}
		return null;
	}

	private void onLaunchTaskClosed(TerminalTab tab) {
		tab.setLaunchComplete(true);

		TerminalTabSnapshot snapshot = tab.snapshot();
		if (snapshot.launchFailure() != null || !snapshot.launchSucceeded()) {
			Throwable failure = snapshot.launchFailure() != null ? snapshot.launchFailure()
					: new IllegalStateException("BusyBox terminal session " + tab.id + " did not start.");
			scheduleOnClose(tab, failure);
			return;
		}

		if (isClosing(tab)) {
			closeSession(tab);
		}
		schedulePanelUpdate();
	}

	private void showTerminal(TerminalTab tab) {
		BusyBoxTerminalSession session = tab.snapshot().session();
		if (isClosing(tab) || !isRegistered(tab) || session == null || !session.isAlive()) {
			return;
		}
		panel.showTerminal(tab.id, session.getConnector());
		updatePanelState();
	}

	private void closeSelectedTerminal() {
		BigInteger selectedSession = panel.getSelectedSessionId();
		if (selectedSession == null || getTab(selectedSession) == null) {
			return;
		}
		requestClose(selectedSession);
	}

	private void requestClose(BigInteger sessionId) {
		TerminalTab tab = getTab(sessionId);
		if (tab == null) {
			return;
		}
		synchronized (tabsLock) {
			if (tab.snapshot().closeRequested()) {
				return;
			}
			tab.setCloseRequested(true);
		}
		schedulePanelUpdate();
		if (tab.isLaunchComplete()) {
			closeSession(tab);
		}
	}

	private void closeSession(TerminalTab tab) {
		TerminalTabSnapshot snapshot = tab.snapshot();
		BusyBoxTerminalSession session = snapshot.session();
		if (session == null || !session.isAlive()) {
			if (tab.isLaunchComplete()) {
				scheduleOnClose(tab, snapshot.launchFailure());
			}
			return;
		}

		if (tab.cleaned.get() || !tab.closeTaskStarted.compareAndSet(false, true)) {
			return;
		}
		executeTerminalTask("close session " + tab.id, () -> {
			try {
				session.closeGracefullyAndWait();
			}
			catch (RuntimeException | LinkageError exception) {
				LOGGER.error("Terminal session {} failed during graceful close.", tab.id, exception);
				session.forceTerminate();
			}
			finally {
				if (session.isAlive()) {
					LOGGER.warn("Terminal session {} is still running after graceful close escalation: {}", tab.id,
							session.watchDiagnostics());
					session.forceTerminate();
					if (session.isAlive()) {
						LOGGER.warn(
								"Detaching terminal session {} from the UI; it will be monitored until it exits: {}",
								tab.id, session.watchDiagnostics());
						WatchDead.watch("terminal session " + tab.id, session::isAlive, session::watchDiagnostics);
						session.detach();
						scheduleOnClose(tab, null);
					}
				}
			}
		});
	}

	private void scheduleOnClose(TerminalTab tab, Throwable processError) {
		if (tab.cleaned.get() || !tab.cleanupScheduled.compareAndSet(false, true)) {
			return;
		}
		executeTerminalTask("cleanup session " + tab.id, () -> onClose(tab, processError));
	}

	private void onClose(TerminalTab tab, Throwable processError) {
		if (!tab.cleaned.compareAndSet(false, true)) {
			return;
		}
		if (processError != null) {
			LOGGER.error("Terminal session {} failed.", tab.id, processError);
		}
		else {
			LOGGER.info("Terminal session {} exited.", tab.id);
		}

		try {
			BusyBoxTerminalSession session = tab.snapshot().session();
			if (session != null) {
				session.close(false);
			}
		}
		catch (RuntimeException | LinkageError exception) {
			LOGGER.error("Unable to clean up terminal session {}.", tab.id, exception);
		}
		finally {
			synchronized (tabsLock) {
				tabs.remove(tab.id);
			}
			Runnable closeCallback = getWindowCloseCallbackIfFinished();
			boolean showStartupError = !tab.snapshot().launchSucceeded()
					&& processError instanceof BusyBoxTerminalSession.FallbackStartupException;
			Runnable updateUi = () -> {
				try {
					panel.closeTerminalWidget(tab.id);
				}
				catch (RuntimeException exception) {
					LOGGER.error("Unable to close the terminal view for session {}.", tab.id, exception);
				}
				try {
					panel.removeTerminal(tab.id);
					updatePanelState();
				}
				catch (RuntimeException exception) {
					LOGGER.error("Unable to remove the terminal tab for session {}.", tab.id, exception);
				}
				if (showStartupError) {
					try {
						new ErrorPopUP(
								"The terminal could not start. BusyBox and the system shell fallback both failed.\n\n"
										+ processError.getMessage(),
								panel, ErrorTypePopUp.INTERNAL_ERROR)
							.startManaged();
					}
					catch (RuntimeException exception) {
						LOGGER.error("Unable to show the terminal startup error dialog.", exception);
					}
				}
				if (closeCallback != null) {
					closeCallback.run();
				}
			};
			executeOnEDT("update UI for terminal session " + tab.id, updateUi);
		}
	}

	@SuppressWarnings("ConvertToTryWithResources")
	private Runnable getWindowCloseCallbackIfFinished() {
		Runnable callback;
		synchronized (tabsLock) {
			if (!windowClosing || !tabs.isEmpty()) {
				return null;
			}
			callback = onAllTabsClosed;
			onAllTabsClosed = null;
		}
		// Keep the pool alive while the window is still open: closing it here on
		// every tab close would reject the cleanup tasks of the remaining tabs.
		terminalWorkers.close();
		return callback;
	}

	private void runCloseCallback() {
		Runnable callback;
		try (terminalWorkers) {
			synchronized (tabsLock) {
				callback = onAllTabsClosed;
				onAllTabsClosed = null;
			}
		}
		if (callback != null) {
			callback.run();
		}
	}

	private void schedulePanelUpdate() {
		executeOnEDT("update terminal panel", this::updatePanelState);
	}

	private void executeOnEDT(String operation, Runnable action) {
		Runnable isolatedAction = () -> {
			try {
				action.run();
			}
			catch (RuntimeException | Error failure) {
				LOGGER.error("Unable to {}.", operation, failure);
			}
		};
		try {
			threads.executeOnEDT(isolatedAction);
		}
		catch (RuntimeException exception) {
			LOGGER.error("Unable to schedule {} through ThreadMaster.", operation, exception);
			throw exception;
		}
	}

	private void executeTerminalTask(String operation, Runnable action) {
		Runnable isolatedAction = () -> {
			try {
				action.run();
			}
			catch (RuntimeException | Error failure) {
				LOGGER.error("Terminal {} task failed.", operation, failure);
			}
		};
		try {
			terminalWorkers.execute(isolatedAction);
		}
		catch (RuntimeException exception) {
			LOGGER.error("Unable to schedule terminal {} task through ThreadMaster.", operation, exception);
			throw exception;
		}
	}

	private void updatePanelState() {
		BigInteger selectedSession = panel.getSelectedSessionId();
		boolean canAdd;
		boolean canClose;
		boolean platformSupported = platform != LangPlatform.UNKNOWN;
		synchronized (tabsLock) {
			canAdd = !windowClosing && platformSupported && tabs.size() < TerminalPanel.MAX_TABS;
			TerminalTab selectedTab = selectedSession == null ? null : tabs.get(selectedSession);
			canClose = !windowClosing && selectedTab != null && !selectedTab.snapshot().closeRequested();
		}
		panel.setActionsEnabled(canAdd, canClose, platformSupported);
	}

	private boolean isRegistered(TerminalTab tab) {
		synchronized (tabsLock) {
			return tabs.get(tab.id) == tab;
		}
	}

	private boolean isClosing(TerminalTab tab) {
		synchronized (tabsLock) {
			return windowClosing || tab.snapshot().closeRequested();
		}
	}

	private TerminalTab getTab(BigInteger sessionId) {
		synchronized (tabsLock) {
			return tabs.get(sessionId);
		}
	}

	private static final class TerminalTab {

		private final BigInteger id;

		private final AtomicBoolean cleaned = new AtomicBoolean();

		private final AtomicBoolean closeTaskStarted = new AtomicBoolean();

		private final AtomicBoolean cleanupScheduled = new AtomicBoolean();

		private final AtomicReference<TerminalTabSnapshot> state = new AtomicReference<>(
				new TerminalTabSnapshot(null, null, false, false, false));

		private TerminalTab(BigInteger id) {
			this.id = id;
		}

		private boolean isLaunchComplete() {
			return state.get().launchComplete();
		}

		private TerminalTabSnapshot snapshot() {
			return state.get();
		}

		private void setSession(BusyBoxTerminalSession session) {
			state.updateAndGet(snapshot -> snapshot.withSession(session));
		}

		private void setLaunchFailure(Throwable failure) {
			state.updateAndGet(snapshot -> snapshot.withLaunchFailure(failure));
		}

		private void setLaunchSucceeded(boolean succeeded) {
			state.updateAndGet(snapshot -> snapshot.withLaunchSucceeded(succeeded));
		}

		private void setLaunchComplete(boolean complete) {
			state.updateAndGet(snapshot -> snapshot.withLaunchComplete(complete));
		}

		private void setCloseRequested(boolean requested) {
			state.updateAndGet(snapshot -> snapshot.withCloseRequested(requested));
		}

	}

	private record TerminalTabSnapshot(BusyBoxTerminalSession session, Throwable launchFailure, boolean launchSucceeded,
			boolean launchComplete, boolean closeRequested) {

		private TerminalTabSnapshot withSession(BusyBoxTerminalSession session) {
			return new TerminalTabSnapshot(session, launchFailure, launchSucceeded, launchComplete, closeRequested);
		}

		private TerminalTabSnapshot withLaunchFailure(Throwable failure) {
			return new TerminalTabSnapshot(session, failure, launchSucceeded, launchComplete, closeRequested);
		}

		private TerminalTabSnapshot withLaunchSucceeded(boolean succeeded) {
			return new TerminalTabSnapshot(session, launchFailure, succeeded, launchComplete, closeRequested);
		}

		private TerminalTabSnapshot withLaunchComplete(boolean complete) {
			return new TerminalTabSnapshot(session, launchFailure, launchSucceeded, complete, closeRequested);
		}

		private TerminalTabSnapshot withCloseRequested(boolean requested) {
			return new TerminalTabSnapshot(session, launchFailure, launchSucceeded, launchComplete, requested);
		}

	}

}
