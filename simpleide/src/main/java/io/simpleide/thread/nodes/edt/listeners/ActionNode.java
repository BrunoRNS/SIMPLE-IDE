package io.simpleide.thread.nodes.edt.listeners;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

public class ActionNode extends EDTNode implements ActionListener {

    protected ActionEvent event;
    protected Consumer<ActionEvent> c_onStart;
    protected Consumer<ActionEvent> c_onClose;

    public ActionNode(Consumer<ActionEvent> onStart) {
        super();
        this.c_onStart = onStart;
    }

    public ActionNode(
        Consumer<ActionEvent> onStart,
        Consumer<ActionEvent> onClose
    ) {
        super();
        this.c_onStart = onStart;
        this.c_onClose = onClose;
    }

    @Override
    public Runnable onStart() {
        return new ActionNodeStartRunnable(this);
    }

    @Override 
    public Runnable onClose() {
          return new ActionNodeCloseRunnable(this);
     }

    @Override
    public void actionPerformed(ActionEvent e) {
        this.event = e;
        this.start = this.onStart();
        this.close = this.onClose();
        ThreadMaster.getInstance().register(this);
    }
    
}

class ActionNodeStartRunnable implements Runnable {

    private final ActionNode node;

    public ActionNodeStartRunnable(ActionNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        if (this.node.c_onStart != null) {
            this.node.c_onStart.accept(this.node.event);
        }
    }
    
}

class ActionNodeCloseRunnable implements Runnable {

    private final ActionNode node;

    public ActionNodeCloseRunnable(ActionNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        if (this.node.c_onClose != null) {
            this.node.c_onClose.accept(this.node.event);
        }
    }
    
}
