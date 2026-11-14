package io.simpleide.ui.terminal;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.Font;
import javax.swing.UIManager;

public final class TerminalSettingsProvider extends DefaultSettingsProvider {

  @Override
  public Font getTerminalFont() {
    Font uiFont = UIManager.getFont("TextArea.font");
    if (uiFont == null) {
      uiFont = UIManager.getFont("TextPane.font");
    }
    if (uiFont == null) {
      uiFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    }
    return new Font(Font.MONOSPACED, uiFont.getStyle(), uiFont.getSize());
  }

  @Override
  public float getTerminalFontSize() {
    return getTerminalFont().getSize2D();
  }

  @Override
  public TerminalColor getDefaultForeground() {
    return new TerminalColor(() -> getThemeColor("TextArea.foreground", java.awt.Color.WHITE));
  }

  @Override
  public TerminalColor getDefaultBackground() {
    return new TerminalColor(() -> getThemeColor("TextArea.background", java.awt.Color.BLACK));
  }

  @Override
  public TextStyle getDefaultStyle() {
    return new TextStyle(getDefaultForeground(), getDefaultBackground());
  }

  private static Color getThemeColor(String key, java.awt.Color fallback) {
    java.awt.Color color = UIManager.getColor(key);
    if (color == null) {
      color = fallback;
    }
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
  }
}
