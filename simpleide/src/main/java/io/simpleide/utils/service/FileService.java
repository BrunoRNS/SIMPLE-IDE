package io.simpleide.utils.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TreeFile;

/**
 * File and directory operations for the project tree.
 *
 * <p>
 * The service writes UTF-8 text, deletes trees recursively, renames entries and creates
 * new files or directories. User-provided names are validated to a single relative path
 * segment before they are resolved against a parent directory.
 *
 * <p>
 * Covered by {@code FileServiceTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
public class FileService {

	/**
	 * Writes text to a file using UTF-8.
	 * @param path file to write
	 * @param content text to write
	 * @throws IOException when the file cannot be written
	 */
	public void saveFile(Path path, String content) throws IOException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(content, "content");
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	/**
	 * Deletes a file or directory tree without following symbolic links.
	 * @param path file or directory to delete
	 * @throws IOException when an entry cannot be deleted
	 */
	public void deleteFile(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}

		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
				if (exception != null) {
					throw exception;
				}
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Renames a file or directory within its parent directory.
	 * @param oldPath current path
	 * @param name new single-segment name
	 * @return the new path
	 * @throws IllegalArgumentException when the name is not a valid single segment
	 * @throws IOException when the entry cannot be moved
	 */
	public Path renameFile(Path oldPath, String name) throws IOException {
		Path namePath = parseName(name);
		Path newPath = oldPath.getParent().resolve(namePath);
		Files.move(oldPath, newPath);
		return newPath;
	}

	/**
	 * Creates a file or directory below a parent directory.
	 * @param parent parent directory node
	 * @param name new single-segment name
	 * @param isDirectory {@code true} to create a directory, {@code false} for a file
	 * @return the created file or directory node
	 * @throws IllegalArgumentException when the name is not a valid single segment
	 * @throws IOException when the entry cannot be created
	 */
	public TreeFile createNewFile(Directory parent, String name, boolean isDirectory) throws IOException {
		Path path = parent.getPath().resolve(parseName(name));
		if (isDirectory) {
			Files.createDirectory(path);
			return new Directory(path);
		}

		Files.createFile(path);
		return new File(path);
	}

	private Path parseName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Enter a valid file or directory name.");
		}

		Path namePath = Path.of(name.strip());
		if (namePath.isAbsolute() || namePath.getNameCount() != 1 || namePath.toString().equals(".")
				|| namePath.toString().equals("..")) {
			throw new IllegalArgumentException("Enter a valid file or directory name.");
		}
		return namePath;
	}

}
