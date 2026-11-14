package io.simpleide.ui.terminal;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.jediterm.terminal.TtyConnector;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

public final class TerminalPanel extends JPanel {

  static final int MAX_TABS = 5;
  private static final double MIN_ICON_CONTRAST = 4.5;
  private static final Color ICON_ACCENT = new Color(0x6E7781);

  private final JTabbedPane terminalTabs = new JTabbedPane();
  private final JButton addButton = new JButton();
  private final JButton closeButton = new JButton();
  private final Map<BigInteger, TerminalView> views = new LinkedHashMap<>();

  public TerminalPanel() {
    super(new BorderLayout());
    JPanel actions = new JPanel();
    actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
    Dimension buttonSize = new Dimension(38, 34);

    addButton.setIcon(createActionIcon("io/simpleide/ui/terminal/terminal-add.svg"));
    addButton.setToolTipText("Add terminal");
    addButton.setPreferredSize(buttonSize);
    addButton.setMaximumSize(buttonSize);
    addButton.getAccessibleContext().setAccessibleName("Add terminal");

    closeButton.setIcon(createActionIcon("io/simpleide/ui/terminal/terminal-close.svg"));
    closeButton.setToolTipText("Close selected terminal");
    closeButton.setPreferredSize(buttonSize);
    closeButton.setMaximumSize(buttonSize);
    closeButton.getAccessibleContext().setAccessibleName("Close selected terminal");
    closeButton.setEnabled(false);

    actions.add(addButton);
    actions.add(closeButton);
    add(terminalTabs, BorderLayout.CENTER);
    add(actions, BorderLayout.EAST);
  }

  private static FlatSVGIcon createActionIcon(String resource) {
    return new FlatSVGIcon(resource, 20, 20)
        .setColorFilter(
            new FlatSVGIcon.ColorFilter(
                (component, color) -> {
                  Color background = getIconBackground(component);
                  if (ICON_ACCENT.equals(color)) {
                    Color foreground = UIManager.getColor("Button.foreground");
                    if (foreground == null && component != null) {
                      foreground = component.getForeground();
                    }
                    return getAccessibleIconForeground(foreground, background);
                  }
                  if (Color.WHITE.equals(color)) {
                    return background;
                  }
                  return color;
                }));
  }

  static Color getAccessibleIconForeground(Color preferred, Color background) {
    Color effectiveBackground = background == null ? Color.WHITE : background;
    Color effectiveForeground = preferred == null ? Color.DARK_GRAY : preferred;
    if (contrastRatio(effectiveForeground, effectiveBackground) >= MIN_ICON_CONTRAST) {
      return effectiveForeground;
    }
    return contrastRatio(Color.BLACK, effectiveBackground)
            >= contrastRatio(Color.WHITE, effectiveBackground)
        ? Color.BLACK
        : Color.WHITE;
  }

  private static Color getIconBackground(Component component) {
    Color background = component == null ? null : component.getBackground();
    if (background == null) {
      background = UIManager.getColor("Button.background");
    }
    if (background == null) {
      background = UIManager.getColor("Panel.background");
    }
    return background == null ? Color.WHITE : background;
  }

  private static double contrastRatio(Color foreground, Color background) {
    Color opaqueBackground = composite(background, Color.WHITE);
    Color opaqueForeground = composite(foreground, opaqueBackground);
    double foregroundLuminance = relativeLuminance(opaqueForeground);
    double backgroundLuminance = relativeLuminance(opaqueBackground);
    double lighter = Math.max(foregroundLuminance, backgroundLuminance);
    double darker = Math.min(foregroundLuminance, backgroundLuminance);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static Color composite(Color color, Color background) {
    if (color.getAlpha() == 255) {
      return color;
    }
    double alpha = color.getAlpha() / 255.0;
    return new Color(
        (int) Math.round(color.getRed() * alpha + background.getRed() * (1 - alpha)),
        (int) Math.round(color.getGreen() * alpha + background.getGreen() * (1 - alpha)),
        (int) Math.round(color.getBlue() * alpha + background.getBlue() * (1 - alpha)));
  }

  private static double relativeLuminance(Color color) {
    return 0.2126 * linearize(color.getRed())
        + 0.7152 * linearize(color.getGreen())
        + 0.0722 * linearize(color.getBlue());
  }

  private static double linearize(int channel) {
    double normalized = channel / 255.0;
    return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
  }

  void setActions(Runnable addAction, Runnable closeAction) {
    addButton.addActionListener(event -> addAction.run());
    closeButton.addActionListener(event -> closeAction.run());
  }

  void setActionsEnabled(boolean canAdd, boolean canClose, boolean platformSupported) {
    addButton.setEnabled(canAdd);
    addButton.setToolTipText(
        platformSupported ? "Add terminal" : "Terminal is not supported on this platform");
    closeButton.setEnabled(canClose);
  }

  void addTerminalTab(BigInteger sessionId) {
    if (views.containsKey(sessionId)) {
      throw new IllegalArgumentException("Terminal session already exists: " + sessionId + ".");
    }
    if (views.size() >= MAX_TABS) {
      throw new IllegalStateException("A maximum of " + MAX_TABS + " terminal tabs is supported.");
    }
    TerminalView view = new TerminalView(sessionId);
    views.put(sessionId, view);
    terminalTabs.addTab(sessionId.toString(), view);
    terminalTabs.setSelectedComponent(view);
  }

  void showTerminal(BigInteger sessionId, TtyConnector connector) {
    TerminalView view = views.get(sessionId);
    if (view == null) {
      throw new IllegalStateException("No terminal tab exists for session " + sessionId + ".");
    }
    view.show(connector);
  }

  void closeTerminalWidget(BigInteger sessionId) {
    TerminalView view = views.get(sessionId);
    if (view != null) {
      view.closeWidget();
    }
  }

  void removeTerminal(BigInteger sessionId) {
    TerminalView view = views.remove(sessionId);
    if (view == null) {
      return;
    }
    view.removeWidget();
    terminalTabs.remove(view);
  }

  BigInteger getSelectedSessionId() {
    Object selected = terminalTabs.getSelectedComponent();
    for (Map.Entry<BigInteger, TerminalView> entry : views.entrySet()) {
      if (entry.getValue() == selected) {
        return entry.getKey();
      }
    }
    return null;
  }
}
