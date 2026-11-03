package io.simpleide.ui.menubar.actions;

import java.awt.event.ActionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;

public class NewFileAction extends ActionNode {

  private final static Logger LOGGER = LoggerFactory.getLogger(NewFileAction.class);

  public NewFileAction() {
    super((e) -> onStart(e));
  }

  private static void onStart(ActionEvent e) {
    LOGGER.info("Create new file");
  }
}
