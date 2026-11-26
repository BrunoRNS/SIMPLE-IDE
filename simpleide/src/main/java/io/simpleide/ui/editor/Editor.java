package io.simpleide.ui.editor;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;

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
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/rsyntax.md">docs/api/rsyntax.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class Editor extends RSyntaxTextArea {

	private final TreeSet<Integer> breakpointLines = new TreeSet<>();

	private final Map<Integer, GutterIconInfo> breakpointIcons = new LinkedHashMap<>();

	private Consumer<List<Integer>> breakpointListener = ignored -> {
	};

	private Gutter gutter;

	private int hoverLine;

	/**
	 * Creates an empty editor with the application font and editing defaults.
	 */
	public Editor() {
		super();
		applyUiFont();
		config();
	}

	/**
	 * Attaches the gutter used to paint breakpoint markers.
	 * @param gutter gutter of the scroll pane that hosts this editor
	 */
	public void attachGutter(Gutter gutter) {
		this.gutter = gutter;
		if (gutter != null) {
			gutter.setIconRowHeaderEnabled(true);
		}
		repaintBreakpoints();
	}

	/**
	 * Returns the one-based line numbers with a breakpoint, in ascending order.
	 * @return an immutable snapshot of the breakpoint lines
	 */
	public List<Integer> getBreakpointLines() {
		return List.copyOf(breakpointLines);
	}

	/**
	 * Returns the one-based line currently under the pointer in the gutter, or {@code 0}
	 * when the pointer is not over a line.
	 * @return the hovered line, or {@code 0}
	 */
	public int getHoverLine() {
		return hoverLine;
	}

	/**
	 * Updates the line shown with a translucent marker while the pointer is over the
	 * gutter. A value of {@code 0} clears the marker.
	 * @param line one-based line number, or {@code 0} to clear
	 */
	public void setHoverLine(int line) {
		int clamped = line < 1 || line > getLineCount() ? 0 : line;
		if (hoverLine == clamped) {
			return;
		}
		hoverLine = clamped;
		repaintBreakpoints();
	}

	/**
	 * Toggles a breakpoint on a one-based line.
	 * @param line one-based line number
	 * @return {@code true} when a breakpoint now exists on the line
	 */
	public boolean toggleBreakpoint(int line) {
		if (line < 1 || line > getLineCount()) {
			return false;
		}
		boolean added;
		if (breakpointLines.remove(line)) {
			added = false;
		}
		else {
			breakpointLines.add(line);
			added = true;
		}
		repaintBreakpoints();
		breakpointListener.accept(getBreakpointLines());
		return added;
	}

	/**
	 * Toggles a breakpoint on the line that contains the caret.
	 * @return {@code true} when a breakpoint now exists on the caret line
	 */
	public boolean toggleBreakpointAtCaret() {
		try {
			return toggleBreakpoint(getLineOfOffset(getCaretPosition()) + 1);
		}
		catch (javax.swing.text.BadLocationException error) {
			return false;
		}
	}

	/**
	 * Removes every breakpoint from the editor.
	 */
	public void clearBreakpoints() {
		breakpointLines.clear();
		repaintBreakpoints();
		breakpointListener.accept(getBreakpointLines());
	}

	/**
	 * Registers a listener notified when breakpoints change.
	 * @param listener listener invoked with the current breakpoint lines, or {@code null}
	 * to clear it
	 */
	public void setBreakpointListener(Consumer<List<Integer>> listener) {
		breakpointListener = listener == null ? ignored -> {
		} : listener;
	}

	private void repaintBreakpoints() {
		Gutter activeGutter = gutter;
		if (activeGutter == null) {
			return;
		}
		activeGutter.setIconRowHeaderEnabled(true);
		for (GutterIconInfo icon : breakpointIcons.values()) {
			activeGutter.removeTrackingIcon(icon);
		}
		breakpointIcons.clear();
		Icon selectedIcon = new BreakpointIcon(true);
		for (Integer line : breakpointLines) {
			addBreakpointIcon(activeGutter, line, selectedIcon);
		}
		int hovered = hoverLine;
		if (hovered > 0 && !breakpointLines.contains(hovered)) {
			addBreakpointIcon(activeGutter, hovered, new BreakpointIcon(false));
		}
	}

	private void addBreakpointIcon(Gutter activeGutter, int line, Icon icon) {
		try {
			GutterIconInfo info = activeGutter.addLineTrackingIcon(line - 1, icon, "Breakpoint at line " + line);
			breakpointIcons.put(line, info);
		}
		catch (javax.swing.text.BadLocationException ignored) {
			// The line disappeared while the editor was reloading.
		}
	}

	/**
	 * Paints the breakpoint dot shown in the gutter.
	 *
	 * <p>
	 * A selected breakpoint is a solid red dot; the translucent variant is shown while
	 * the pointer hovers a line that has no breakpoint yet.
	 *
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	private static final class BreakpointIcon implements Icon {

		private static final int SIZE = 12;

		private static final java.awt.Color SELECTED = new java.awt.Color(0xE5142B);

		private static final java.awt.Color SELECTED_BORDER = new java.awt.Color(0x8B1A23);

		private static final java.awt.Color HOVERED = new java.awt.Color(0x88E5142B, true);

		private final boolean selected;

		private BreakpointIcon(boolean selected) {
			this.selected = selected;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			graphics.setColor(selected ? SELECTED : HOVERED);
			graphics.fillOval(x + 1, y + 1, SIZE, SIZE);
			if (selected) {
				graphics.setColor(SELECTED_BORDER);
				graphics.drawOval(x + 1, y + 1, SIZE, SIZE);
			}
		}

		@Override
		public int getIconWidth() {
			return SIZE + 2;
		}

		@Override
		public int getIconHeight() {
			return SIZE + 2;
		}

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
