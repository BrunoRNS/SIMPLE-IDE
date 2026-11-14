package io.simpleide.thread.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.edt.EDTNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import org.junit.jupiter.api.Test;

class NodeRegistrationValidationTest {

  @Test
  void exposesConsistentLevelsAndAcceptsValidBuiltInNodeTypes() {
    assertEquals(0, NodeRegistrationValidation.ROOT_LEVEL);
    assertEquals(1, NodeRegistrationValidation.FIRST_NODE_LEVEL);
    assertEquals(
        NodeRegistrationValidation.FIRST_NODE_LEVEL, NodeRegistrationValidation.EDT_NODE_LEVEL);

    Validation<NodePattern> validation = NodeRegistrationValidation.INSTANCE;
    assertDoesNotThrow(
        () ->
            validation.validate(new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {})));
    assertDoesNotThrow(() -> validation.validate(new EDTNode(() -> {})));
  }

  @Test
  void rejectsNullRootsInvalidLevelsAndMismatchedBuiltInNodeTypes() {
    assertThatThrownBy(() -> NodeRegistrationValidation.validateRoot(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NodeRegistrationValidation.validateRoot(new TestPattern(null, false)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NodeRegistrationValidation.INSTANCE.validate(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> NodeRegistrationValidation.INSTANCE.validate(new TestPattern(0, false)))
        .isInstanceOf(IllegalArgumentException.class);

    RunnableNode runnableOnEdt =
        new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {}) {
          @Override
          public boolean isEDT() {
            return true;
          }
        };
    EDTNode edtOffEdt =
        new EDTNode(() -> {}) {
          @Override
          public boolean isEDT() {
            return false;
          }
        };

    assertThatThrownBy(() -> NodeRegistrationValidation.INSTANCE.validate(runnableOnEdt))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NodeRegistrationValidation.INSTANCE.validate(edtOffEdt))
        .isInstanceOf(IllegalArgumentException.class);
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
