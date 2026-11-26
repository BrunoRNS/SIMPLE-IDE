package io.simpleide.ui.editor;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.ui.treefile.SimpleIDEFile;
import io.simpleide.ui.treefile.TypeFile;

/**
 * Tests {@link io.simpleide.ui.editor.Editor}.
 */
class EditorTest {

	@TempDir
	Path tempDirectory;

	@Test
	void loadFileDisplaysItsContentsAndUsesSyntaxStylesForEveryLanguage() throws Exception {
		String[] extensions = { "java", "kt", "groovy", "js", "py", "cs", "cpp", "c", "go", "rs" };
		String[] syntaxStyles = { SyntaxConstants.SYNTAX_STYLE_JAVA, SyntaxConstants.SYNTAX_STYLE_KOTLIN,
				SyntaxConstants.SYNTAX_STYLE_GROOVY, SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT,
				SyntaxConstants.SYNTAX_STYLE_PYTHON, SyntaxConstants.SYNTAX_STYLE_CSHARP,
				SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS, SyntaxConstants.SYNTAX_STYLE_C, SyntaxConstants.SYNTAX_STYLE_GO,
				SyntaxConstants.SYNTAX_STYLE_RUST };
		SimpleIDEFile[] files = new SimpleIDEFile[extensions.length];
		for (int index = 0; index < extensions.length; index++) {
			files[index] = new SimpleIDEFile(
					Files.writeString(tempDirectory.resolve("main." + extensions[index]), "source"));
		}
		SimpleIDEFile textFile = new SimpleIDEFile(Files.writeString(tempDirectory.resolve("notes.txt"), "plain text"));

		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			for (int index = 0; index < files.length; index++) {
				editor.loadFile(files[index]);
				assertThat(editor.getSyntaxEditingStyle()).isEqualTo(syntaxStyles[index]);
			}
			editor.loadFile(textFile);

			assertThat(editor.getText()).isEqualTo("plain text");
			assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_NONE);
		});
	}

	@Test
	void mapsRsyntaxSupportedFileExtensionsWithoutEnablingLanguageAssistance() {
		String[][] extensionsAndStyles = { { "sample.as", SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT },
				{ "sample.asm", SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86 },
				{ "sample.a65", SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_6502 },
				{ "sample.bb", SyntaxConstants.SYNTAX_STYLE_BBCODE },
				{ "sample.clj", SyntaxConstants.SYNTAX_STYLE_CLOJURE },
				{ "sample.css", SyntaxConstants.SYNTAX_STYLE_CSS }, { "sample.csv", SyntaxConstants.SYNTAX_STYLE_CSV },
				{ "sample.d", SyntaxConstants.SYNTAX_STYLE_D },
				{ "Dockerfile", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE },
				{ "sample.dart", SyntaxConstants.SYNTAX_STYLE_DART },
				{ "sample.pas", SyntaxConstants.SYNTAX_STYLE_DELPHI },
				{ "sample.dtd", SyntaxConstants.SYNTAX_STYLE_DTD },
				{ "sample.f90", SyntaxConstants.SYNTAX_STYLE_FORTRAN },
				{ "sample.hbs", SyntaxConstants.SYNTAX_STYLE_HANDLEBARS },
				{ "hosts", SyntaxConstants.SYNTAX_STYLE_HOSTS }, { ".htaccess", SyntaxConstants.SYNTAX_STYLE_HTACCESS },
				{ "sample.html", SyntaxConstants.SYNTAX_STYLE_HTML },
				{ "sample.ini", SyntaxConstants.SYNTAX_STYLE_INI },
				{ "sample.json", SyntaxConstants.SYNTAX_STYLE_JSON },
				{ ".jshintrc", SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS },
				{ "sample.jsp", SyntaxConstants.SYNTAX_STYLE_JSP },
				{ "sample.tex", SyntaxConstants.SYNTAX_STYLE_LATEX },
				{ "sample.less", SyntaxConstants.SYNTAX_STYLE_LESS },
				{ "sample.lisp", SyntaxConstants.SYNTAX_STYLE_LISP },
				{ "sample.lua", SyntaxConstants.SYNTAX_STYLE_LUA },
				{ "Makefile", SyntaxConstants.SYNTAX_STYLE_MAKEFILE },
				{ "sample.md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN },
				{ "sample.mxml", SyntaxConstants.SYNTAX_STYLE_MXML },
				{ "sample.nsi", SyntaxConstants.SYNTAX_STYLE_NSIS }, { "sample.pl", SyntaxConstants.SYNTAX_STYLE_PERL },
				{ "sample.php", SyntaxConstants.SYNTAX_STYLE_PHP },
				{ "sample.ps1", SyntaxConstants.SYNTAX_STYLE_POWERSHELL },
				{ "sample.proto", SyntaxConstants.SYNTAX_STYLE_PROTO },
				{ "sample.properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE },
				{ "sample.rb", SyntaxConstants.SYNTAX_STYLE_RUBY }, { "sample.sas", SyntaxConstants.SYNTAX_STYLE_SAS },
				{ "sample.scala", SyntaxConstants.SYNTAX_STYLE_SCALA },
				{ "sample.sql", SyntaxConstants.SYNTAX_STYLE_SQL }, { "sample.tcl", SyntaxConstants.SYNTAX_STYLE_TCL },
				{ "sample.ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT },
				{ "sample.sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL },
				{ "sample.vb", SyntaxConstants.SYNTAX_STYLE_VISUAL_BASIC },
				{ "sample.vhd", SyntaxConstants.SYNTAX_STYLE_VHDL },
				{ "sample.cmd", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH },
				{ "sample.xml", SyntaxConstants.SYNTAX_STYLE_XML },
				{ "sample.yaml", SyntaxConstants.SYNTAX_STYLE_YAML } };
		for (String[] mapping : extensionsAndStyles) {
			assertThat(Editor.syntaxStyleForFile(mapping[0], TypeFile.TXT)).as("syntax for %s", mapping[0])
				.isEqualTo(mapping[1]);
		}
		assertThat(Editor.syntaxStyleForFile("notes.txt", TypeFile.TXT)).isEqualTo(SyntaxConstants.SYNTAX_STYLE_NONE);
		assertThat(Editor.syntaxStyleForFile("unknown.custom", TypeFile.TXT))
			.isEqualTo(SyntaxConstants.SYNTAX_STYLE_NONE);
		assertThat(LocalCompletionProviders.supports(SyntaxConstants.SYNTAX_STYLE_XML)).isFalse();
	}

	@Test
	void breakpointsAreRegisteredAndVisibleInTheEditorGutter() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Editor editor = new Editor();
			editor.setText("line one\nline two\nline three\n");
			RTextScrollPane scrollPane = new RTextScrollPane(editor);
			editor.attachGutter(scrollPane.getGutter());

			assertThat(scrollPane.getGutter().isIconRowHeaderEnabled()).isTrue();

			assertThat(editor.toggleBreakpoint(1)).isTrue();
			assertThat(editor.toggleBreakpoint(3)).isTrue();
			assertThat(editor.getBreakpointLines()).containsExactly(1, 3);

			assertThat(editor.toggleBreakpoint(1)).isFalse();
			assertThat(editor.getBreakpointLines()).containsExactly(3);

			editor.clearBreakpoints();
			assertThat(editor.getBreakpointLines()).isEmpty();
			assertThat(scrollPane.getGutter().isIconRowHeaderEnabled()).isTrue();
		});
	}

}
