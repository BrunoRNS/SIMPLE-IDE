package io.simpleide.ui.menubar.menus;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

class HelpMenuTest {

  @Test
  void shouldExposeShortcutsMachineInformationAndAbout() {
    HelpMenu helpMenu = new HelpMenu();

    assertThat(helpMenu.getItem(0).getText()).isEqualTo("Keyboard Shortcuts");
    assertThat(helpMenu.getItem(1).getText()).isEqualTo("Machine Information");
    assertThat(helpMenu.getItem(2)).isNull();
    assertThat(helpMenu.getItem(3)).isInstanceOf(JMenuItem.class);
    assertThat(helpMenu.getItem(3).getText()).isEqualTo("About Simple IDE");
  }
}
