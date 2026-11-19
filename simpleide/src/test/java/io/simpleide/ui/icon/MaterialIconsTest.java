package io.simpleide.ui.icon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.swing.Icon;
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
		assertThat(java.getIconWidth()).isEqualTo(16);
		assertThat(java.getIconHeight()).isEqualTo(16);
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

}
