package io.simpleide.thread.nodes;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ActionNode extends Node implements ActionListener {

    public ActionNode(NodePattern superNode, NodePattern actual) {
        super(superNode, actual);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Runnable onStart() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Runnable onClose() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Integer level() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
