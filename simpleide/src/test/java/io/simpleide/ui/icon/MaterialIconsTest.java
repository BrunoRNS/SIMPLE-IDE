package io.simpleide.ui.icon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.awt.Font;
import javax.swing.plaf.FontUIResource;
import javax.swing.Icon;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link io.simpleide.ui.icon.MaterialIcons}.
 */
class MaterialIconsTest {

	@Test
	void bundledManifestResolvesFilesAndFolders() {
		assertThat(MaterialIcons.hasDefinition("java")).isTrue();
		assertThat(MaterialIcons.hasDefinition("python")).isTrue();
		assertThat(MaterialIcons.hasDefinition("file")).isTrue();
		assertThat(MaterialIcons.hasDefinition("folder")).isTrue();

		Icon java = MaterialIcons.forFile(Path.of("Main.java"));
		Icon python = MaterialIcons.forFile(Path.of("main.py"));
		Icon unknown = MaterialIcons.forFile(Path.of("notes.unknown-extension"));
		Icon folder = MaterialIcons.forDirectory(Path.of("src"), false);
		Icon openFolder = MaterialIcons.forDirectory(Path.of("src"), true);

		assertThat(java).isNotNull();
		Font menuFont = UIManager.getFont("Menu.font");
		int expectedSize = Math.max(12, menuFont == null ? 12 : menuFont.getSize());
		assertThat(java.getIconWidth()).isEqualTo(expectedSize);
		assertThat(java.getIconHeight()).isEqualTo(expectedSize);
		assertThat(MaterialIcons.tabIconSize()).isEqualTo(expectedSize);
		assertThat(python).isNotNull();
		assertThat(unknown).isNotNull();
		assertThat(folder).isNotNull();
		assertThat(openFolder).isNotNull();
		assertThat(folder).isNotSameAs(openFolder);
	}

	@Test
	void compoundExtensionsUseTheMostSpecificMapping() {
		Icon component = MaterialIcons.forFile(Path.of("app.component.ts"));
		Icon typescript = MaterialIcons.forFile(Path.of("app.ts"));

		assertThat(component).isNotNull();
		assertThat(typescript).isNotNull();
	}

	@Test
	void iconSizeTracksTheConfiguredApplicationMenuFont() {
		Object originalFont = UIManager.get("Menu.font");
		try {
			UIManager.put("Menu.font", new FontUIResource(Font.DIALOG, Font.PLAIN, 23));
			Icon icon = MaterialIcons.forFile("Main.java");
			assertThat(icon.getIconWidth()).isEqualTo(23);
			assertThat(icon.getIconHeight()).isEqualTo(23);
			assertThat(MaterialIcons.tabIconSize()).isEqualTo(23);
		}
		finally {
			if (originalFont == null) {
				UIManager.getDefaults().remove("Menu.font");
			}
			else {
				UIManager.put("Menu.font", originalFont);
			}
		}
	}

}
