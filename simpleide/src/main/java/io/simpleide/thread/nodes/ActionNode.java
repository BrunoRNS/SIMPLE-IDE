package io.simpleide.thread.nodes;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import io.simpleide.thread.ThreadMaster;

public class ActionNode extends EDTNode implements ActionListener {

    protected ActionEvent event;

    public ActionNode(NodePattern actual) {
        super(actual);
    }

    public ActionNode(Runnable onStart) {
        super(onStart);
    }

    public ActionNode(Runnable onStart, Runnable onClose) {
        super(onStart, onClose);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        this.event = e;
        ThreadMaster.getInstance().register(this);
    }
    
}
