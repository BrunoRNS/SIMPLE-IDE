package io.simpleide.ui.menubar.menus;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.menubar.ThemeManager;

/**
 * Radio menu that selects the application theme.
 *
 * <p>
 * Every item changes {@link io.simpleide.ui.menubar.ThemeManager} on the EDT. Dark is
 * selected initially, matching the theme manager default.
 *
 * <p>
 * Covered by {@code ThemeMenuTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class ThemeMenu extends JMenu {

	private static final Logger LOGGER = LoggerFactory.getLogger(ThemeMenu.class);

	protected JRadioButtonMenuItem dark;

	protected JRadioButtonMenuItem light;

	protected JRadioButtonMenuItem dracula;

	protected JRadioButtonMenuItem draculaLight;

	protected JRadioButtonMenuItem brown;

	protected JRadioButtonMenuItem brownLight;

	protected ButtonGroup buttonGroup;

	/**
	 * Creates the theme menu and its radio items.
	 */
	public ThemeMenu() {
		super("Theme");
		createItems();
		configureMenu();
		configureActions();
	}

	protected void configureMenu() {
		add(dark);
		add(light);
		add(dracula);
		add(draculaLight);
		add(brown);
		add(brownLight);
	}

	protected void createItems() {
		buttonGroup = new ButtonGroup();

		light = new JRadioButtonMenuItem("Light");
		dark = new JRadioButtonMenuItem("Dark");
		dracula = new JRadioButtonMenuItem("Dracula");
		draculaLight = new JRadioButtonMenuItem("Dracula Light");
		brown = new JRadioButtonMenuItem("Brown");
		brownLight = new JRadioButtonMenuItem("Brown Light");
		dark.setSelected(true);

		buttonGroup.add(light);
		buttonGroup.add(dark);
		buttonGroup.add(dracula);
		buttonGroup.add(draculaLight);
		buttonGroup.add(brown);
		buttonGroup.add(brownLight);
	}

	protected void configureActions() {
		light.addActionListener(new ActionNode(e -> {
			LOGGER.info("Set light theme");
			ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.LIGHT);
		}));

		dark.addActionListener(new ActionNode(e -> {
			LOGGER.info("Set dark theme");
			ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.DARK);
		}));

		dracula.addActionListener(new ActionNode(e -> {
			LOGGER.info("Set Dracula theme");
			ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.DRACULA);
		}));

		draculaLight.addActionListener(new ActionNode(e -> {
			LOGGER.info("Set Dracula Light theme");
			ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.DRACULA_LIGHT);
		}));

		brown.addActionListener(new ActionNode(e -> {
			LOGGER.info("Set Brown theme");
			ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.BROWN);
		}));

		brownLight.addActionListener(new ActionNode(e -> {
			LOGGER.info("Set Brown Light theme");
			ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.BROWN_LIGHT);
		}));
	}

}
