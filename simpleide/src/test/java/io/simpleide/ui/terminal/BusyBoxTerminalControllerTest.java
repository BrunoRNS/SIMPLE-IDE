package io.simpleide.ui.terminal;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.language.config.LangPlatform;

class BusyBoxTerminalControllerTest {

    @Test
    void keepsMultipleSessionsAliveAfterTheirLaunchTasksComplete() throws Exception {
        LangPlatform platform = LangPlatform.getCurrentPlatform();
        assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform");

        TerminalPanel panel = new TerminalPanel();
        BusyBoxTerminalController controller = new BusyBoxTerminalController(panel, platform);
        SwingUtilities.invokeAndWait(() -> {
            JButton addButton = findButtons(panel).get(0);
            addButton.doClick();
            addButton.doClick();
        });

        await("Both terminal launch tasks should finish without stopping their BusyBox processes")
            .during(3, TimeUnit.SECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .until(() -> {
                List<TerminalState> states = terminalStates(controller);
                return states.size() == 2
                    && states.stream().allMatch(state -> state.launchComplete && state.session != null
                        && state.session.isAlive());
            });

        assertEquals(2, terminalStates(controller).size());

        CountDownLatch closed = new CountDownLatch(1);
        controller.onWindowClosing(closed::countDown);
        assertTrue(closed.await(10, TimeUnit.SECONDS));
    }

    @Test
    void disablesTerminalCreationWhenPlatformIsUnsupported() throws Exception {
        TerminalPanel panel = new TerminalPanel();
        BusyBoxTerminalController controller = new BusyBoxTerminalController(panel, LangPlatform.UNKNOWN);

        SwingUtilities.invokeAndWait(() -> {
            JButton addButton = findButtons(panel).get(0);
            assertFalse(addButton.isEnabled());
            assertEquals("Terminal is not supported on this platform", addButton.getToolTipText());
            addButton.doClick();
        });

        assertTrue(terminalStates(controller).isEmpty());
        assertEquals(0, terminalTabCount(panel));
    }

    private static List<JButton> findButtons(Component component) {
        List<JButton> buttons = new ArrayList<>();
        collectButtons(component, buttons);
        return buttons;
    }

    private static void collectButtons(Component component, List<JButton> buttons) {
        if (component instanceof JButton button) {
            buttons.add(button);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtons(child, buttons);
            }
        }
    }

    private static int terminalTabCount(TerminalPanel panel) {
        AtomicInteger tabCount = new AtomicInteger();
        try {
            SwingUtilities.invokeAndWait(() -> tabCount.set(findTabbedPane(panel).getTabCount()));
        } catch (Exception exception) {
            throw new AssertionError("Unable to read terminal tab count.", exception);
        }
        return tabCount.get();
    }

    private static JTabbedPane findTabbedPane(Component component) {
        if (component instanceof JTabbedPane tabbedPane) {
            return tabbedPane;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTabbedPane result = findTabbedPane(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static List<TerminalState> terminalStates(BusyBoxTerminalController controller) {
        try {
            Field tabsField = BusyBoxTerminalController.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);
            Map<?, ?> tabs = (Map<?, ?>) tabsField.get(controller);
            List<TerminalState> states = new ArrayList<>();
            for (Object tab : tabs.values()) {
                Field sessionField = tab.getClass().getDeclaredField("session");
                Field launchCompleteField = tab.getClass().getDeclaredField("launchComplete");
                sessionField.setAccessible(true);
                launchCompleteField.setAccessible(true);
                states.add(new TerminalState(
                    (BusyBoxTerminalSession) sessionField.get(tab),
                    launchCompleteField.getBoolean(tab)
                ));
            }
            return states;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect terminal session state.", exception);
        }
    }

    private record TerminalState(BusyBoxTerminalSession session, boolean launchComplete) {}
}
