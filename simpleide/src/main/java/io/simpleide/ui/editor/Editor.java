package io.simpleide.ui.editor;

import java.awt.Font;
import java.util.Locale;
import java.util.Objects;

import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import io.simpleide.ui.treefile.SimpleIDEFile;
import io.simpleide.ui.treefile.TypeFile;

/**
 * RSyntaxTextArea subclass with the application editing defaults.
 *
 * <p>
 * The editor applies the UI font, enables code folding, auto-indent, animated bracket
 * matching and automatic closing of curly braces, and maps a
 * {@link io.simpleide.ui.treefile.TypeFile} to the matching syntax style. All remaining
 * behavior, including search, completion and themes, comes from RSyntaxTextArea.
 *
 * <p>
 * Covered by {@code EditorTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/rsyntax.md">docs/api/rsyntax.md</a>
 */
public class Editor extends RSyntaxTextArea {

	/**
	 * Creates an empty editor with the application font and editing defaults.
	 */
	public Editor() {
		super();
		applyUiFont();
		config();
	}

	@Override
	public void updateUI() {
		super.updateUI();
		applyUiFont();
	}

	private void applyUiFont() {
		Font editorFont = UIManager.getFont("TextArea.font");
		if (editorFont != null) {
			setFont(editorFont);
		}
	}

	/** Reapplies the current application font after a global settings change. */
	public void refreshUiFont() {
		applyUiFont();
	}

	/**
	 * Loads a source file into the editor and moves the caret to the start.
	 * @param file file to read, must not be {@code null}
	 */
	public void loadFile(SimpleIDEFile file) {
		Objects.requireNonNull(file, "file");
		applyUiFont();
		setSyntaxEditingStyle(syntaxStyleForFile(file.getName(), file.getTypeFile()));
		setText(file.getCode().toString());
		setCaretPosition(0);
	}

	/**
	 * Applies the editor syntax style that corresponds to a file type.
	 * @param type file type to apply
	 */
	public void setSyntaxStyle(TypeFile type) {
		setSyntaxEditingStyle(syntaxStyleFor(type));
	}

	private void config() {
		setCodeFoldingEnabled(true);
		setAutoIndentEnabled(true);
		setAnimateBracketMatching(true);
		setCloseCurlyBraces(true);
		setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
	}

	/**
	 * Returns the RSyntaxTextArea syntax style constant for a file type.
	 * @param type file type to translate
	 * @return an RSyntaxTextArea syntax style constant
	 */
	public static String syntaxStyleFor(TypeFile type) {
		Objects.requireNonNull(type, "type");
		return switch (type) {
			case JAVA -> SyntaxConstants.SYNTAX_STYLE_JAVA;
			case CSHARP -> SyntaxConstants.SYNTAX_STYLE_CSHARP;
			case GROOVY -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
			case KOTLIN -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
			case JAVASCRIPT -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
			case GO -> SyntaxConstants.SYNTAX_STYLE_GO;
			case RUST -> SyntaxConstants.SYNTAX_STYLE_RUST;
			case PYTHON -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
			case C -> SyntaxConstants.SYNTAX_STYLE_C;
			case CPP -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
			case TXT, BINARY -> SyntaxConstants.SYNTAX_STYLE_NONE;
		};
	}

	/**
	 * Returns an RSyntaxTextArea syntax style for a file. Existing language types keep
	 * their dedicated mappings; other file extensions supported by RSyntaxTextArea get
	 * highlighting only and do not enable language completion or correction features.
	 * @param fileName file name including extension
	 * @param type classified application file type
	 * @return the RSyntaxTextArea syntax style
	 * @since v1.1.0
	 */
	public static String syntaxStyleForFile(String fileName, TypeFile type) {
		Objects.requireNonNull(fileName, "fileName");
		Objects.requireNonNull(type, "type");
		if (type != TypeFile.TXT) {
			return syntaxStyleFor(type);
		}
		String name = fileName.toLowerCase(Locale.ROOT);
		if (name.equals("dockerfile")) {
			return SyntaxConstants.SYNTAX_STYLE_DOCKERFILE;
		}
		if (name.startsWith("dockerfile.")) {
			return SyntaxConstants.SYNTAX_STYLE_DOCKERFILE;
		}
		if (name.equals("makefile") || name.equals("gnumakefile") || name.equals("bsdmakefile")
				|| name.startsWith("makefile.")) {
			return SyntaxConstants.SYNTAX_STYLE_MAKEFILE;
		}
		if (name.equals(".htaccess")) {
			return SyntaxConstants.SYNTAX_STYLE_HTACCESS;
		}
		if (name.equals(".jshintrc")) {
			return SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS;
		}
		if (name.equals("hosts")) {
			return SyntaxConstants.SYNTAX_STYLE_HOSTS;
		}
		String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
		return switch (extension) {
			case "as" -> SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT;
			case "s", "asm", "a51" -> SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86;
			case "a65" -> SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_6502;
			case "bb" -> SyntaxConstants.SYNTAX_STYLE_BBCODE;
			case "clj" -> SyntaxConstants.SYNTAX_STYLE_CLOJURE;
			case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
			case "csv" -> SyntaxConstants.SYNTAX_STYLE_CSV;
			case "d" -> SyntaxConstants.SYNTAX_STYLE_D;
			case "dart" -> SyntaxConstants.SYNTAX_STYLE_DART;
			case "pas", "dpr" -> SyntaxConstants.SYNTAX_STYLE_DELPHI;
			case "dtd" -> SyntaxConstants.SYNTAX_STYLE_DTD;
			case "f", "for", "f90", "f95" -> SyntaxConstants.SYNTAX_STYLE_FORTRAN;
			case "hbs", "handlebars" -> SyntaxConstants.SYNTAX_STYLE_HANDLEBARS;
			case "hosts" -> SyntaxConstants.SYNTAX_STYLE_HOSTS;
			case "htm", "html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
			case "ini" -> SyntaxConstants.SYNTAX_STYLE_INI;
			case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
			case "jsonc" -> SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS;
			case "jsp" -> SyntaxConstants.SYNTAX_STYLE_JSP;
			case "tex", "latex" -> SyntaxConstants.SYNTAX_STYLE_LATEX;
			case "less" -> SyntaxConstants.SYNTAX_STYLE_LESS;
			case "lisp", "lsp" -> SyntaxConstants.SYNTAX_STYLE_LISP;
			case "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA;
			case "mk" -> SyntaxConstants.SYNTAX_STYLE_MAKEFILE;
			case "md", "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
			case "mxml" -> SyntaxConstants.SYNTAX_STYLE_MXML;
			case "nsi", "nsh" -> SyntaxConstants.SYNTAX_STYLE_NSIS;
			case "pl", "pm" -> SyntaxConstants.SYNTAX_STYLE_PERL;
			case "php" -> SyntaxConstants.SYNTAX_STYLE_PHP;
			case "ps1", "psm1" -> SyntaxConstants.SYNTAX_STYLE_POWERSHELL;
			case "proto" -> SyntaxConstants.SYNTAX_STYLE_PROTO;
			case "properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
			case "rb" -> SyntaxConstants.SYNTAX_STYLE_RUBY;
			case "sas" -> SyntaxConstants.SYNTAX_STYLE_SAS;
			case "scala" -> SyntaxConstants.SYNTAX_STYLE_SCALA;
			case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
			case "tcl" -> SyntaxConstants.SYNTAX_STYLE_TCL;
			case "ts", "tsx" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
			case "sh", "bash", "zsh" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
			case "vb" -> SyntaxConstants.SYNTAX_STYLE_VISUAL_BASIC;
			case "vhd", "vhdl" -> SyntaxConstants.SYNTAX_STYLE_VHDL;
			case "bat", "cmd" -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
			case "xml", "xsd", "xsl", "xslt" -> SyntaxConstants.SYNTAX_STYLE_XML;
			case "yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML;
			default -> SyntaxConstants.SYNTAX_STYLE_NONE;
		};
	}

}
