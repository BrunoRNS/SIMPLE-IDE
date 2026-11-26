package io.simpleide;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Exposes the version declared by the packaged application.
 *
 * <p>
 * The version is read once from the {@code /simpleide.properties} classpath resource so
 * that callers do not need to know how the build stores its metadata.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/application.md">
 * docs/api/ui/application.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class SimpleIDEVersion {

	/**
	 * Version string declared in {@code /simpleide.properties}, for example
	 * {@code 1.0.0beta}.
	 */
	public static final String VERSION = loadVersion();

	private SimpleIDEVersion() {
	}

	private static String loadVersion() {
		Properties properties = new Properties();
		try (InputStream input = SimpleIDEVersion.class.getResourceAsStream("/simpleide.properties")) {
			if (input == null) {
				throw new IllegalStateException("Missing resource: /simpleide.properties");
			}
			properties.load(input);
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to load /simpleide.properties", e);
		}

		String version = properties.getProperty("version");
		if (version == null || version.isBlank()) {
			throw new IllegalStateException("Missing version in /simpleide.properties");
		}
		return version;
	}

}
