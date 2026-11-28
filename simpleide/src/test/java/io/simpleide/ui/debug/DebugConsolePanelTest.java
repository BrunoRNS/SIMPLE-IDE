package io.simpleide.ui.debug;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JList;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.core.Document;
import io.simpleide.ui.editor.EditorTab;
import io.simpleide.ui.treefile.SimpleIDEFile;

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
				EditorTab tab = new EditorTab(new SimpleIDEFile(path), new Document(path, "java")) {
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
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});

		assertThat(listReference.get().getModel().getSize()).isEqualTo(1);
		currentDiagnostics.set(List.of());
		SwingUtilities.invokeAndWait(() -> panelReference.get().refresh(tabReference.get()));
		assertThat(listReference.get().getModel().getSize()).isZero();
	}

	@Test
	void filtersDiagnosticsBySeverity() throws Exception {
		Path path = Files.writeString(root.resolve("Main.java"), "class Main {}");
		List<Diagnostic> currentDiagnostics = List.of(diagnostic(DiagnosticSeverity.Error, "Syntax error"),
				diagnostic(DiagnosticSeverity.Warning, "Unused variable"));
		AtomicReference<JList<?>> listReference = new AtomicReference<>();
		AtomicReference<JToggleButton> warningFilter = new AtomicReference<>();

		SwingUtilities.invokeAndWait(() -> {
			try {
				EditorTab tab = new EditorTab(new SimpleIDEFile(path), new Document(path, "java")) {
					@Override
					public List<Diagnostic> getDiagnostics() {
						return currentDiagnostics;
					}
				};
				DebugConsolePanel panel = new DebugConsolePanel();
				panel.showTab(tab);
				listReference.set(findList(panel));
				warningFilter.set(findToggle(panel, "Warnings"));
			}
			catch (IOException error) {
				throw new AssertionError(error);
			}
		});

		assertThat(listReference.get().getModel().getSize()).isEqualTo(2);
		SwingUtilities.invokeAndWait(() -> warningFilter.get().doClick());
		await().atMost(5, TimeUnit.SECONDS)
			.untilAsserted(() -> assertThat(listReference.get().getModel().getSize()).isEqualTo(1));
	}

	private static Diagnostic diagnostic(DiagnosticSeverity severity, String message) {
		Diagnostic diagnostic = new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), message);
		diagnostic.setSeverity(severity);
		return diagnostic;
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

	private static JToggleButton findToggle(Container container, String label) {
		for (Component child : container.getComponents()) {
			if (child instanceof JToggleButton button && button.getText().startsWith(label)) {
				return button;
			}
			if (child instanceof Container nested) {
				JToggleButton result = findToggle(nested, label);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

}
