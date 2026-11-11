package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class EditorSearchPanelTest {

  @Test
  void findsAndReplacesTextInTheEditor() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Editor editor = new Editor();
      editor.setText("alpha beta alpha");
      EditorSearchPanel panel = new EditorSearchPanel(editor);

      assertThat(panel.find("beta", true).wasFound()).isTrue();
      assertThat(editor.getSelectedText()).isEqualTo("beta");

      assertThat(panel.replaceOne("beta", "gamma").wasFound()).isTrue();
      assertThat(editor.getText()).isEqualTo("alpha gamma alpha");

      assertThat(panel.replaceAll("alpha", "x").getCount()).isEqualTo(2);
      assertThat(editor.getText()).isEqualTo("x gamma x");
    });
  }

  @Test
  void usesEnglishLabelsForTheSearchPanel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Editor editor = new Editor();
      EditorSearchPanel panel = new EditorSearchPanel(editor);

      JPanel searchRow = (JPanel) panel.getComponent(0);
      JLabel findLabel = (JLabel) searchRow.getComponent(0);
      assertThat(findLabel.getText()).isEqualTo("Find:");
      assertThat(((javax.swing.JButton) searchRow.getComponent(2)).getText()).isEqualTo("Previous");
      assertThat(((javax.swing.JButton) searchRow.getComponent(3)).getText()).isEqualTo("Next");
      assertThat(((javax.swing.JButton) searchRow.getComponent(4)).getText()).isEqualTo("Close");

      JPanel replacementRow = (JPanel) panel.getComponent(1);
      JLabel replaceLabel = (JLabel) replacementRow.getComponent(0);
      assertThat(replaceLabel.getText()).isEqualTo("Replace with:");
      assertThat(((javax.swing.JButton) replacementRow.getComponent(2)).getText()).isEqualTo("Replace");
      assertThat(((javax.swing.JButton) replacementRow.getComponent(3)).getText()).isEqualTo("Replace all");
    });
  }

  @Test
  void emptySearchDoesNotModifyTheDocument() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Editor editor = new Editor();
      editor.setText("unchanged");
      EditorSearchPanel panel = new EditorSearchPanel(editor);

      assertThat(panel.find("", true)).isNull();
      assertThat(panel.replaceAll("", "changed")).isNull();
      assertThat(editor.getText()).isEqualTo("unchanged");
    });
  }
}
