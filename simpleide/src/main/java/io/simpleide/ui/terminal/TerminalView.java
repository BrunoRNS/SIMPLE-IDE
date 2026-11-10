package io.simpleide.ui.terminal;

import java.awt.BorderLayout;
import java.math.BigInteger;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;

public final class TerminalView extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalView.class);
    private static final int COLUMNS = 80;
    private static final int ROWS = 24;

    private final BigInteger sessionId;
    private volatile ThemedTerminalWidget terminalWidget;

    TerminalView(BigInteger sessionId) {
        super(new BorderLayout());
        this.sessionId = sessionId;
    }

    @SuppressWarnings("unused")
    BigInteger getSessionId() {
        return sessionId;
    }

    void show(TtyConnector connector) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Terminal views must be updated on the EDT.");
        }
        terminalWidget = new ThemedTerminalWidget(connector);
        add(terminalWidget, BorderLayout.CENTER);
        terminalWidget.start();
        revalidate();
        repaint();
        terminalWidget.requestFocusInWindow();
    }

    void closeWidget() {
        ThemedTerminalWidget widget = terminalWidget;
        if (widget != null) {
            try {
                widget.close();
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to close terminal tab {}.", sessionId, exception);
            }
        }
    }

    void removeWidget() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Terminal views must be updated on the EDT.");
        }
        removeAll();
        terminalWidget = null;
        revalidate();
        repaint();
    }

    private static final class ThemedTerminalWidget extends JediTermWidget {

        private ThemedTerminalPanel terminalPanel;

        private ThemedTerminalWidget(TtyConnector connector) {
            super(COLUMNS, ROWS, new TerminalSettingsProvider());
            setTtyConnector(connector);
        }

        @Override
        protected com.jediterm.terminal.ui.TerminalPanel createTerminalPanel(
            SettingsProvider settingsProvider,
            StyleState styleState,
            TerminalTextBuffer textBuffer
        ) {
            terminalPanel = new ThemedTerminalPanel(settingsProvider, textBuffer, styleState);
            return terminalPanel;
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (terminalPanel != null) {
                terminalPanel.refreshFont();
            }
        }
    }

    private static final class ThemedTerminalPanel extends com.jediterm.terminal.ui.TerminalPanel {

        private ThemedTerminalPanel(
            SettingsProvider settingsProvider,
            TerminalTextBuffer textBuffer,
            StyleState styleState
        ) {
            super(settingsProvider, textBuffer, styleState);
        }

        private void refreshFont() {
            reinitFontAndResize();
            repaint();
        }
    }
}
