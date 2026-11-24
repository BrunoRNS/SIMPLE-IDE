package io.simpleide.ui.io;

import java.awt.Color;

import javax.swing.SwingUtilities;
import javax.swing.text.StyledDocument;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;

/**
 * Tests {@link io.simpleide.ui.io.IOPanel}.
 */
class IOPanelTest {

	@Test
	void coloursAndPrefixesEveryOutputType() throws Exception {
		IOPanel panel = new IOPanel();

		SwingUtilities.invokeAndWait(() -> {
			panel.appendLine(OutputType.OUTPUT, "hello");
			panel.appendLine(OutputType.ERROR, "broken");
			panel.appendLine(OutputType.DEBUG, "diagnostic");
		});

		String output = panel.getOutputText();
		assertThat(output).contains("[OUTPUT] hello").contains("[ERROR] broken").contains("[DEBUG] diagnostic");

		StyledDocument document = panel.getOutputPane().getStyledDocument();
		Color errorColor = (Color) document.getCharacterElement(output.indexOf("[ERROR]"))
			.getAttributes()
			.getAttribute(javax.swing.text.StyleConstants.Foreground);
		Color debugColor = (Color) document.getCharacterElement(output.indexOf("[DEBUG]"))
			.getAttributes()
			.getAttribute(javax.swing.text.StyleConstants.Foreground);
		assertThat(errorColor).isNotNull();
		assertThat(debugColor).isNotNull();
	}

	@Test
	void redirectsProgramOutputAndInputWhileRunning() throws Exception {
		IOPanel panel = new IOPanel();
		TestLanguage language = new TestLanguage();
		drainInput();

		panel.beginRun(language);
		assertThat(panel.isExecutionActive()).isTrue();
		assertThat(panel.isDebugMode()).isFalse();

		Output.getInstance().publish(OutputType.OUTPUT, "program line");
		SwingUtilities.invokeAndWait(() -> {
		});
		assertThat(panel.getOutputText()).contains("[OUTPUT] program line");

		panel.setInputText("typed answer");
		assertThat(panel.submitInput()).isTrue();
		assertThat(Input.getInstance().get(1)).isEqualTo("typed answer");

		panel.finishExecution();
		assertThat(panel.isExecutionActive()).isFalse();
		String outputBeforeLaterEvent = panel.getOutputText();
		Output.getInstance().publish(OutputType.OUTPUT, "after program finished");
		SwingUtilities.invokeAndWait(() -> {
		});
		assertThat(panel.getOutputText()).isEqualTo(outputBeforeLaterEvent);
	}

	@Test
	void showsDebugControlsOnlyInDebugMode() {
		IOPanel panel = new IOPanel();
		assertThat(panel.getDebugControls().isVisible()).isFalse();

		panel.beginDebug(null);
		assertThat(panel.isDebugMode()).isTrue();
		assertThat(panel.getDebugControls().isVisible()).isTrue();
		assertThat(panel.getDebugControls().getComponentCount()).isEqualTo(6);

		panel.finishExecution();
		assertThat(panel.isDebugMode()).isFalse();
		assertThat(panel.getDebugControls().isVisible()).isFalse();
	}

	private static void drainInput() throws InterruptedException {
		while (Input.getInstance().get(0) != null) {
			// Drain lines left by other tests.
		}
	}

	private static final class TestLanguage extends AbstractLanguage {

	}

}
