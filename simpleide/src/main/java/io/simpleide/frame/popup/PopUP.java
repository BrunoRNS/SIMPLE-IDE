package io.simpleide.frame.popup;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

public abstract class PopUP implements Runnable {

  protected volatile boolean on = false;
  protected volatile String title;

  protected JComponent parent = null;
  protected JDialog jd = null;
  protected JOptionPane jp;
  protected final Object[] options = {"Ok"};

  public PopUP(
      JComponent parent,
      String title,
      Object message,
      int messageType,
      int optionType,
      Icon icon,
      Object initialValue) {

    this.parent = parent;
    this.title = title;
    this.jp = new JOptionPane(message, messageType, optionType, icon, options, initialValue);
  }

  public PopUP(
      JComponent parent,
      String title,
      Object message,
      int messageType,
      Icon icon,
      Object initialValue) {
    this(parent, title, message, messageType, JOptionPane.DEFAULT_OPTION, icon, initialValue);
  }

  public String getTitle() {
    return this.title;
  }

  public void setTitle(String s) {
    this.title = s;
  }

  public JDialog getDialog() {
    return this.jd;
  }

  public void setDialog(JDialog jd) {
    this.jd = jd;
  }

  @Override
  public void run() throws NullPointerException, IllegalStateException {
    startManaged();
  }

  public NodePattern startManaged() {
    synchronized (this) {
      if (this.jp == null) {
        throw new NullPointerException("JOptionPane can't be null!");
      }
      if (this.on) {
        throw new IllegalStateException("PopUP is already on!");
      }
      this.on = true;
    }

    try {
      return ThreadMaster.getInstance()
          .executeOnEDT(
              this::showDialog,
              () -> {
                synchronized (PopUP.this) {
                  PopUP.this.on = false;
                }
              });
    } catch (RuntimeException | Error exception) {
      synchronized (this) {
        this.on = false;
      }
      throw exception;
    }
  }

  private void showDialog() {
    if (this.jd == null) {
      this.jd = jp.createDialog(this.parent, this.title);
    }
    try {
      this.jd.setVisible(true);
    } finally {
      onDialogClosed();
    }
  }

  protected void onDialogClosed() {}

  @Override
  public String toString() {

    StringBuilder sb = new StringBuilder();
    sb.append("The PopUP is on? ").append(this.on).append("\n");
    sb.append("Class ").append(this.title).append(" Data:\n");

    Map<String, Object> m = getMap();
    if (m == null) {
      sb.append("null\n");
    } else {
      m.forEach(
          (name, obj) -> {
            sb.append(name).append(" : ").append(obj.toString()).append("\n");
          });
    }

    return sb.toString();
  }

  public abstract Map<String, Object> getMap();

  public abstract void setMap(Map<String, Object> m);
}
