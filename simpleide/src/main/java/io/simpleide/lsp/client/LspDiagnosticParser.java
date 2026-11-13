package io.simpleide.lsp.client;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;

import io.simpleide.thread.ThreadMaster;

public final class LspDiagnosticParser extends AbstractParser {
  private final RSyntaxTextArea editor;
  private volatile List<Diagnostic> diagnostics = List.of();

  public LspDiagnosticParser(RSyntaxTextArea editor) {
    this.editor = editor;
  }

  public void setDiagnostics(List<Diagnostic> diagnostics) {
    this.diagnostics = List.copyOf(diagnostics);
    ThreadMaster.getInstance().executeOnEDT(() -> editor.forceReparsing(this));
  }

  @SuppressWarnings("")
  @Override
  public ParseResult parse(RSyntaxDocument document, String style) {
    long startedAt = System.nanoTime();
    DefaultParseResult result = new DefaultParseResult(this);
    int lineCount = document.getDefaultRootElement().getElementCount();
    result.setParsedLines(0, Math.max(0, lineCount - 1));

    for (Diagnostic diagnostic : diagnostics) {
      Position start = diagnostic.getRange() == null ? null : diagnostic.getRange().getStart();
      if (start == null || lineCount == 0) {
        continue;
      }

      int line = Math.max(0, Math.min(start.getLine(), lineCount - 1));
      var lineElement = document.getDefaultRootElement().getElement(line);
      int lineStart = lineElement.getStartOffset();
      int lineEnd = lineElement.getEndOffset();
      int startOffset = Math.min(
          document.getLength(),
          Math.min(lineStart + Math.max(0, start.getCharacter()), lineEnd));

      int length = 1;
      Position end = diagnostic.getRange().getEnd();
      if (end != null && end.getLine() == line) {
        length = Math.max(1, end.getCharacter() - start.getCharacter());
      }
      length = Math.min(length, Math.max(1, document.getLength() - startOffset));

      String message = diagnostic.getMessage() == null ? "Language server diagnostic" : diagnostic.getMessage();
      DefaultParserNotice notice = new DefaultParserNotice(this, message, line, startOffset, length);
      notice.setLevel(toNoticeLevel(diagnostic.getSeverity()));
      notice.setToolTipText(message);
      notice.setShowInEditor(true);
      result.addNotice(notice);
    }

    result.setParseTime((System.nanoTime() - startedAt) / 1_000_000L);
    return result;
  }

  private ParserNotice.Level toNoticeLevel(DiagnosticSeverity severity) {
    if (severity == DiagnosticSeverity.Error) {
      return ParserNotice.Level.ERROR;
    }
    if (severity == DiagnosticSeverity.Warning) {
      return ParserNotice.Level.WARNING;
    }
    return ParserNotice.Level.INFO;
  }
}
