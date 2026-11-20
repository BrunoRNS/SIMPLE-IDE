package io.simpleide.ui.icon;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.awt.Font;
import java.util.HashSet;
import java.util.Set;
import javax.swing.plaf.FontUIResource;
import javax.swing.Icon;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link io.simpleide.ui.icon.MaterialIcons}.
 */
class MaterialIconsTest {

	@Test
	void everyBundledMaterialSvgIconLoadsAndRendersVisiblePixels() throws Exception {
		Set<String> iconFiles = new HashSet<>();
		try (InputStream manifest = getClass().getClassLoader()
			.getResourceAsStream("io/simpleide/icon/material/material-icons.json")) {
			assertThat(manifest).isNotNull();
			JsonNode definitions = new ObjectMapper().readTree(manifest).path("iconDefinitions");
			definitions.fields().forEachRemaining(entry -> {
				String path = entry.getValue().path("iconPath").asText();
				if (!path.isBlank()) {
					iconFiles.add(path.substring(path.lastIndexOf('/') + 1));
				}
			});
		}
		assertThat(iconFiles).isNotEmpty();
		for (String fileName : iconFiles) {
			String resource = "io/simpleide/icon/material/icons/" + fileName;
			assertThat(getClass().getClassLoader().getResource(resource)).as("bundled SVG %s", fileName).isNotNull();
			Icon icon = new FlatSVGIcon(resource, 16, 16);
			BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try {
				icon.paintIcon(null, graphics, 0, 0);
			}
			finally {
				graphics.dispose();
			}
			assertThat(hasVisiblePixel(image)).as("rendered SVG %s", fileName).isTrue();
		}
	}

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

	private boolean hasVisiblePixel(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					return true;
				}
			}
		}
		return false;
	}

}
