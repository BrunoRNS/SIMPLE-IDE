package io.simpleide.frame.menubar.actions;

import java.awt.Font;
import java.awt.Window;
import java.util.Enumeration;

import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.FontUIResource;

public class FontSizeAction implements ChangeListener {
  @Override
  public void stateChanged(ChangeEvent e) {
    JSpinner source = (JSpinner) e.getSource();

    UIDefaults defaults = UIManager.getDefaults();
    for (Enumeration<?> keys = defaults.keys(); keys.hasMoreElements();) {
      Object key = keys.nextElement();
      Object value = defaults.get(key);

      if (value instanceof FontUIResource) {
        FontUIResource fontUi = (FontUIResource) value;
        Font newFont = new FontUIResource(
            fontUi.getFamily(),
            fontUi.getStyle(),
            (int) source.getValue());
        UIManager.put(key, newFont);
      }
    }
    for (Window window : Window.getWindows()) {
      SwingUtilities.updateComponentTreeUI(window);
    }
  }
}
