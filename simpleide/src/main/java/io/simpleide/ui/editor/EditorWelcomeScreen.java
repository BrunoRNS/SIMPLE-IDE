package io.simpleide.ui.editor;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import io.simpleide.frame.layout.MainLayout;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;

/**
 * Welcome page shown when no editor tab is open.
 *
 * <p>
 * Offers buttons to open a folder or create a file. Both callbacks are supplied by the
 * editor manager and run as EDT nodes.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class EditorWelcomeScreen {

	private EditorWelcomeScreen() {
	}

	public static JPanel create(Runnable openFolder, Runnable createFile) {
		JPanel welcomePanel = new WelcomePanel();
		welcomePanel.setLayout(new BorderLayout());

		JPanel content = new JPanel();
		MainLayout layout = new MainLayout();
		content.setLayout(layout);
		content.setOpaque(false);

		JLabel title = new JLabel("Welcome to SIMPLE-IDE");
		title.setHorizontalAlignment(SwingConstants.CENTER);
		layout.addComponent(title, 0, 0, 2, 1);
		content.add(title);

		JLabel subtitle = new JLabel("Open a file in the tree to start editing.");
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
		button.setFocusable(false);
		button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
		button.setContentAreaFilled(true);
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
			java.awt.Color background = UIManager.getColor("TabbedPane.background");
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
