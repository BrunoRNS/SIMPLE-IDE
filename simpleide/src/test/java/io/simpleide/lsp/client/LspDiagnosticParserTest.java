package io.simpleide.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.simpleide.ui.editor.Editor;
import javax.swing.SwingUtilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.junit.jupiter.api.Test;

class LspDiagnosticParserTest {

  @Test
  void mapsLspDiagnosticRangeToRsyntaxNotice() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Editor editor = new Editor();
          editor.setText("first\nsecond");
          LspDiagnosticParser parser = new LspDiagnosticParser(editor);
          Diagnostic diagnostic =
              new Diagnostic(new Range(new Position(1, 1), new Position(1, 3)), "Unexpected token");
          diagnostic.setSeverity(DiagnosticSeverity.Error);
          parser.setDiagnostics(java.util.List.of(diagnostic));

          var result =
              parser.parse((RSyntaxDocument) editor.getDocument(), editor.getSyntaxEditingStyle());

          assertThat(result.getNotices()).hasSize(1);
          ParserNotice notice = result.getNotices().get(0);
          assertThat(notice.getLine()).isEqualTo(1);
          assertThat(notice.getOffset()).isEqualTo(7);
          assertThat(notice.getLength()).isEqualTo(2);
          assertThat(notice.getLevel()).isEqualTo(ParserNotice.Level.ERROR);
          assertThat(notice.getMessage()).isEqualTo("Unexpected token");
        });
  }
}
