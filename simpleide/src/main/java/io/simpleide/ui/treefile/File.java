package io.simpleide.ui.treefile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source file entry in the project tree.
 *
 * <p>
 * The file holds its resolved {@link TypeFile} and a mutable buffer with its text, which
 * the editor loads and saves in place. The type is inferred from the extension.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class File extends TreeFile {

	private static final Logger LOGGER = LoggerFactory.getLogger(File.class);

	private final TypeFile typeFile;

	private final StringBuffer code;

	/**
	 * Creates a file entry with explicit metadata.
	 * @param path file system path
	 * @param name display name
	 * @param typeFile resolved file type
	 * @param code file content
	 */
	public File(Path path, String name, TypeFile typeFile, StringBuffer code) {
		super(path, name);
		this.typeFile = typeFile;
		this.code = code;
	}

	/**
	 * Reads a file from disk and resolves its type from the extension.
	 * @param path file to read
	 * @throws IOException when the file cannot be read
	 */
	public File(Path path) throws IOException {
		this(path, loadNameFile(path), getTypeFile(path), new StringBuffer(Files.readString(path)));
	}

	/**
	 * Returns the resolved type of this file.
	 * @return the file type
	 */
	public TypeFile getTypeFile() {
		return typeFile;
	}

	/**
	 * Returns the in-memory content buffer.
	 * @return the mutable file content
	 */
	public StringBuffer getCode() {
		return code;
	}

	private static String loadNameFile(Path path) {
		return path.getFileName().toString();
	}

	/**
	 * Resolves the editor file type from a path's extension.
	 * @param path path to inspect
	 * @return the matching file type, or {@link TypeFile#TXT} when unknown
	 */
	public static TypeFile typeOf(Path path) {
		return getTypeFile(path);
	}

	private static TypeFile getTypeFile(Path path) {
		String name = loadNameFile(path);
		String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		TypeFile typeFile;
		switch (ext) {
			case "jav", "java" -> typeFile = TypeFile.JAVA;
			case "cs", "csx" -> typeFile = TypeFile.CSHARP;
			case "kt", "kts" -> typeFile = TypeFile.KOTLIN;
			case "groovy", "gvy", "gy", "gsh" -> typeFile = TypeFile.GROOVY;
			case "js", "jsx", "mjs", "cjs", "jscript" -> typeFile = TypeFile.JAVASCRIPT;
			case "go" -> typeFile = TypeFile.GO;
			case "rs" -> typeFile = TypeFile.RUST;
			case "py", "pyi", "pyw", "py3" -> typeFile = TypeFile.PYTHON;
			case "c", "h" -> typeFile = TypeFile.C;
			case "cc", "cpp", "cxx", "hpp", "hxx" -> typeFile = TypeFile.CPP;
			default -> typeFile = TypeFile.TXT;
		}
		LOGGER.info("Load file by extension {}", ext);
		return typeFile;
	}

}
