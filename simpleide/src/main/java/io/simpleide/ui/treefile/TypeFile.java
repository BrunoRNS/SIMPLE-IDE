package io.simpleide.ui.treefile;

/**
 * File type used for syntax highlighting, icons and language selection.
 *
 * <p>
 * Each constant carries a display name that is returned by {@link #toString()}. Unknown
 * extensions are classified as {@link #TXT}, and undecodable files as {@link #BINARY}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public enum TypeFile {

	JAVA("java"), CSHARP("csharp"), GROOVY("groovy"), TXT("txt"), KOTLIN("kotlin"), JAVASCRIPT("javascript"), GO("go"),
	RUST("rust"), PYTHON("python"), BINARY("binary"), CPP("c++"), C("C");

	private final String name;

	private TypeFile(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}

}
