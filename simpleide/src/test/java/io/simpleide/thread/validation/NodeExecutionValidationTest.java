package io.simpleide.thread.validation;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.nodes.NodePattern;

class NodeExecutionValidationTest {

    @Test
    void acceptsOnlyExecutionOnTheConfiguredThread() throws Exception {
        NodePattern workerNode = new TestPattern(false);
        NodePattern edtNode = new TestPattern(true);

        NodeExecutionValidation.INSTANCE.validate(workerNode);
        assertThatThrownBy(() -> NodeExecutionValidation.INSTANCE.validate(edtNode))
            .isInstanceOf(IllegalStateException.class);

        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                NodeExecutionValidation.INSTANCE.validate(edtNode);
            } catch (RuntimeException exception) {
                failure.set(exception);
            }
        });
        assertNull(failure.get());

        assertThatThrownBy(
            () -> SwingUtilities.invokeAndWait(
                () -> NodeExecutionValidation.INSTANCE.validate(workerNode)
            )
        )
            .isInstanceOf(InvocationTargetException.class)
            .cause()
            .isInstanceOf(IllegalStateException.class);
    }

    private record TestPattern(boolean edt) implements NodePattern {
        @Override
        public Runnable onStart() {
            return null;
        }

        @Override
        public Runnable onClose() {
            return null;
        }

        @Override
        public Integer level() {
            return 1;
        }

        @Override
        public boolean isEDT() {
            return edt;
        }
    }
}
