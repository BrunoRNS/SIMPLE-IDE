package io.simpleide.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locates executables on the host {@code PATH}.
 *
 * <p>
 * Absolute paths and paths containing a separator are checked directly. Bare command
 * names are searched in every {@code PATH} entry, adding the platform executable
 * extensions from {@code PATHEXT} on Windows.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
public class OSpathUtil {

	protected static final Logger LOGGER = LoggerFactory.getLogger(OSpathUtil.class);

	/**
	 * Resolves an executable command to an absolute path.
	 * @param command command name or path to locate
	 * @return the absolute executable path, or {@code null} when it cannot be found
	 */
	public static Path which(String command) {
		if (command == null || command.trim().isEmpty()) {
			return null;
		}

		Path directPath = Paths.get(command);
		if (directPath.isAbsolute() || command.contains("/") || command.contains("\\")) {
			return isExecutable(directPath) ? directPath.toAbsolutePath() : null;
		}

		List<String> extensions = getExecutableExtensions();

		String pathEnv = System.getenv("PATH");
		if (pathEnv == null || pathEnv.isEmpty()) {
			return null;
		}

		String[] dirs = pathEnv.split(File.pathSeparator);
		for (String dir : dirs) {
			if (dir.trim().isEmpty())
				continue;

			try {
				Path baseDir = Paths.get(dir);

				for (String ext : extensions) {
					Path candidate = baseDir.resolve(command + ext);

					if (isExecutable(candidate)) {
						return candidate.toAbsolutePath();
					}
				}
			}
			catch (Exception e) {
				LOGGER.warn("Failed to load candidate for checking PATH");
			}
		}

		return null;
	}

	private static boolean isExecutable(Path path) {
		return Files.exists(path) && Files.isRegularFile(path) && Files.isExecutable(path);
	}

	private static List<String> getExecutableExtensions() {
		List<String> extensions = new ArrayList<>();
		extensions.add("");

		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			String pathExt = System.getenv("PATHEXT");
			if (pathExt != null && !pathExt.isEmpty()) {
				for (String ext : pathExt.split(File.pathSeparator)) {
					if (!ext.isEmpty()) {
						extensions.add(ext.toLowerCase());
					}
				}
			}
			else {
				extensions.add(".exe");
				extensions.add(".cmd");
				extensions.add(".bat");
			}
		}
		return extensions;
	}

}
