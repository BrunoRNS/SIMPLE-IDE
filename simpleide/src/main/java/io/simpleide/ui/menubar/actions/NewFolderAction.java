package io.simpleide.ui.menubar.actions;

import java.awt.event.ActionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;

public class NewFolderAction extends ActionNode {

  private final static Logger LOGGER = LoggerFactory.getLogger(NewFileAction.class);

  public NewFolderAction() {
    super(NewFolderAction::onStartEvent);
  }

  private static void onStartEvent(ActionEvent e) {
    LOGGER.info("New folder");
  }
}
