package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;

@Mappeable
public class InfoPopUP extends PopUP implements MappeableSupport {

  public InfoPopUP(String title, String message, Object option) {
    this(title, message, null, option);
  }

  public InfoPopUP(String title, String message, JComponent parent, Object option) {

    super(parent, title, message, JOptionPane.INFORMATION_MESSAGE, LoadIcon.loadingIcon("info.png"), option);
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
