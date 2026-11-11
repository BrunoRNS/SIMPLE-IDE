package io.simpleide.ui.editor;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

final class EditorSearchPanel extends JPanel {
  private final Editor editor;
  private final JTextField searchField = new JTextField(22);
  private final JTextField replacementField = new JTextField(18);
  private final JPanel replacementRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
  private final JLabel status = new JLabel(" ");

  EditorSearchPanel(Editor editor) {
    super(new java.awt.GridLayout(0, 1));
    this.editor = editor;
    setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

    JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
    searchRow.add(new JLabel("Find:"));
    searchRow.add(searchField);
    JButton previousButton = new JButton("Previous");
    previousButton.addActionListener(event -> find(searchField.getText(), false));
    searchRow.add(previousButton);
    JButton nextButton = new JButton("Next");
    nextButton.addActionListener(event -> find(searchField.getText(), true));
    searchRow.add(nextButton);
    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(event -> close());
    searchRow.add(closeButton);
    add(searchRow);

    replacementRow.add(new JLabel("Replace with:"));
    replacementRow.add(replacementField);
    JButton replaceButton = new JButton("Replace");
    replaceButton.addActionListener(event -> replaceOne(searchField.getText(), replacementField.getText()));
    replacementRow.add(replaceButton);
    JButton replaceAllButton = new JButton("Replace all");
    replaceAllButton.addActionListener(event -> replaceAll(searchField.getText(), replacementField.getText()));
    replacementRow.add(replaceAllButton);
    replacementRow.setVisible(false);
    add(replacementRow);

    add(status);
    bindKey(searchField, "editorSearch.next", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        () -> find(searchField.getText(), true));
    bindKey(searchField, "editorSearch.previous", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK),
        () -> find(searchField.getText(), false));
    bindKey(replacementField, "editorSearch.replace", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        () -> replaceOne(searchField.getText(), replacementField.getText()));
    bindKey(searchField, "editorSearch.find", KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK),
        () -> open(false));
    bindKey(replacementField, "editorSearch.find", KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK),
        () -> open(false));
    bindKey(searchField, "editorSearch.showReplace", KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK),
        () -> open(true));
    bindKey(replacementField, "editorSearch.showReplace",
        KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK), () -> open(true));
    bindKey(searchField, "editorSearch.close", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::close);
    bindKey(replacementField, "editorSearch.close", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::close);
    setVisible(false);
  }

  void open(boolean showReplacement) {
    replacementRow.setVisible(showReplacement);
    String selectedText = editor.getSelectedText();
    if (selectedText != null && !selectedText.isEmpty()) {
      searchField.setText(selectedText);
      searchField.selectAll();
    }
    status.setText(" ");
    setVisible(true);
    revalidate();
    if (getParent() != null) {
      getParent().revalidate();
    }
    searchField.requestFocusInWindow();
  }

  SearchResult find(String query, boolean forward) {
    if (query == null || query.isEmpty()) {
      status.setText("Enter text to search.");
      return null;
    }
    SearchContext context = searchContext(query, null, forward);
    SearchResult result = SearchEngine.find(editor, context);
    status.setText(result.wasFound() ? "Occurrence found." : "No occurrences found.");
    return result;
  }

  SearchResult replaceOne(String query, String replacement) {
    if (query == null || query.isEmpty()) {
      status.setText("Enter text to search.");
      return null;
    }
    SearchResult result = SearchEngine.replace(editor, searchContext(query, replacement, true));
    status.setText(result.wasFound() ? "Occurrence replaced." : "No occurrences found.");
    return result;
  }

  SearchResult replaceAll(String query, String replacement) {
    if (query == null || query.isEmpty()) {
      status.setText("Enter text to search.");
      return null;
    }
    SearchResult result = SearchEngine.replaceAll(editor, searchContext(query, replacement, true));
    status.setText(result.getCount() + " occurrence(s) replaced.");
    return result;
  }

  void setSearchText(String text) {
    searchField.setText(text);
  }

  void setReplacementText(String text) {
    replacementField.setText(text);
  }

  private SearchContext searchContext(String query, String replacement, boolean forward) {
    SearchContext context = new SearchContext(query);
    context.setReplaceWith(replacement == null ? "" : replacement);
    context.setSearchForward(forward);
    context.setSearchWrap(true);
    return context;
  }

  private void bindKey(JTextField field, String name, KeyStroke keyStroke, Runnable action) {
    field.getInputMap().put(keyStroke, name);
    field.getActionMap().put(name, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent event) {
        action.run();
      }
    });
  }

  private void close() {
    setVisible(false);
    editor.requestFocusInWindow();
  }
}
