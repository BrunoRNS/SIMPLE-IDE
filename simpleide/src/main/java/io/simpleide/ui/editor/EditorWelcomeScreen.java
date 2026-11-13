package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import io.simpleide.frame.layout.MainLayout;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;

final class EditorWelcomeScreen {

  private EditorWelcomeScreen() {
  }

  static JPanel create(Runnable openFolder, Runnable createFile) {
    JPanel welcomePanel = new JPanel(new BorderLayout());

    JPanel content = new JPanel();
    MainLayout layout = new MainLayout();
    content.setLayout(layout);
    content.setOpaque(false);

    JLabel title = new JLabel("Welcome to SIMPLE-IDE");
    title.setFont(new Font("Segoe UI", Font.BOLD, 24));
    title.setHorizontalAlignment(SwingConstants.CENTER);
    layout.addComponent(title, 0, 0, 2, 1);
    content.add(title);

    JLabel subtitle = new JLabel("Open a file in the tree to start editing.");
    subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    subtitle.setHorizontalAlignment(SwingConstants.CENTER);
    layout.addComponent(subtitle, 0, 1, 2, 1);
    content.add(subtitle);

    JButton openFolderButton = createButton("Open Folder");
    openFolderButton.addActionListener(new ActionNode(event -> openFolder.run()));
    layout.addComponent(openFolderButton, 0, 3, 1, 1);
    content.add(openFolderButton);

    JButton newFileButton = createButton("New File");
    newFileButton.addActionListener(new ActionNode(event -> createFile.run()));
    layout.addComponent(newFileButton, 0, 4, 1, 1);
    content.add(newFileButton);

    welcomePanel.add(content, BorderLayout.CENTER);
    return welcomePanel;
  }

  private static JButton createButton(String text) {
    JButton button = new JButton(text);
    button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    button.setFocusable(false);
    button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    button.setContentAreaFilled(false);
    return button;
  }
}
