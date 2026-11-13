package io.simpleide.ui.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;

public class ButtonExit extends JButton {
  private static final int ICON_SIZE = 12;
  @SuppressWarnings("unused")
  private final EditorTab tab;
  private final Icon iconX = new TabIcon(false);
  private final Icon iconDot = new TabIcon(true);

  public ButtonExit(EditorTab tab) {
    super();
    this.tab = tab;
    setIcon(iconX);
    setToolTipText("Close " + tab.getName());
    addActionListener(new ActionNode(e -> {
      EditorManager editorManager = EditorManager.getInstance();
      if (editorManager != null) {
        editorManager.closeTab(tab);
      }
    }));
    setBorder(BorderFactory.createEmptyBorder());
    setContentAreaFilled(false);
    setFocusable(false);
  }

  public void setEdited() {
    setIcon(iconDot);
  }

  public void setNotEdited() {
    setIcon(iconX);
  }

  private static final class TabIcon implements Icon {
    private final boolean edited;

    private TabIcon(boolean edited) {
      this.edited = edited;
    }

    @Override
    public int getIconWidth() {
      return ICON_SIZE;
    }

    @Override
    public int getIconHeight() {
      return ICON_SIZE;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g2 = (Graphics2D) graphics.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (edited) {
          Color accent = UIManager.getColor("Actions.Yellow");
          g2.setColor(accent != null ? accent : new Color(0xD9A441));
          g2.fillOval(x + 6, y + 6, 6, 6);
        } else {
          Color foreground = component.getForeground();
          g2.setColor(foreground != null ? foreground : Color.GRAY);
          g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine(x + 3, y + 3, x + 9, y + 9);
          g2.drawLine(x + 9, y + 3, x + 3, y + 9);
        }
      } finally {
        g2.dispose();
      }
    }
  }
}
