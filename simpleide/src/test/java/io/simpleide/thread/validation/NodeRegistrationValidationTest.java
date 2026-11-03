package io.simpleide.thread.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.edt.EDTNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

class NodeRegistrationValidationTest {

    @Test
    void exposesConsistentLevelsAndAcceptsValidBuiltInNodeTypes() {
        assertEquals(0, NodeRegistrationValidation.ROOT_LEVEL);
        assertEquals(1, NodeRegistrationValidation.FIRST_NODE_LEVEL);
        assertEquals(NodeRegistrationValidation.FIRST_NODE_LEVEL, NodeRegistrationValidation.EDT_NODE_LEVEL);

        Validation<NodePattern> validation = NodeRegistrationValidation.INSTANCE;
        assertDoesNotThrow(
            () -> validation.validate(
                new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {})
            )
        );
        assertDoesNotThrow(
            () -> validation.validate(new EDTNode(() -> {}))
        );
    }

    @Test
    void rejectsNullRootsInvalidLevelsAndMismatchedBuiltInNodeTypes() {
        assertThrows(IllegalArgumentException.class, () -> NodeRegistrationValidation.validateRoot(null));
        assertThrows(
            IllegalArgumentException.class,
            () -> NodeRegistrationValidation.validateRoot(new TestPattern(null, false))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> NodeRegistrationValidation.INSTANCE.validate(null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> NodeRegistrationValidation.INSTANCE.validate(new TestPattern(0, false))
        );

        RunnableNode runnableOnEdt = new RunnableNode(
            ThreadMaster.getInstance().getROOT(),
            () -> {}
        ) {
            @Override
            public boolean isEDT() {
                return true;
            }
        };
        EDTNode edtOffEdt = new EDTNode(() -> {}) {
            @Override
            public boolean isEDT() {
                return false;
            }
        };

        assertThrows(
            IllegalArgumentException.class,
            () -> NodeRegistrationValidation.INSTANCE.validate(runnableOnEdt)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> NodeRegistrationValidation.INSTANCE.validate(edtOffEdt)
        );
    }

    private record TestPattern(Integer level, boolean edt) implements NodePattern {
        @Override
        public Runnable onStart() {
            return null;
        }

        @Override
        public Runnable onClose() {
            return null;
        }

        @Override
        public boolean isEDT() {
            return edt;
        }
    }
}
