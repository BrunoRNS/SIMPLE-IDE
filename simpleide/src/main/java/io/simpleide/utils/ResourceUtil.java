package io.simpleide.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ResourceUtil {
    private ResourceUtil() {
    }

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

    public static Path getResourcePath(Class<?> packageClass, String fileName) throws IOException {
        if (packageClass == null) {
            throw new IllegalArgumentException("Package class must not be null.");
        }

        String packageName = packageClass.getPackageName();
        String resourceName = toResourceName(packageName, fileName);
        URL resource = packageClass.getResource("/" + resourceName);
        if (resource == null) {
            throw new FileNotFoundException("Resource not found: " + resourceName);
        }
        return toPath(resource, fileName);
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
        if (fileName == null || fileName.isBlank()
            || ".".equals(fileName) || "..".equals(fileName)
            || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("File name must be a single resource name.");
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
            } catch (URISyntaxException exception) {
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
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(extracted);
            } catch (IOException cleanupException) {
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
