package io.simpleide.ui.editor;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.InfoPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.treefile.File;

final class EditorActions {
  private static final Logger LOGGER = LoggerFactory.getLogger(EditorActions.class);

  private final EditorManager editorManager;

  EditorActions(EditorManager editorManager) {
    this.editorManager = editorManager;
  }

  void configure(EditorTab tab) {
    Editor editor = tab.getEditor();
    bindEditorAction(
        editor,
        "simpleide.save",
        KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
        () -> editorManager.saveTab(tab));
    bindEditorAction(
        editor,
        "simpleide.goToDefinition",
        KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK),
        () -> goToDefinition(tab));
    bindEditorAction(
        editor,
        "simpleide.goToDefinitionF12",
        KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0),
        () -> goToDefinition(tab));
    editor.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        if (event.getButton() != MouseEvent.BUTTON1 || !event.isControlDown()) {
          return;
        }
        int offset = editor.viewToModel2D(event.getPoint());
        if (offset >= 0 && offset <= editor.getDocument().getLength()) {
          editor.setCaretPosition(offset);
          goToDefinition(tab);
        }
      }
    });
  }

  private void bindEditorAction(Editor editor, String name, KeyStroke keyStroke, Runnable action) {
    editor.getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, name);
    editor.getActionMap().put(name, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent event) {
        action.run();
      }
    });
  }

  @SuppressWarnings("")
  private void goToDefinition(EditorTab sourceTab) {
    sourceTab.definitionAtCaret().whenComplete((definitions, error) -> {
      if (error != null) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
            ? error.getCause()
            : error;
        ThreadMaster.getInstance().executeOnEDT(() -> {
          if (cause instanceof IllegalStateException) {
            showInformation(sourceTab, "The language server is not connected for this file.");
          } else {
            LOGGER.error("Unable to find definition in {}.", sourceTab.getFile().getPath(), cause);
            if (cause != null) {
              new ErrorPopUP(
                "Unable to find definition: " + cause.getMessage(),
                sourceTab,
                ErrorTypePopUp.INTERNAL_ERROR).startManaged();
            } else {
              new ErrorPopUP(
                "Unable to find definition for cause NULL",
                sourceTab,
                ErrorTypePopUp.INTERNAL_ERROR).startManaged();
            }
          }
        });
        return;
      }

      DefinitionTarget target = getFirstDefinition(definitions);
      ThreadMaster.getInstance().executeOnEDT(() -> navigateToDefinition(sourceTab, target));
    });
  }

  @SuppressWarnings("")
  private DefinitionTarget getFirstDefinition(
      Either<List<? extends Location>, List<? extends LocationLink>> definitions) {
    if (definitions == null) {
      return null;
    }
    if (definitions.isLeft()) {
      List<? extends Location> locations = definitions.getLeft();
      if (locations == null || locations.isEmpty()) {
        return null;
      }
      Location location = locations.get(0);
      if (location == null) {
        return null;
      }
      return new DefinitionTarget(location.getUri(), location.getRange());
    }

    List<? extends LocationLink> links = definitions.getRight();
    if (links == null || links.isEmpty()) {
      return null;
    }
    LocationLink link = links.get(0);
    if (link == null) {
      return null;
    }
    Range range = link.getTargetSelectionRange() == null
        ? link.getTargetRange()
        : link.getTargetSelectionRange();
    return new DefinitionTarget(link.getTargetUri(), range);
  }

  private void navigateToDefinition(EditorTab sourceTab, DefinitionTarget target) {
    if (target == null || target.uri() == null) {
      showInformation(sourceTab, "No definition was found at the caret.");
      return;
    }

    try {
      URI uri = URI.create(target.uri());
      if (!"file".equalsIgnoreCase(uri.getScheme())) {
        showInformation(sourceTab, "The definition is not a local file.");
        return;
      }
      Path path = Path.of(uri).toAbsolutePath().normalize();
      EditorTab targetTab = editorManager.getOpenedEditor(path);
      if (targetTab == null) {
        editorManager.openFile(new File(path));
        targetTab = editorManager.getOpenedEditor(path);
      }
      if (targetTab != null) {
        editorManager.selectEditorTab(targetTab);
        targetTab.goToRange(target.range());
      }
    } catch (IllegalArgumentException | IOException error) {
      LOGGER.error("Unable to open definition target {}.", target.uri(), error);
      new ErrorPopUP(
          "Unable to open definition: " + error.getMessage(),
          sourceTab,
          ErrorTypePopUp.INTERNAL_ERROR).startManaged();
    }
  }

  private void showInformation(EditorTab sourceTab, String message) {
    new InfoPopUP("Go to Definition", message, sourceTab).startManaged();
  }

  private record DefinitionTarget(String uri, Range range) {
  }
}
