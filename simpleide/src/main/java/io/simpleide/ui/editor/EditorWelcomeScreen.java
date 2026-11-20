package io.simpleide.ui.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.menubar.ThemeManager;

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
		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);
		content.setBorder(BorderFactory.createEmptyBorder(22, 28, 20, 28));

		JLabel title = new WelcomeTextLabel("Welcome to SIMPLE-IDE", false, 36f);
		JLabel subtitle = new WelcomeTextLabel("Open a project or create a file to get started", true, 16f);

		JPanel actions = new JPanel(new GridBagLayout());
		actions.setOpaque(false);
		GridBagConstraints actionConstraints = new GridBagConstraints();
		actionConstraints.gridy = 0;
		actionConstraints.insets = new Insets(0, 0, 0, 12);
		JButton openFolderButton = createButton("Open Folder", true);
		openFolderButton.addActionListener(new ActionNode(event -> openFolder.run()));
		actions.add(openFolderButton, actionConstraints);

		actionConstraints.gridx = 1;
		actionConstraints.insets = new Insets(0, 0, 0, 0);
		JButton newFileButton = createButton("New File", false);
		newFileButton.addActionListener(new ActionNode(event -> createFile.run()));
		actions.add(newFileButton, actionConstraints);

		GridBagConstraints contentConstraints = new GridBagConstraints();
		contentConstraints.gridx = 0;
		contentConstraints.fill = GridBagConstraints.HORIZONTAL;
		contentConstraints.weightx = 1.0;
		contentConstraints.anchor = GridBagConstraints.NORTH;
		contentConstraints.insets = new Insets(0, 0, 8, 0);
		content.add(title, contentConstraints);
		contentConstraints.gridy = 1;
		contentConstraints.insets = new Insets(0, 0, 8, 0);
		content.add(subtitle, contentConstraints);
		contentConstraints.gridy = 2;
		contentConstraints.fill = GridBagConstraints.BOTH;
		contentConstraints.weighty = 1.0;
		contentConstraints.insets = new Insets(0, 0, 8, 0);
		content.add(createLogo(), contentConstraints);
		contentConstraints.gridy = 3;
		contentConstraints.fill = GridBagConstraints.NONE;
		contentConstraints.weighty = 0.0;
		contentConstraints.anchor = GridBagConstraints.SOUTH;
		contentConstraints.insets = new Insets(0, 0, 10, 0);
		content.add(actions, contentConstraints);
		welcomePanel.add(content, BorderLayout.CENTER);
		return welcomePanel;
	}

	private static JComponent createLogo() {
		ThemeLogoView logo = new ThemeLogoView();
		logo.setName("welcome-logo");
		return logo;
	}

	private static String logoResource() {
		String theme = switch (ThemeManager.getInstance().getCurrentTheme()) {
			case DARK -> "dark";
			case LIGHT -> "light";
			case DRACULA -> "dracula";
			case DRACULA_LIGHT -> "dracula-light";
			case BROWN -> "brown";
			case BROWN_LIGHT -> "brown-light";
		};
		return "/io/simpleide/icon/simpleide-logo-" + theme + ".svg";
	}

	private static final class ThemeLogoView extends JComponent {

		private static final Dimension MINIMUM_SIZE = new Dimension(120, 120);

		private static final Dimension PREFERRED_SIZE = new Dimension(220, 220);

		private FlatSVGIcon logo;

		private ThemeLogoView() {
			refreshLogo();
		}

		@Override
		public Dimension getMinimumSize() {
			return MINIMUM_SIZE;
		}

		@Override
		public Dimension getPreferredSize() {
			return PREFERRED_SIZE;
		}

		@Override
		public void updateUI() {
			super.updateUI();
			refreshLogo();
		}

		private void refreshLogo() {
			java.net.URL url = EditorWelcomeScreen.class.getResource(logoResource());
			if (url != null) {
				logo = new FlatSVGIcon(url);
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (logo == null) {
				return;
			}
			int size = Math.max(1, Math.min(getWidth(), getHeight()));
			FlatSVGIcon scaledLogo = logo.derive(size, size);
			scaledLogo.paintIcon(this, graphics, (getWidth() - size) / 2, (getHeight() - size) / 2);
		}

	}

	private static final class WelcomeTextLabel extends JLabel {

		private final boolean secondary;

		private final float size;

		private WelcomeTextLabel(String text, boolean secondary, float size) {
			super(text, SwingConstants.CENTER);
			this.secondary = secondary;
			this.size = size;
			setOpaque(false);
			applyThemeStyle();
		}

		@Override
		public void updateUI() {
			super.updateUI();
			if (size > 0) {
				applyThemeStyle();
			}
		}

		private void applyThemeStyle() {
			Font base = UIManager.getFont("Label.font");
			if (base != null) {
				setFont(base.deriveFont(secondary ? Font.PLAIN : Font.BOLD, size));
			}
			ThemePalette palette = ThemeManager.getInstance().currentPalette();
			Color background = palette.background();
			for (Container parent = getParent(); parent != null; parent = parent.getParent()) {
				if (parent.isOpaque() && parent.getBackground() != null) {
					background = parent.getBackground();
					break;
				}
			}
			Color preferred = secondary ? palette.mutedForeground()
					: ThemeColors.blend(palette.accent(), palette.foreground(), 0.12f);
			Color readable = ThemeColors.readable(preferred, background);
			if (ThemeColors.contrastRatio(readable, palette.surface()) < ThemeColors.MIN_TEXT_CONTRAST) {
				readable = ThemeColors.readable(readable, palette.surface());
			}
			setForeground(readable);
		}

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
			Color background = UIManager.getColor("TabbedPane.contentAreaColor");
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
