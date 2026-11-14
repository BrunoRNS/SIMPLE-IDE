package io.simpleide.frame.popup;

import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

public class InputPopUP extends InfoPopUP {

  private final Consumer<String> onInput;

  public InputPopUP(String title, String message, JComponent parent, Consumer<String> onInput) {
    super(title, message, parent, "Ok");
    this.onInput = onInput;
    jp.setWantsInput(true);
  }

  @Override
  protected void onDialogClosed() {
    Object input = jp.getInputValue();
    boolean dismissed =
        jp.getValue() == JOptionPane.UNINITIALIZED_VALUE
            || input == JOptionPane.UNINITIALIZED_VALUE;
    onInput.accept(!dismissed && input instanceof String value ? value : null);
  }
}
