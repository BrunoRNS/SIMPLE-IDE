package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;

@Mappeable
public class SuccessPopUP extends PopUP implements MappeableSupport {

  public SuccessPopUP(String title, String message, JComponent parent, Object option) {
    super(parent, title, message, JOptionPane.INFORMATION_MESSAGE, LoadIcon.loadingIcon("sucess.png"), option);
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
