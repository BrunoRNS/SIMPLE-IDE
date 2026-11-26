package io.simpleide.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves classpath resources to filesystem paths.
 *
 * <p>
 * File-system resources are returned directly after verifying they are regular files.
 * Resources inside archives are extracted to a temporary file that is deleted on exit.
 * Package and file segments are validated to prevent path traversal.
 *
 * <p>
 * Covered by {@code ResourceAndFileUtilTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class ResourceUtil {

	private ResourceUtil() {
	}

	/**
	 * Resolves a resource from a package name using the context class loader.
	 * @param packageName dot-separated package that contains the resource
	 * @param fileName resource file name without path separators
	 * @return the resource path, extracted to a temporary file when necessary
	 * @throws IllegalArgumentException when the package name or file name is invalid
	 * @throws IOException when the resource is missing, is not a regular file or cannot
	 * be extracted
	 */
	public static Path getResourcePath(String packageName, String fileName) throws IOException {
		String resourceName = toResourceName(packageName, fileName);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if (classLoader == null) {
			classLoader = ResourceUtil.class.getClassLoader();
		}
		URL resource = classLoader.getResource(resourceName);
		if (resource == null && classLoader != ResourceUtil.class.getClassLoader()) {
			resource = ResourceUtil.class.getClassLoader().getResource(resourceName);
		}
		if (resource == null) {
			throw new FileNotFoundException("Resource not found: " + resourceName);
		}
		return toPath(resource, fileName);
	}

	/**
	 * Resolves a resource relative to the package of a class.
	 * @param packageClass class whose package contains the resource
	 * @param fileName resource file name
	 * @return the resource path, extracted to a temporary file when necessary
	 * @throws IllegalArgumentException when the class or file name is invalid
	 * @throws IOException when the resource is missing, is not a regular file or cannot
	 * be extracted
	 */
	public static Path getResourcePath(Class<?> packageClass, String fileName) throws IOException {
		return getResourcePath(packageClass, new String[] { fileName });
	}

	/**
	 * Resolves a resource nested in subdirectories of a class package.
	 * @param packageClass class whose package is the base path
	 * @param resourcePath path segments below the package
	 * @return the resource path, extracted to a temporary file when necessary
	 * @throws IllegalArgumentException when the class or any path segment is invalid
	 * @throws IOException when the resource is missing, is not a regular file or cannot
	 * be extracted
	 */
	public static Path getResourcePath(Class<?> packageClass, String... resourcePath) throws IOException {
		if (packageClass == null) {
			throw new IllegalArgumentException("Package class must not be null.");
		}
		if (resourcePath == null || resourcePath.length == 0) {
			throw new IllegalArgumentException("Resource path must not be empty.");
		}

		StringBuilder resourceName = new StringBuilder(packageClass.getPackageName().replace('.', '/') + "/");
		for (String segment : resourcePath) {
			if (segment == null || segment.isBlank() || ".".equals(segment) || "..".equals(segment)
					|| segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0) {
				throw new IllegalArgumentException("Invalid resource path segment: " + segment);
			}
			resourceName.append(segment).append('/');
		}
		resourceName.setLength(resourceName.length() - 1);
		URL resource = packageClass.getResource("/" + resourceName);
		if (resource == null) {
			throw new FileNotFoundException("Resource not found: " + resourceName);
		}
		return toPath(resource, resourcePath[resourcePath.length - 1]);
	}

	private static String toResourceName(String packageName, String fileName) {
		if (packageName == null || packageName.isBlank()) {
			throw new IllegalArgumentException("Package name must not be blank.");
		}
		String[] segments = packageName.split("\\.", -1);
		for (String segment : segments) {
			if (!isJavaIdentifier(segment)) {
				throw new IllegalArgumentException("Invalid package name: " + packageName);
			}
		}
		if (fileName == null || fileName.isBlank() || ".".equals(fileName) || "..".equals(fileName)
				|| fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
			throw new IllegalArgumentException("File name must be a single resource name: " + fileName);
		}
		return String.join("/", segments) + "/" + fileName;
	}

	private static boolean isJavaIdentifier(String value) {
		if (value.isEmpty()) {
			return false;
		}
		int first = value.codePointAt(0);
		if (!Character.isJavaIdentifierStart(first)) {
			return false;
		}
		for (int offset = Character.charCount(first); offset < value.length();) {
			int codePoint = value.codePointAt(offset);
			if (!Character.isJavaIdentifierPart(codePoint)) {
				return false;
			}
			offset += Character.charCount(codePoint);
		}
		return true;
	}

	private static Path toPath(URL resource, String fileName) throws IOException {
		if ("file".equalsIgnoreCase(resource.getProtocol())) {
			try {
				Path path = Path.of(resource.toURI());
				if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					throw new IOException("Resource is not a regular file: " + path);
				}
				return path;
			}
			catch (URISyntaxException exception) {
				throw new IOException("Invalid resource URL: " + resource, exception);
			}
		}

		Path extracted = Files.createTempFile("simpleide-resource-", getSuffix(fileName));
		try {
			try (var input = resource.openStream()) {
				Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
			}
			extracted.toFile().deleteOnExit();
			return extracted;
		}
		catch (IOException exception) {
			try {
				Files.deleteIfExists(extracted);
			}
			catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			throw new IOException("Could not extract resource: " + resource, exception);
		}
	}

	private static String getSuffix(String fileName) {
		int extensionStart = fileName.lastIndexOf('.');
		if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
			return ".resource";
		}
		String extension = fileName.substring(extensionStart);
		return extension.length() <= 16 ? extension : ".resource";
	}

}
