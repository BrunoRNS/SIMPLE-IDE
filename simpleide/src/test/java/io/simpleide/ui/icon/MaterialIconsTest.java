package io.simpleide.ui.icon;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.ThemeColors;
import io.simpleide.frame.utils.ApplicationIcon;
import io.simpleide.ui.menubar.ThemeManager;

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
			definitions.properties().iterator().forEachRemaining(entry -> {
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
	void everyWelcomeLogoRevealsItsThemeBackgroundAndRendersVisibleDetails() throws Exception {
		for (ThemeManager.ThemeType theme : ThemeManager.ThemeType.values()) {
			String suffix = switch (theme) {
				case DARK -> "dark";
				case LIGHT -> "light";
				case DRACULA -> "dracula";
				case DRACULA_LIGHT -> "dracula-light";
				case BROWN -> "brown";
				case BROWN_LIGHT -> "brown-light";
			};
			String resource = "io/simpleide/icon/simpleide-logo-" + suffix + ".svg";
			assertThat(getClass().getClassLoader().getResource(resource)).as("logo resource for %s", theme).isNotNull();
			Icon icon = new FlatSVGIcon(resource, 64, 64);
			BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try {
				icon.paintIcon(null, graphics, 0, 0);
			}
			finally {
				graphics.dispose();
			}
			assertThat(image.getRGB(0, 0) >>> 24).as("canvas reveals the welcome background for %s", theme).isZero();
			String svg;
			try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
				assertThat(input).isNotNull();
				svg = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			}
			assertLogoPathUsesTransparentFill(svg, "M0,0L1254,0");
			assertLogoPathHasTransparentCounter(svg, "M505,825", "M538,857");
			assertLogoPathHasTransparentCounter(svg, "M911,825", "M944,857");
			ThemePalette palette = ThemePalette.of(theme);
			assertThat(ThemeColors.contrastRatio(palette.foreground(), palette.background()))
				.as("logo foreground contrast for %s", theme)
				.isGreaterThanOrEqualTo(3.0d);
			assertThat(ThemeColors.contrastRatio(palette.accent(), palette.background()))
				.as("logo accent contrast for %s", theme)
				.isGreaterThanOrEqualTo(3.0d);
			long detailPixels = countOpaquePixels(image);
			assertThat(detailPixels).as("logo details render for %s", theme).isGreaterThan(100L);
			BufferedImage fullLogo = renderWelcomeLogo(resource, 256);
			assertThat(fullLogo.getRGB(115, 178) >>> 24).as("P counter reveals page for %s", theme).isZero();
			assertThat(fullLogo.getRGB(198, 183) >>> 24).as("D counter reveals page for %s", theme).isZero();
		}
	}

	private void assertLogoPathUsesTransparentFill(String svg, String pathStart) {
		int pathIndex = svg.indexOf("d=\"" + pathStart);
		assertThat(pathIndex).as("SVG path %s exists", pathStart).isGreaterThanOrEqualTo(0);
		int pathEnd = svg.indexOf("/>", pathIndex);
		assertThat(svg.substring(pathIndex, pathEnd)).as("SVG path %s reveals the welcome canvas", pathStart)
			.contains("fill=\"none\"");
	}

	private void assertLogoPathHasTransparentCounter(String svg, String outerPath, String counterPath) {
		int pathIndex = svg.indexOf("d=\"" + outerPath);
		assertThat(pathIndex).as("SVG outline %s exists", outerPath).isGreaterThanOrEqualTo(0);
		int pathEnd = svg.indexOf("/>", pathIndex);
		assertThat(svg.substring(pathIndex, pathEnd)).as("SVG outline %s cuts out %s", outerPath, counterPath)
			.contains(counterPath)
			.contains("fill-rule=\"evenodd\"");
	}

	private BufferedImage renderWelcomeLogo(String resource, int size) {
		Icon icon = new FlatSVGIcon(resource, size, size);
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			icon.paintIcon(null, graphics, 0, 0);
		}
		finally {
			graphics.dispose();
		}
		return image;
	}

	private long countOpaquePixels(BufferedImage image) {
		long count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					count++;
				}
			}
		}
		return count;
	}

	@Test
	void bundledApplicationPngIconCanBeDecoded() throws Exception {
		assertThat(ApplicationIcon.loadImage()).isNotNull();
		try (InputStream icon = getClass().getClassLoader()
			.getResourceAsStream("io/simpleide/icon/simpleide_icon.png")) {
			assertThat(icon).isNotNull();
			BufferedImage image = javax.imageio.ImageIO.read(icon);
			assertThat(image).isNotNull();
			assertThat(image.getWidth()).isGreaterThan(0);
			assertThat(image.getHeight()).isGreaterThan(0);
			assertThat(countPixelsDifferentFrom(image, 0)).isGreaterThan(100L);
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

	private long countPixelsDifferentFrom(BufferedImage image, int rgb) {
		long count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0xFFFFFF) != rgb) {
					count++;
				}
			}
		}
		return count;
	}

}
