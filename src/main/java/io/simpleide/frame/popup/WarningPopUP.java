package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;

@Mappeable
public class WarningPopUP extends PopUP implements MappeableSupport {

  public WarningPopUP(String title, String message, JComponent parent, Object option) {
    super(parent, title, message, JOptionPane.WARNING_MESSAGE, LoadIcon.loadingIcon("warning.png"), option);
  }

  public WarningPopUP(String title, String message) {
    this(title, message, null, "Ok");
  }

  public WarningPopUP(String title, String message, Object option) {
    this(title, message, null, option);
  }

  public WarningPopUP(String title, String message, JComponent parent) {
    this(title, message, parent, "Ok");
  }

  @Override
  public Map<String, Object> getMap() {
    return getMaping();
  }

  @Override
  public void setMap(Map<String, Object> m) {
    setMaping(m);
  }

}
