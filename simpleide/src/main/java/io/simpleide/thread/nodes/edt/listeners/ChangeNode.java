package io.simpleide.thread.nodes.edt.listeners;

import java.util.function.Consumer;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

public class ChangeNode extends EDTNode implements ChangeListener {

    protected ChangeEvent event;
    protected Consumer<ChangeEvent> c_onStart;
    protected Consumer<ChangeEvent> c_onClose;

    public ChangeNode(Consumer<ChangeEvent> onStart) {
        super();
        this.c_onStart = onStart;
    }

    public ChangeNode(
        Consumer<ChangeEvent> onStart,
        Consumer<ChangeEvent> onClose
    ) {
        super();
        this.c_onStart = onStart;
        this.c_onClose = onClose;
    }

    @Override
    public Runnable onStart() {
        return new ChangeNodeStartRunnable(this);
    }

    @Override 
    public Runnable onClose() {
        return new ChangeNodeCloseRunnable(this);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        this.event = e;
        this.start = this.onStart();
        this.close = this.onClose();
        ThreadMaster.getInstance().register(this);
    }
    
}

class ChangeNodeStartRunnable implements Runnable {

    private final ChangeNode node;

    public ChangeNodeStartRunnable(ChangeNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        if (this.node.c_onStart != null) {
            this.node.c_onStart.accept(this.node.event);
        }
    }
    
}

class ChangeNodeCloseRunnable implements Runnable {

    private final ChangeNode node;

    public ChangeNodeCloseRunnable(ChangeNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        if (this.node.c_onClose != null) {
            this.node.c_onClose.accept(this.node.event);
        }
    }
    
}
