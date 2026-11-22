package io.simpleide.ui.sidebar;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JList;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.core.Document;
import io.simpleide.ui.editor.EditorTab;
import io.simpleide.ui.treefile.File;

class DebugConsolePanelTest {

	@TempDir
	Path root;

	@Test
	void removesResolvedDiagnosticsWhenTheTabRefreshes() throws Exception {
		Path path = Files.writeString(root.resolve("Main.java"), "class Main {}");
		AtomicReference<List<Diagnostic>> currentDiagnostics = new AtomicReference<>(
				List.of(new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "Unexpected token")));
		AtomicReference<DebugConsolePanel> panelReference = new AtomicReference<>();
		AtomicReference<EditorTab> tabReference = new AtomicReference<>();
		AtomicReference<JList<?>> listReference = new AtomicReference<>();

		SwingUtilities.invokeAndWait(() -> {
			try {
				EditorTab tab = new EditorTab(new File(path), new Document(path, "java")) {
					@Override
					public List<Diagnostic> getDiagnostics() {
						return currentDiagnostics.get();
					}
				};
				tabReference.set(tab);
				DebugConsolePanel panel = new DebugConsolePanel();
				panel.showTab(tab);
				panelReference.set(panel);
				listReference.set(findList(panel));
			}
			catch (Exception error) {
				throw new AssertionError(error);
			}
		});

		assertThat(listReference.get().getModel().getSize()).isEqualTo(1);
		currentDiagnostics.set(List.of());
		SwingUtilities.invokeAndWait(() -> panelReference.get().refresh(tabReference.get()));
		assertThat(listReference.get().getModel().getSize()).isZero();
	}

	private static JList<?> findList(Container container) {
		for (Component child : container.getComponents()) {
			if (child instanceof JList<?> list) {
				return list;
			}
			if (child instanceof Container nested) {
				JList<?> result = findList(nested);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

}
