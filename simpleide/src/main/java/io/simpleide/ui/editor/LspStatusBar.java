package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

import io.simpleide.lsp.LspStatus;
import io.simpleide.thread.ThreadMaster;

public final class LspStatusBar extends JPanel {
  private final Map<String, LspStatus> statuses = new HashMap<>();
  private final JLabel label = new JLabel("LSP: Idle");
  private final JProgressBar progress = new JProgressBar();

  public LspStatusBar() {
    super(new BorderLayout(8, 0));
    Color separator = UIManager.getColor("Separator.foreground");
    if (separator == null) {
      separator = Color.GRAY;
    }
    setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, separator),
        BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    setMinimumSize(new Dimension(0, 26));

    progress.setIndeterminate(true);
    progress.setBorderPainted(false);
    progress.setPreferredSize(new Dimension(90, 8));
    progress.setVisible(false);
    add(label, BorderLayout.CENTER);
    add(progress, BorderLayout.EAST);
  }

  public void updateStatus(LspStatus status) {
    Runnable update = () -> {
      if (status.state() == LspStatus.State.IDLE) {
        statuses.remove(status.id());
      } else {
        statuses.put(status.id(), status);
      }
      refreshStatus();
    };
    ThreadMaster.getInstance().executeOnEDT(update);
  }

  private void refreshStatus() {
    LspStatus active = statuses.values().stream()
        .filter(status -> isInProgress(status.state()))
        .max(Comparator.comparingInt(status -> priority(status.state())))
        .orElse(null);
    if (active != null) {
      label.setText("LSP: " + statusText(active.state()) + " " + active.serverName() + "...");
      progress.setVisible(true);
      return;
    }

    progress.setVisible(false);
    LspStatus failed = statuses.values().stream()
        .filter(status -> status.state() == LspStatus.State.FAILED)
        .findFirst()
        .orElse(null);
    if (failed != null) {
      label.setText("LSP: Failed to start " + failed.serverName());
      return;
    }

    long running = statuses.values().stream()
        .filter(status -> status.state() == LspStatus.State.RUNNING)
        .count();
    label.setText(running == 0 ? "LSP: Idle" : "LSP: Running (" + running + ")");
  }

  private boolean isInProgress(LspStatus.State state) {
    return switch (state) {
      case CHECKING_VERSION, DOWNLOADING, EXTRACTING, INSTALLING, STARTING -> true;
      default -> false;
    };
  }

  private int priority(LspStatus.State state) {
    return switch (state) {
      case DOWNLOADING, INSTALLING -> 4;
      case EXTRACTING -> 3;
      case STARTING -> 2;
      case CHECKING_VERSION -> 1;
      default -> 0;
    };
  }

  private String statusText(LspStatus.State state) {
    return switch (state) {
      case CHECKING_VERSION -> "Checking version";
      case DOWNLOADING -> "Downloading";
      case EXTRACTING -> "Extracting";
      case INSTALLING -> "Installing";
      case STARTING -> "Starting";
      default -> "";
    };
  }
}
