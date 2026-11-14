package io.simpleide.thread.nodes.edt.listeners;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;
import java.util.function.Consumer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ChangeNode extends EDTNode implements ChangeListener {

  protected ChangeEvent event;
  protected Consumer<ChangeEvent> c_onStart;
  protected Consumer<ChangeEvent> c_onClose;

  public ChangeNode(Consumer<ChangeEvent> onStart) {
    super();
    this.c_onStart = onStart;
  }

  public ChangeNode(Consumer<ChangeEvent> onStart, Consumer<ChangeEvent> onClose) {
    super();
    this.c_onStart = onStart;
    this.c_onClose = onClose;
  }

  @Override
  public Runnable onStart() {
    return new ChangeNodeStartRunnable(this, event);
  }

  @Override
  public Runnable onClose() {
    return new ChangeNodeCloseRunnable(this, event);
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    this.event = e;
    ThreadMaster.getInstance().executeOnEDT(this.onStart(), this.onClose());
  }
}

class ChangeNodeStartRunnable implements Runnable {

  private final ChangeNode node;
  private final ChangeEvent event;

  public ChangeNodeStartRunnable(ChangeNode node, ChangeEvent event) {
    this.node = node;
    this.event = event;
  }

  @Override
  public void run() {
    if (this.node.c_onStart != null) {
      this.node.c_onStart.accept(this.event);
    }
  }
}

class ChangeNodeCloseRunnable implements Runnable {

  private final ChangeNode node;
  private final ChangeEvent event;

  public ChangeNodeCloseRunnable(ChangeNode node, ChangeEvent event) {
    this.node = node;
    this.event = event;
  }

  @Override
  public void run() {
    if (this.node.c_onClose != null) {
      this.node.c_onClose.accept(this.event);
    }
  }
}
