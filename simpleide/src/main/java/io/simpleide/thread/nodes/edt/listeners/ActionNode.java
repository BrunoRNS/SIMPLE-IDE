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
        return new ActionNodeStartRunnable(this, event);
    }

    @Override
    public Runnable onClose() {
        return new ActionNodeCloseRunnable(this, event);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        this.event = e;
        ThreadMaster.getInstance().executeOnEDT(this.onStart(), this.onClose());
    }
    
}

class ActionNodeStartRunnable implements Runnable {

    private final ActionNode node;
    private final ActionEvent event;

    public ActionNodeStartRunnable(ActionNode node, ActionEvent event) {
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

class ActionNodeCloseRunnable implements Runnable {

    private final ActionNode node;
    private final ActionEvent event;

    public ActionNodeCloseRunnable(ActionNode node, ActionEvent event) {
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
