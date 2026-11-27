package io.simpleide.ui.treefile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source file entry in the project tree.
 *
 * <p>
 * The file holds its resolved {@link TypeFile}, detected {@link FileContentKind} and a
 * mutable buffer with its text. Image and binary content is kept out of the text buffer
 * and handled by the corresponding viewer.
 *
 * @deprecated since v1.1.0, use SimpleIDEFile instead.
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
@Deprecated
public class File extends TreeFile {

	private static final long MAX_IN_MEMORY_FILE_BYTES = 2L * 1024 * 1024;

	private static final long MAX_IMAGE_PIXELS = 1_000_000;

	private static final Logger LOGGER = LoggerFactory.getLogger(File.class);

	private volatile TypeFile typeFile;

	private final StringBuffer code;

	private volatile FileContentKind contentKind;

	private volatile BufferedImage imageContent;

	@SuppressWarnings("")
	private volatile byte[] binaryContent;

	private volatile boolean contentLoaded;

	private volatile boolean largeContent;

	private volatile long fileSize;

	private volatile int imageWidth;

	private volatile int imageHeight;

	/**
	 * Creates a file entry with explicit metadata.
	 * @param path file system path
	 * @param name display name
	 * @param typeFile resolved file type
	 * @param code file content
	 */
	@Deprecated
	public File(Path path, String name, TypeFile typeFile, StringBuffer code) {
		super(path, name);
		this.typeFile = typeFile;
		this.code = code;
		this.contentKind = typeFile == TypeFile.BINARY ? FileContentKind.BINARY : FileContentKind.TEXT;
		this.contentLoaded = true;
		this.fileSize = code.length();
	}

	/**
	 * Reads a file from disk and resolves its type from the extension.
	 * @param path file to read
	 * @throws IOException when the file cannot be read
	 */
	@Deprecated
	public File(Path path) throws IOException {
		this(path, loadNameFile(path), getTypeFile(path), readContent(path));
	}

	private File(Path path, String name, TypeFile typeFile, Content content) {
		this(path, name, typeFile, new StringBuffer(), false);
		apply(content);
	}

	private File(Path path, String name, TypeFile typeFile, StringBuffer code, boolean contentLoaded) {
		super(path, name);
		this.typeFile = typeFile;
		this.code = code;
		this.contentLoaded = contentLoaded;
		if (contentLoaded) {
			this.contentKind = typeFile == TypeFile.BINARY ? FileContentKind.BINARY : FileContentKind.TEXT;
		}
	}

	/**
	 * Creates a metadata-only file entry for explorers. Load its content with
	 * {@link #loadContent()} on a background thread when the file is opened.
	 * @param path file path
	 * @return an unloaded file entry
	 */
	@Deprecated
	public static File lazy(Path path) {
		return new File(path, loadNameFile(path), getTypeFile(path), new StringBuffer(), false);
	}

	/**
	 * Returns the resolved type of this file.
	 * @return the file type
	 */
	@Deprecated
	public TypeFile getTypeFile() {
		return typeFile;
	}

	/**
	 * Returns the in-memory content buffer.
	 * @return the mutable file content
	 */
	@Deprecated
	public StringBuffer getCode() {
		return code;
	}

	/** Returns whether this entry has been read and classified. */
	@Deprecated
	public boolean isContentLoaded() {
		return contentLoaded;
	}

	/**
	 * Returns whether this file needs a memory-bounded viewer instead of a full editor.
	 */
	@Deprecated
	public boolean isLargeContent() {
		return largeContent;
	}

	/** Returns the file size in bytes after content classification. */
	@Deprecated
	public long getFileSize() {
		return fileSize;
	}

	/**
	 * Returns how this file should be presented when opened.
	 * @return text, image, or binary content kind
	 */
	@Deprecated
	public FileContentKind getContentKind() {
		if (!contentLoaded) {
			throw new IllegalStateException("File content has not been loaded yet.");
		}
		return contentKind;
	}

	/**
	 * Loads contents once. Tree-created files should be loaded on a background thread.
	 * @throws IOException when the file cannot be read
	 */
	@Deprecated
	public synchronized void loadContent() throws IOException {
		if (!contentLoaded) {
			apply(readContent(getPath()));
		}
	}

	/**
	 * Returns a defensive copy of the binary content, or {@code null} for other kinds.
	 */
	@Deprecated
	public byte[] getBinaryContent() {
		byte[] content = binaryContent;
		return content == null ? null : content.clone();
	}

	/** Returns the decoded image, or {@code null} for other content kinds. */
	@Deprecated
	public BufferedImage getImageContent() {
		return imageContent;
	}

	/**
	 * Releases a decoded image preview so it can be reloaded the next time it is opened.
	 */
	@Deprecated
	public synchronized void releaseImageContent() {
		if (contentKind == FileContentKind.IMAGE) {
			imageContent = null;
			contentLoaded = false;
		}
	}

	/** Returns the original image width, or {@code 0} for non-image files. */
	@Deprecated
	public int getImageWidth() {
		return imageWidth;
	}

	/** Returns the original image height, or {@code 0} for non-image files. */
	@Deprecated
	public int getImageHeight() {
		return imageHeight;
	}

	private void apply(Content content) {
		typeFile = content.kind == FileContentKind.BINARY ? TypeFile.BINARY : typeFile;
		code.replace(0, code.length(), content.text);
		contentKind = content.kind;
		imageContent = content.image;
		binaryContent = content.bytes;
		largeContent = content.large;
		fileSize = content.size;
		imageWidth = content.imageWidth;
		imageHeight = content.imageHeight;
		contentLoaded = true;
	}

	private static Content readContent(Path path) throws IOException {
		long size = Files.size(path);
		try {
			ImageContent image = readImage(path);
			if (image != null) {
				return new Content(FileContentKind.IMAGE, "", image.image, null,
						(long) image.width * image.height > MAX_IMAGE_PIXELS, size, image.width, image.height);
			}
		}
		catch (IOException malformedImage) {
			LOGGER.debug("ImageIO could not decode {}; checking it as text or binary.", path, malformedImage);
		}
		if (size > MAX_IN_MEMORY_FILE_BYTES) {
			boolean text = isReadableText(path);
			return new Content(text ? FileContentKind.TEXT : FileContentKind.BINARY, "", null, null, true, size, 0, 0);
		}
		byte[] bytes;
		try (InputStream input = Files.newInputStream(path)) {
			bytes = input.readNBytes((int) MAX_IN_MEMORY_FILE_BYTES + 1);
		}
		if (bytes.length > MAX_IN_MEMORY_FILE_BYTES || Files.size(path) > MAX_IN_MEMORY_FILE_BYTES) {
			boolean text = isReadableText(path);
			return new Content(text ? FileContentKind.TEXT : FileContentKind.BINARY, "", null, null, true,
					Files.size(path), 0, 0);
		}
		String text = decodeText(bytes);
		if (text != null && isReadableText(text)) {
			return new Content(FileContentKind.TEXT, text, null, null, false, size, 0, 0);
		}
		return new Content(FileContentKind.BINARY, "", null, null, false, size, 0, 0);
	}

	private static ImageContent readImage(Path path) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
			if (input == null) {
				return null;
			}
			java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				double pixels = (double) width * height;
				int subsampling = pixels <= MAX_IMAGE_PIXELS ? 1
						: Math.max(1, (int) Math.ceil(Math.sqrt(pixels / MAX_IMAGE_PIXELS)));
				ImageReadParam param = reader.getDefaultReadParam();
				if (subsampling > 1) {
					param.setSourceSubsampling(subsampling, subsampling, 0, 0);
				}
				BufferedImage image = reader.read(0, param);
				return image == null ? null : new ImageContent(image, width, height);
			}
			finally {
				reader.dispose();
			}
		}
		catch (RuntimeException error) {
			LOGGER.debug("ImageIO could not decode {}; checking it as text or binary.", path, error);
			return null;
		}
	}

	private static String decodeText(byte[] bytes) {
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
		try {
			return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		}
		catch (CharacterCodingException error) {
			return null;
		}
	}

	private static boolean isReadableText(Path path) throws IOException {
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
		try (Reader reader = new InputStreamReader(Files.newInputStream(path), decoder)) {
			char[] buffer = new char[8192];
			int length;
			while ((length = reader.read(buffer)) != -1) {
				if (!isReadableText(buffer, length)) {
					return false;
				}
			}
			return true;
		}
		catch (CharacterCodingException error) {
			return false;
		}
	}

	private static boolean isReadableText(String text) {
		for (int index = 0; index < text.length(); index++) {
			if (!isReadableText(text.charAt(index))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isReadableText(char[] text, int length) {
		for (int index = 0; index < length; index++) {
			if (!isReadableText(text[index])) {
				return false;
			}
		}
		return true;
	}

	private static boolean isReadableText(char character) {
		return character != '\0' && (!Character.isISOControl(character) || character == '\n' || character == '\r'
				|| character == '\t' || character == '\f');
	}

	private record Content(FileContentKind kind, String text, BufferedImage image, byte[] bytes, boolean large,
			long size, int imageWidth, int imageHeight) {
	}

	private record ImageContent(BufferedImage image, int width, int height) {
	}

	private static String loadNameFile(Path path) {
		return path.getFileName().toString();
	}

	/**
	 * Resolves the editor file type from a path's extension.
	 * @param path path to inspect
	 * @return the matching file type, or {@link TypeFile#TXT} when unknown
	 */
	@Deprecated
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
