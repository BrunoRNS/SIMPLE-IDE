package io.simpleide.ui.terminal;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.language.config.LangPlatform;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

public final class BusyBoxTerminalController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusyBoxTerminalController.class);

    private final ThreadMaster threads = ThreadMaster.getInstance();
    private final TerminalPanel panel;
    private final LangPlatform platform;
    private final Object tabsLock = new Object();
    private final Map<BigInteger, TerminalTab> tabs = new LinkedHashMap<>();

    private BigInteger nextSessionId = BigInteger.ZERO;
    private boolean windowClosing;
    private Runnable onAllTabsClosed;

    public BusyBoxTerminalController(TerminalPanel panel) {
        this(panel, LangPlatform.getCurrentPlatform());
    }

    BusyBoxTerminalController(TerminalPanel panel, LangPlatform platform) {
        this.panel = panel;
        this.platform = platform;
        panel.setActions(this::addTerminal, this::closeSelectedTerminal);
        schedulePanelUpdate();
    }

    public void onWindowClosing(Runnable onClosed) {
        List<TerminalTab> openTabs;
        synchronized (tabsLock) {
            windowClosing = true;
            onAllTabsClosed = onClosed;
            openTabs = new ArrayList<>(tabs.values());
            openTabs.forEach(tab -> {
                tab.closeRequested = true;
                tab.forceExitRequested = true;
            });
        }
        schedulePanelUpdate();
        if (openTabs.isEmpty()) {
            runCloseCallback();
            return;
        }
        openTabs.stream()
            .filter(TerminalTab::isLaunchComplete)
            .forEach(this::forceExit);
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

        RunnableNode launchTask = new RunnableNode(
            () -> launch(tab),
            () -> onLaunchTaskClosed(tab)
        );
        tab.launchTask = launchTask;
        try {
            threads.register(launchTask);
        } catch (RuntimeException exception) {
            tab.launchFailure = exception;
            tab.launchComplete = true;
            scheduleOnClose(tab, exception);
        }
    }

    @SuppressWarnings("resource")
    private void launch(TerminalTab tab) {
        BusyBoxTerminalSession session = new BusyBoxTerminalSession(platform);
        tab.session = session;
        try {
            session.start();
            if (!session.isAlive()) {
                tab.launchFailure = new IllegalStateException(
                    "The terminal process exited immediately after startup. " + session.processDiagnostics()
                );
                return;
            }
            tab.launchSucceeded = true;
            session.onExit().whenComplete((process, failure) ->
                scheduleOnClose(tab, processExitFailure(tab, process, failure)));

            if (isClosing(tab)) {
                return;
            }
            schedulePanelUpdate();
            threads.executeOnEDT(() -> showTerminal(tab));
        } catch (IOException | RuntimeException | LinkageError exception) {
            tab.launchFailure = exception;
        }
    }

    private Throwable processExitFailure(
        TerminalTab tab,
        Process process,
        Throwable processError
    ) {
        BusyBoxTerminalSession session = tab.session;
        boolean terminationRequested = tab.forceExitRequested
            || (session != null && session.isTerminationRequested());
        if (processError != null) {
            return terminationRequested ? null : processError;
        }
        if (process.exitValue() != 0 && !terminationRequested) {
            String diagnostics = session == null ? "" : " " + session.processDiagnostics();
            return new IllegalStateException(
                "Terminal session " + tab.id + " exited with status " + process.exitValue() + "."
                    + diagnostics
            );
        }
        return null;
    }

    private void onLaunchTaskClosed(TerminalTab tab) {
        tab.launchComplete = true;
        removeLaunchTask(tab);

        if (tab.launchFailure != null || !tab.launchSucceeded) {
            Throwable failure = tab.launchFailure != null
                ? tab.launchFailure
                : new IllegalStateException("BusyBox terminal session " + tab.id + " did not start.");
            scheduleOnClose(tab, failure);
            return;
        }

        if (isClosing(tab)) {
            forceExit(tab);
        }
        schedulePanelUpdate();
    }

    private void showTerminal(TerminalTab tab) {
        if (isClosing(tab) || !isRegistered(tab) || tab.session == null || !tab.session.isAlive()) {
            return;
        }
        panel.showTerminal(tab.id, tab.session.getConnector());
        updatePanelState();
    }

    private void closeSelectedTerminal() {
        BigInteger selectedSession = panel.getSelectedSessionId();
        if (selectedSession == null || getTab(selectedSession) == null) {
            return;
        }
        try {
            new ForceCloseConfirmationPopUP(panel, confirmed -> {
                if (confirmed) {
                    requestForceExit(selectedSession);
                }
            }).startManaged();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to open the terminal close confirmation.", exception);
        }
    }

    private void requestForceExit(BigInteger sessionId) {
        TerminalTab tab = getTab(sessionId);
        if (tab == null) {
            return;
        }
        synchronized (tabsLock) {
            if (tab.closeRequested) {
                return;
            }
            tab.closeRequested = true;
            tab.forceExitRequested = true;
        }
        schedulePanelUpdate();
        if (tab.isLaunchComplete()) {
            forceExit(tab);
        }
    }

    private void forceExit(TerminalTab tab) {
        BusyBoxTerminalSession session = tab.session;
        if (session == null || !session.isAlive()) {
            if (tab.isLaunchComplete()) {
                scheduleOnClose(tab, tab.launchFailure);
            }
            return;
        }

        synchronized (tab) {
            if (tab.forceTask != null || tab.cleaned.get()) {
                return;
            }
            tab.forceTask = new RunnableNode(
                session::forceTerminateAndWait,
                () -> onForceTaskClosed(tab)
            );
        }
        try {
            threads.register(tab.forceTask);
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to schedule forced exit for terminal session {}.", tab.id, exception);
            session.forceTerminate();
        }
    }

    private void onForceTaskClosed(TerminalTab tab) {
        RunnableNode forceTask;
        synchronized (tab) {
            forceTask = tab.forceTask;
            tab.forceTask = null;
        }
        if (forceTask != null) {
            threads.removeNodeCascade(forceTask);
        }
        BusyBoxTerminalSession session = tab.session;
        if (session != null && session.isAlive()) {
            LOGGER.warn("Terminal session {} is still running after a forced exit request.", tab.id);
            session.forceTerminate();
        }
    }

    private void scheduleOnClose(TerminalTab tab, Throwable processError) {
        RunnableNode cleanupTask;
        synchronized (tab) {
            if (tab.cleaned.get() || tab.cleanupTask != null) {
                return;
            }
            cleanupTask = new RunnableNode(
                () -> onClose(tab, processError),
                () -> removeCleanupTask(tab)
            );
            tab.cleanupTask = cleanupTask;
        }
        try {
            threads.register(cleanupTask);
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to schedule cleanup for terminal session {}.", tab.id, exception);
            onClose(tab, processError == null ? exception : processError);
        }
    }

    private void onClose(TerminalTab tab, Throwable processError) {
        if (!tab.cleaned.compareAndSet(false, true)) {
            return;
        }
        if (processError != null) {
            LOGGER.error("Terminal session {} failed.", tab.id, processError);
        } else {
            LOGGER.info("Terminal session {} exited.", tab.id);
        }

        try {
            if (tab.session != null) {
                tab.session.close(tab.forceExitRequested);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to clean up terminal session {}.", tab.id, exception);
        }

        synchronized (tabsLock) {
            tabs.remove(tab.id);
        }
        Runnable closeCallback = getWindowCloseCallbackIfFinished();
        boolean showStartupError = !tab.launchSucceeded
            && processError instanceof BusyBoxTerminalSession.FallbackStartupException;
        Runnable updateUi = () -> {
            try {
                panel.closeTerminalWidget(tab.id);
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to close the terminal view for session {}.", tab.id, exception);
            }
            try {
                panel.removeTerminal(tab.id);
                updatePanelState();
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to remove the terminal tab for session {}.", tab.id, exception);
            }
            if (showStartupError) {
                try {
                    new ErrorPopUP(
                        "The terminal could not start. BusyBox and the system shell fallback both failed.\n\n"
                            + processError.getMessage(),
                        panel,
                        ErrorTypePopUp.INTERNAL_ERROR
                    ).startManaged();
                } catch (RuntimeException exception) {
                    LOGGER.error("Unable to show the terminal startup error dialog.", exception);
                }
            }
            if (closeCallback != null) {
                closeCallback.run();
            }
        };
        try {
            threads.executeOnEDT(updateUi);
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to update the terminal UI after session {} closed.", tab.id, exception);
            SwingUtilities.invokeLater(updateUi);
        }
    }

    private void removeLaunchTask(TerminalTab tab) {
        RunnableNode task = tab.launchTask;
        if (task != null) {
            threads.removeNodeCascade(task);
            tab.launchTask = null;
        }
    }

    private void removeCleanupTask(TerminalTab tab) {
        RunnableNode task;
        synchronized (tab) {
            task = tab.cleanupTask;
            tab.cleanupTask = null;
        }
        if (task != null) {
            threads.removeNodeCascade(task);
        }
    }

    private Runnable getWindowCloseCallbackIfFinished() {
        synchronized (tabsLock) {
            if (!windowClosing || !tabs.isEmpty()) {
                return null;
            }
            Runnable callback = onAllTabsClosed;
            onAllTabsClosed = null;
            return callback;
        }
    }

    private void runCloseCallback() {
        Runnable callback;
        synchronized (tabsLock) {
            callback = onAllTabsClosed;
            onAllTabsClosed = null;
        }
        if (callback != null) {
            callback.run();
        }
    }

    private void schedulePanelUpdate() {
        try {
            threads.executeOnEDT(this::updatePanelState);
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to schedule a terminal UI update.", exception);
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
            canClose = !windowClosing && selectedTab != null && !selectedTab.closeRequested;
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
            return windowClosing || tab.closeRequested;
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
        private volatile BusyBoxTerminalSession session;
        private volatile RunnableNode launchTask;
        private volatile RunnableNode forceTask;
        private volatile RunnableNode cleanupTask;
        private volatile Throwable launchFailure;
        private volatile boolean launchSucceeded;
        private volatile boolean launchComplete;
        private volatile boolean closeRequested;
        private volatile boolean forceExitRequested;

        private TerminalTab(BigInteger id) {
            this.id = id;
        }

        private boolean isLaunchComplete() {
            return launchComplete;
        }
    }
}
