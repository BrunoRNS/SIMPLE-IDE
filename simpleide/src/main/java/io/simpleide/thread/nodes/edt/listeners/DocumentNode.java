package io.simpleide.thread.nodes.edt.listeners;

import java.util.Objects;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

public final class DocumentNode extends EDTNode implements DocumentListener, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentNode.class);

    private final Runnable onChange;
    private volatile boolean closed;

    public DocumentNode(Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
        dispatchChange();
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
        dispatchChange();
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
        dispatchChange();
    }

    private void dispatchChange() {
        if (closed) {
            return;
        }
        try {
            ThreadMaster.getInstance().executeOnEDT(() -> {
                if (!closed) {
                    onChange.run();
                }
            });
        } catch (RuntimeException error) {
            LOGGER.error("Unable to schedule editor document change.", error);
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
