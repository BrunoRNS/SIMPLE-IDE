package io.simpleide.ui.sidebar.icons;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.utils.ResourceUtil;

/**
 * Loads the pre-coloured activity-bar SVG for a theme, state and view.
 *
 * <p>
 * Resources are organized as {@code theme/state/type.svg}; each combination is mapped
 * once and loaded through {@link ResourceUtil}, including when packaged in an archive.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class SidebarIconResources {

	private static final int ICON_SIZE = 24;

	private static final Map<Key, String> RESOURCES = createResources();

	private static final Map<Key, Icon> ICONS = new ConcurrentHashMap<>();

	private SidebarIconResources() {
	}

	/**
	 * Returns the SVG icon for the requested activity-bar state.
	 * @param theme active application theme
	 * @param state icon state
	 * @param type activity-bar view
	 * @return the bundled SVG icon
	 */
	public static Icon get(ThemeManager.ThemeType theme, State state, Type type) {
		Key key = new Key(theme, state, type);
		return ICONS.computeIfAbsent(key, SidebarIconResources::load);
	}

	/**
	 * Returns the relative resource path for a theme/state/type combination.
	 * @param theme active application theme
	 * @param state icon state
	 * @param type activity-bar view
	 * @return path below this class's package
	 */
	public static String resourcePath(ThemeManager.ThemeType theme, State state, Type type) {
		return RESOURCES.get(new Key(theme, state, type));
	}

	private static Icon load(Key key) {
		String resource = RESOURCES.get(key);
		try {
			Path path = ResourceUtil.getResourcePath(SidebarIconResources.class, resource.split("/"));
			return new FlatSVGIcon(path.toFile()).derive(ICON_SIZE, ICON_SIZE);
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Unable to load sidebar icon resource " + resource, exception);
		}
	}

	private static Map<Key, String> createResources() {
		Map<Key, String> resources = new java.util.HashMap<>();
		for (ThemeManager.ThemeType theme : ThemeManager.ThemeType.values()) {
			for (State state : State.values()) {
				for (Type type : Type.values()) {
					resources.put(new Key(theme, state, type),
							themeDirectory(theme) + "/" + state.directory + "/" + type.fileName + ".svg");
				}
			}
		}
		return Map.copyOf(resources);
	}

	private static String themeDirectory(ThemeManager.ThemeType theme) {
		return switch (theme) {
			case DARK -> "dark";
			case LIGHT -> "light";
			case DRACULA -> "dracula";
			case DRACULA_LIGHT -> "dracula-light";
			case BROWN -> "brown";
			case BROWN_LIGHT -> "brown-light";
		};
	}

	private record Key(ThemeManager.ThemeType theme, State state, Type type) {
		private Key {
			java.util.Objects.requireNonNull(theme, "theme");
			java.util.Objects.requireNonNull(state, "state");
			java.util.Objects.requireNonNull(type, "type");
		}
	}

	/**
	 * Visual states represented by separate bundled SVG files.
	 */
	public enum State {

		IDLE("idle"),

		HOVER("hover"),

		SELECTED("selected");

		private final String directory;

		State(String directory) {
			this.directory = directory;
		}

	}

	/**
	 * Activity-bar icon types.
	 */
	public enum Type {

		EXPLORER("explorer"),

		SEARCH("search"),

		GIT("git"),

		SETTINGS("settings");

		private final String fileName;

		Type(String fileName) {
			this.fileName = fileName;
		}

	}

}
