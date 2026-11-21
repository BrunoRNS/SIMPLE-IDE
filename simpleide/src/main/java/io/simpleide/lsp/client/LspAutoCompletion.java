package io.simpleide.lsp.client;

import io.simpleide.ui.editor.Editor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.CompletionProvider;

/**
 * Auto-completion controller that keeps completion popups consistent with the editor
 * theme.
 *
 * <p>
 * The controller follows background and foreground color changes on the editor, applies
 * the theme to the completion list and description window, and delegates insertion to its
 * superclass. When the completion is an LSP completion, additional text edits are
 * captured before insertion and applied afterwards by {@link LspCompletionEditApplier}.
 *
 * <p>
 * Covered by {@code LspAutoCompletionTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public class LspAutoCompletion extends AutoCompletion {

	private final Editor editor;

	private final PropertyChangeListener editorThemeListener;

	public LspAutoCompletion(Editor editor, CompletionProvider provider) {
		super(provider);
		this.editor = editor;
		this.editorThemeListener = event -> {
			String name = event.getPropertyName();
			if ("background".equals(name) || "foreground".equals(name)) {
				applyTheme();
			}
		};
		setListCellRenderer(new EditorThemeCompletionRenderer(editor));
		applyTheme();
		editor.addPropertyChangeListener("background", editorThemeListener);
		editor.addPropertyChangeListener("foreground", editorThemeListener);
	}

	@Override
	public void uninstall() {
		editor.removePropertyChangeListener("background", editorThemeListener);
		editor.removePropertyChangeListener("foreground", editorThemeListener);
		super.uninstall();
	}

	private void applyTheme() {
		Color background = editor.getBackground();
		if (background == null) {
			return;
		}
		setDescriptionWindowColor(background);
		applyDescriptionWindowColors(background, resolveForeground(background));
	}

	private Color resolveForeground(Color background) {
		Color foreground = editor.getForeground();
		if (foreground != null) {
			return foreground;
		}
		double luminance = 0.299d * background.getRed() + 0.587d * background.getGreen()
				+ 0.114d * background.getBlue();
		return luminance < 128d ? Color.WHITE : Color.BLACK;
	}

	private void applyDescriptionWindowColors(Color background, Color foreground) {
		try {
			Method method = AutoCompletion.class.getDeclaredMethod("getDescWindow");
			method.setAccessible(true);
			Object window = method.invoke(this);
			if (window instanceof Window descWindow) {
				styleDescriptionComponent(descWindow, background, foreground);
				descWindow.repaint();
			}
			else if (window instanceof JComponent descComponent) {
				styleDescriptionComponent(descComponent, background, foreground);
				descComponent.repaint();
			}
		}
		catch (ReflectiveOperationException | RuntimeException ignored) {
			// Best effort: not all AutoComplete versions expose the description window.
		}
	}

	private void styleDescriptionComponent(Component component, Color background, Color foreground) {
		component.setBackground(background);
		component.setForeground(foreground);
		if (component instanceof JEditorPane editorPane) {
			editorPane.setOpaque(true);
			editorPane.setBackground(background);
			editorPane.setForeground(foreground);
			editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		}
		else if (component instanceof JScrollPane scrollPane) {
			scrollPane.getViewport().setBackground(background);
			scrollPane.getViewport().setOpaque(true);
			Component view = scrollPane.getViewport().getView();
			if (view != null) {
				styleDescriptionComponent(view, background, foreground);
			}
		}
		else if (component instanceof JViewport viewport) {
			viewport.setBackground(background);
			viewport.setOpaque(true);
			Component view = viewport.getView();
			if (view != null) {
				styleDescriptionComponent(view, background, foreground);
			}
		}
		else if (component instanceof JComponent jComponent) {
			jComponent.setOpaque(true);
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				styleDescriptionComponent(child, background, foreground);
			}
		}
	}

	@Override
	protected void insertCompletion(Completion completion, boolean typedParamListStart) {
		if (!(completion instanceof LspCompletionProvider.LspCompletion lspCompletion)) {
			super.insertCompletion(completion, typedParamListStart);
			return;
		}

		int primaryEnd = editor.getCaretPosition();
		String alreadyEntered = completion.getAlreadyEntered(editor);
		if (alreadyEntered == null) {
			alreadyEntered = "";
		}
		int primaryStart = primaryEnd - alreadyEntered.length();
		int originalLength = editor.getDocument().getLength();
		List<LspCompletionEditApplier.ResolvedTextEdit> additionalEdits = LspCompletionEditApplier
			.captureAdditionalEdits(editor, lspCompletion.getItem(), primaryStart, primaryEnd);

		super.insertCompletion(completion, typedParamListStart);

		int primaryDelta = editor.getDocument().getLength() - originalLength;
		LspCompletionEditApplier.applyAdditionalEdits(editor, additionalEdits, primaryStart, primaryEnd, primaryDelta);
	}

	/**
	 * Completion cell renderer that copies the editor colors to the completion list.
	 *
	 * @author LuisOtavio13
	 * @since v1.0.0beta
	 */
	private static final class EditorThemeCompletionRenderer extends CompletionCellRenderer {

		private final Editor editor;

		EditorThemeCompletionRenderer(Editor editor) {
			this.editor = editor;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected,
				boolean cellHasFocus) {
			Color background = editor.getBackground();
			Color foreground = editor.getForeground();
			Color selectionBackground = editor.getSelectionColor();
			Color selectionForeground = editor.getSelectedTextColor();
			if (selectionBackground == null) {
				selectionBackground = background == null ? list.getSelectionBackground() : background.brighter();
			}
			if (selectionForeground == null) {
				selectionForeground = foreground == null ? list.getSelectionForeground() : foreground;
			}
			if (background != null) {
				list.setBackground(background);
				list.setOpaque(true);
			}
			if (foreground != null) {
				list.setForeground(foreground);
			}
			if (selectionBackground != null) {
				list.setSelectionBackground(selectionBackground);
			}
			if (selectionForeground != null) {
				list.setSelectionForeground(selectionForeground);
			}

			Component component = super.getListCellRendererComponent(list, value, index, selected, cellHasFocus);
			Color rowBackground = selected ? selectionBackground : background;
			Color rowForeground = selected ? selectionForeground : foreground;
			applyColors(component, rowBackground, rowForeground);
			return component;
		}

		private void applyColors(Component component, Color background, Color foreground) {
			if (component == null) {
				return;
			}
			if (background != null) {
				component.setBackground(background);
			}
			if (foreground != null) {
				component.setForeground(foreground);
			}
			if (component instanceof JComponent jComponent) {
				jComponent.setOpaque(true);
			}
			if (component instanceof java.awt.Container container) {
				for (Component child : container.getComponents()) {
					applyColors(child, background, foreground);
				}
			}
		}

	}

}
