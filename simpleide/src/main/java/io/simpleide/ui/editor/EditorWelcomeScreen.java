package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;

/**
 * Welcome page shown when no editor tab is open.
 *
 * <p>
 * Offers quick actions to open a folder or create a file. The centered layout and
 * typography keep the landing page visually separate from editor content.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 */
public final class EditorWelcomeScreen {

	private EditorWelcomeScreen() {
	}

	public static JPanel create(Runnable openFolder, Runnable createFile) {
		WelcomePanel welcomePanel = new WelcomePanel();
		JPanel content = new JPanel();
		content.setOpaque(false);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Welcome to SIMPLE-IDE", SwingConstants.CENTER);
		Font labelFont = UIManager.getFont("Label.font");
		if (labelFont != null) {
			title.setFont(labelFont.deriveFont(Font.BOLD, 28f));
		}
		title.setForeground(UIManager.getColor("Label.foreground"));
		title.setAlignmentX(JLabel.CENTER_ALIGNMENT);

		JLabel subtitle = new JLabel("Open a project or create a file to get started", SwingConstants.CENTER);
		subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
		subtitle.setAlignmentX(JLabel.CENTER_ALIGNMENT);

		JPanel actions = new JPanel(new GridBagLayout());
		actions.setOpaque(false);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 0, 12);
		JButton openFolderButton = createButton("Open Folder", true);
		openFolderButton.addActionListener(new ActionNode(event -> openFolder.run()));
		actions.add(openFolderButton, constraints);

		constraints.gridx = 1;
		constraints.insets = new Insets(0, 0, 0, 0);
		JButton newFileButton = createButton("New File", false);
		newFileButton.addActionListener(new ActionNode(event -> createFile.run()));
		actions.add(newFileButton, constraints);

		content.add(Box.createVerticalGlue());
		content.add(title);
		content.add(Box.createVerticalStrut(10));
		content.add(subtitle);
		content.add(Box.createVerticalStrut(24));
		content.add(actions);
		content.add(Box.createVerticalGlue());
		welcomePanel.add(content, BorderLayout.CENTER);
		return welcomePanel;
	}

	private static JButton createButton(String text, boolean primary) {
		JButton button = new JButton(text);
		button.putClientProperty("JButton.buttonType", "roundRect");
		button.setFocusable(false);
		button.setMargin(new Insets(9, 18, 9, 18));
		if (primary) {
			button.setFont(button.getFont().deriveFont(Font.BOLD));
		}
		return button;
	}

	private static final class WelcomePanel extends JPanel {

		private WelcomePanel() {
			super(new BorderLayout());
			applyTheme();
		}

		@Override
		public void updateUI() {
			super.updateUI();
			applyTheme();
		}

		private void applyTheme() {
			java.awt.Color background = UIManager.getColor("TabbedPane.contentAreaColor");
			if (background == null) {
				background = UIManager.getColor("TabbedPane.background");
			}
			if (background == null) {
				background = UIManager.getColor("Panel.background");
			}
			if (background != null) {
				setBackground(background);
			}
			setOpaque(true);
		}

	}

}
