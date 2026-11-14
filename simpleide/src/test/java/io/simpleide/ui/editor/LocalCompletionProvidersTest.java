package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.simpleide.lsp.dowload.LspManager;
import io.simpleide.lsp.dowload.LspServer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;

class LocalCompletionProvidersTest {

  private static final Map<String, String> EDITOR_LANGUAGE_STYLES =
      Map.ofEntries(
          Map.entry("java", SyntaxConstants.SYNTAX_STYLE_JAVA),
          Map.entry("csharp", SyntaxConstants.SYNTAX_STYLE_CSHARP),
          Map.entry("groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY),
          Map.entry("kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN),
          Map.entry("javascript", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
          Map.entry("c", SyntaxConstants.SYNTAX_STYLE_C),
          Map.entry("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
          Map.entry("python", SyntaxConstants.SYNTAX_STYLE_PYTHON),
          Map.entry("go", SyntaxConstants.SYNTAX_STYLE_GO),
          Map.entry("rust", SyntaxConstants.SYNTAX_STYLE_RUST));

  private static final Map<String, String> EXPECTED_KEYWORDS =
      Map.ofEntries(
          Map.entry(SyntaxConstants.SYNTAX_STYLE_JAVA, "class"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_CSHARP, "namespace"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_GROOVY, "def"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_KOTLIN, "fun"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT, "function"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_C, "struct"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS, "namespace"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_PYTHON, "def"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_GO, "func"),
          Map.entry(SyntaxConstants.SYNTAX_STYLE_RUST, "fn"));

  @Test
  void providesKeywordCompletionsForEveryEditorLanguage() {
    EXPECTED_KEYWORDS.forEach(
        (syntaxStyle, expectedKeyword) -> {
          assertThat(LocalCompletionProviders.supports(syntaxStyle)).isTrue();

          Editor editor = new Editor();
          editor.setSyntaxEditingStyle(syntaxStyle);

          assertThat(LocalCompletionProviders.forSyntaxStyle(syntaxStyle).getCompletions(editor))
              .extracting(Completion::getInputText)
              .contains(expectedKeyword);
        });
  }

  @Test
  void coversEveryEditorLanguageWithoutAWindowsX86Server() {
    String originalOsName = System.getProperty("os.name");
    String originalOsArch = System.getProperty("os.arch");
    try {
      System.setProperty("os.name", "Windows 11");
      System.setProperty("os.arch", "x86");

      Set<String> serverLanguages =
          LspManager.getRegistry().getServers().stream()
              .filter(LspServer::isSupportedOnCurrentPlatform)
              .map(LspServer::getLanguageId)
              .collect(Collectors.toSet());

      List<String> fallbackLanguages =
          EDITOR_LANGUAGE_STYLES.keySet().stream()
              .filter(language -> !serverLanguages.contains(language))
              .toList();

      assertThat(fallbackLanguages).contains("java", "javascript", "python", "c", "cpp", "groovy");
      fallbackLanguages.forEach(
          language ->
              assertThat(LocalCompletionProviders.supports(EDITOR_LANGUAGE_STYLES.get(language)))
                  .as(language)
                  .isTrue());
    } finally {
      System.setProperty("os.name", originalOsName);
      System.setProperty("os.arch", originalOsArch);
    }
  }

  @Test
  void hasNoFallbackForPlainText() {
    assertThat(LocalCompletionProviders.supports(SyntaxConstants.SYNTAX_STYLE_NONE)).isFalse();
    assertThat(LocalCompletionProviders.forSyntaxStyle(SyntaxConstants.SYNTAX_STYLE_NONE)).isNull();
  }

  @Test
  void installsKeywordCompletionWithoutAHeadfulToolkit() {
    Editor editor = new Editor();
    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
    AutoCompletion completion =
        new AutoCompletion(
            LocalCompletionProviders.forSyntaxStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON));

    assertThatCode(() -> completion.install(editor)).doesNotThrowAnyException();
    assertThatCode(completion::uninstall).doesNotThrowAnyException();
  }
}
