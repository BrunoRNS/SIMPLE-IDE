package io.simpleide.ui.menubar.menus;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.menubar.ThemeManager;

public final class ThemeMenu extends JMenu {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThemeMenu.class);

  protected JRadioButtonMenuItem dark;
  protected JRadioButtonMenuItem light;
  protected JRadioButtonMenuItem darcula;
  protected JRadioButtonMenuItem brown;
  protected JRadioButtonMenuItem brownLight;
  protected ButtonGroup buttonGroup;

  public ThemeMenu() {
    super("Theme");
    createItems();
    configureMenu();
    configureActions();
  }

  protected void configureMenu() {
    add(dark);
    add(light);
    add(darcula);
    add(brown);
    add(brownLight);
  }

  protected void createItems() {
    buttonGroup = new ButtonGroup();

    light = new JRadioButtonMenuItem("Light");
    dark = new JRadioButtonMenuItem("Dark");
    darcula = new JRadioButtonMenuItem("Darcula");
    brown = new JRadioButtonMenuItem("Brown");
    brownLight = new JRadioButtonMenuItem("Brown Light");
    dark.setSelected(true);

    buttonGroup.add(light);
    buttonGroup.add(dark);
    buttonGroup.add(darcula);
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

    darcula.addActionListener(new ActionNode(e -> {
      LOGGER.info("Set Darcula theme");
      ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.DRACULA);
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
