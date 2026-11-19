package io.simpleide.ui.icon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.simpleide.ui.ThemeColors;
import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Material Icon Theme file and folder icons bundled with the application.
 *
 * <p>
 * The VS Code Material Icon Theme package is vendored under
 * {@code io/simpleide/icon/material}. The mapping manifest is read once and the SVG files
 * are loaded from the application classpath or jar; no network access and no external
 * installation are required. File names, compound extensions and the light-theme
 * overrides from the manifest are honoured, and unknown files fall back to the standard
 * Material file icon.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class MaterialIcons {

	private static final Logger LOGGER = LoggerFactory.getLogger(MaterialIcons.class);

	private static final String BASE = "io/simpleide/icon/material/";

	private static final String MANIFEST = BASE + "material-icons.json";

	private static final int TREE_ICON_SIZE = 16;

	private static final int TAB_ICON_SIZE = 16;

	private static final MaterialIcons INSTANCE = new MaterialIcons();

	private final Map<String, String> fileExtensions = new HashMap<>();

	private final Map<String, String> fileNames = new HashMap<>();

	private final Map<String, String> folderNames = new HashMap<>();

	private final Map<String, String> folderNamesExpanded = new HashMap<>();

	private final Map<String, String> lightFileExtensions = new HashMap<>();

	private final Map<String, String> lightFileNames = new HashMap<>();

	private final Map<String, String> lightFolderNames = new HashMap<>();

	private final Map<String, String> lightFolderNamesExpanded = new HashMap<>();

	private final Map<String, String> iconFiles = new HashMap<>();

	private final Map<IconKey, Icon> cache = new ConcurrentHashMap<>();

	private final String defaultFileIcon;

	private final String defaultFolderIcon;

	private final String defaultFolderExpandedIcon;

	private MaterialIcons() {
		String file = "file";
		String folder = "folder";
		String folderExpanded = "folder-open";
		try (InputStream input = MaterialIcons.class.getClassLoader().getResourceAsStream(MANIFEST)) {
			if (input == null) {
				LOGGER.warn("Material Icon Theme manifest is missing; using built-in fallback icons.");
			}
			else {
				JsonNode root = new ObjectMapper().readTree(input);
				readMap(root.path("fileExtensions"), fileExtensions);
				readMap(root.path("fileNames"), fileNames);
				readMap(root.path("folderNames"), folderNames);
				readMap(root.path("folderNamesExpanded"), folderNamesExpanded);
				readMap(root.path("light").path("fileExtensions"), lightFileExtensions);
				readMap(root.path("light").path("fileNames"), lightFileNames);
				readMap(root.path("light").path("folderNames"), lightFolderNames);
				readMap(root.path("light").path("folderNamesExpanded"), lightFolderNamesExpanded);
				readIconFiles(root.path("iconDefinitions"));
				file = text(root, "file", file);
				folder = text(root, "folder", folder);
				folderExpanded = text(root, "folderExpanded", folderExpanded);
			}
		}
		catch (IOException | RuntimeException error) {
			LOGGER.warn("Unable to read the Material Icon Theme manifest; using built-in fallback icons.", error);
		}
		defaultFileIcon = file;
		defaultFolderIcon = folder;
		defaultFolderExpandedIcon = folderExpanded;
	}

	/**
	 * Returns the icon for a file path, using its name and extension.
	 * @param path file path, may be {@code null}
	 * @return a non-null icon
	 */
	public static Icon forFile(Path path) {
		return INSTANCE
			.forFileInternal(path == null || path.getFileName() == null ? "" : path.getFileName().toString());
	}

	/**
	 * Returns the icon for a file name.
	 * @param fileName file name, may be {@code null}
	 * @return a non-null icon
	 */
	public static Icon forFile(String fileName) {
		return INSTANCE.forFileInternal(fileName == null ? "" : fileName);
	}

	/**
	 * Returns the icon for a directory.
	 * @param path directory path, may be {@code null}
	 * @param expanded whether the directory is expanded in the tree
	 * @return a non-null icon
	 */
	public static Icon forDirectory(Path path, boolean expanded) {
		return INSTANCE.forDirectoryInternal(path, expanded);
	}

	private Icon forDirectoryInternal(Path path, boolean expanded) {
		String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString();
		boolean light = isLightTheme();
		String icon = find(folderNamesExpanded, lightFolderNamesExpanded, name, light);
		if (!expanded) {
			icon = find(folderNames, lightFolderNames, name, light);
			if (icon == null) {
				icon = defaultFolderIcon;
			}
		}
		else if (icon == null) {
			icon = defaultFolderExpandedIcon;
		}
		return load(icon, TREE_ICON_SIZE, light, defaultFolderIcon);
	}

	/**
	 * Returns the icon size used by editor tabs.
	 * @return the tab icon size in pixels
	 */
	public static int tabIconSize() {
		return TAB_ICON_SIZE;
	}

	private Icon forFileInternal(String fileName) {
		boolean light = isLightTheme();
		String icon = find(fileNames, lightFileNames, fileName, light);
		if (icon == null) {
			icon = findByExtension(fileName, light);
		}
		if (icon == null) {
			icon = defaultFileIcon;
		}
		return load(icon, TREE_ICON_SIZE, light, defaultFileIcon);
	}

	private String findByExtension(String fileName, boolean light) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		String icon = find(fileExtensions, lightFileExtensions, lower, light);
		if (icon != null) {
			return icon;
		}
		String[] parts = lower.split("\\.");
		for (int start = 1; start < parts.length; start++) {
			String suffix = String.join(".", java.util.Arrays.copyOfRange(parts, start, parts.length));
			icon = find(fileExtensions, lightFileExtensions, suffix, light);
			if (icon != null) {
				return icon;
			}
		}
		return null;
	}

	private String find(Map<String, String> base, Map<String, String> lightOverrides, String key, boolean light) {
		if (key == null || key.isEmpty()) {
			return null;
		}
		String normalized = key.toLowerCase(Locale.ROOT);
		if (light) {
			String override = lightOverrides.get(normalized);
			if (override == null) {
				override = lightOverrides.get(key);
			}
			if (override != null) {
				return override;
			}
		}
		String icon = base.get(normalized);
		if (icon == null) {
			icon = base.get(key);
		}
		return icon;
	}

	private Icon load(String iconName, int size, boolean light, String fallbackName) {
		String resolved = iconName;
		if (resolved == null || resolved.isBlank() || !iconFiles.containsKey(resolved)) {
			resolved = fallbackName;
		}
		IconKey key = new IconKey(resolved, light, size);
		return cache.computeIfAbsent(key, ignored -> createIcon(key));
	}

	private Icon createIcon(IconKey key) {
		String fileName = iconFiles.get(key.name());
		if (fileName == null) {
			fileName = iconFiles.getOrDefault(defaultFileIcon, defaultFileIcon + ".svg");
		}
		String resource = BASE + "icons/" + fileName;
		if (MaterialIcons.class.getClassLoader().getResource(resource) == null) {
			LOGGER.warn("Material icon {} is not bundled; using the default file icon.", fileName);
			resource = BASE + "icons/" + iconFiles.getOrDefault(defaultFileIcon, "file.svg");
		}
		FlatSVGIcon icon = new FlatSVGIcon(resource, key.size(), key.size());
		icon.setColorFilter(new FlatSVGIcon.ColorFilter((component, color) -> ensureIconContrast(component, color)));
		return icon;
	}

	private static Color ensureIconContrast(Component component, Color color) {
		if (color == null || color.getAlpha() == 0) {
			return color;
		}
		Color background = component == null ? null : component.getBackground();
		if (background == null) {
			background = ThemeColors.ui("Tree.background", "Panel.background", "TabbedPane.background");
		}
		Color readable = ThemeColors.readable(color, background, ThemeColors.MIN_LARGE_TEXT_CONTRAST);
		return new Color(readable.getRed(), readable.getGreen(), readable.getBlue(), color.getAlpha());
	}

	private void readIconFiles(JsonNode node) {
		if (node == null || !node.isObject()) {
			return;
		}
		node.fields().forEachRemaining(entry -> {
			String path = entry.getValue().path("iconPath").asText();
			if (!path.isBlank()) {
				String fileName = path.substring(path.lastIndexOf('/') + 1);
				iconFiles.put(entry.getKey(), fileName);
			}
		});
	}

	private static boolean isLightTheme() {
		Color background = ThemeColors.ui("Panel.background", "control", "Tree.background");
		return ThemeColors.isLight(background);
	}

	private static void readMap(JsonNode node, Map<String, String> target) {
		if (node == null || !node.isObject()) {
			return;
		}
		node.fields().forEachRemaining(entry -> {
			if (entry.getValue().isTextual()) {
				target.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().asText());
			}
		});
	}

	private static String text(JsonNode root, String field, String fallback) {
		JsonNode node = root.path(field);
		return node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
	}

	/**
	 * Returns whether the bundled manifest and SVG files include a definition.
	 * @param iconName icon definition name
	 * @return {@code true} when the icon is known
	 */
	public static boolean hasDefinition(String iconName) {
		return iconName != null && INSTANCE.iconFiles.containsKey(iconName);
	}

	private record IconKey(String name, boolean light, int size) {
	}

}
