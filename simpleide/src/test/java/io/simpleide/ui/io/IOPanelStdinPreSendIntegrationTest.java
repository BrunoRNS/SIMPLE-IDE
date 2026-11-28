package io.simpleide.ui.io;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;

/**
 * Integra\u00e7\u00e3o: verifica que ao iniciar uma execu\u00e7\u00e3o (Run ou Debug) o
 * conte\u00fado j\u00e1 digitado na \u00e1rea de input \u00e9 consumido e enviado como
 * stdin inicial ao programa, enquanto o usu\u00e1rio ainda pode digitar mais inputs via
 * Enter.
 */
class IOPanelStdinPreSendIntegrationTest {

	@Test
	void runSendsInputAreaContentAsInitialStdinThenClearsIt() throws Exception {
		drainInput();

		IOPanel panel = new IOPanel();
		TestLanguage language = new TestLanguage();

		panel.setInputText("primeira linha\nsegunda linha\n");
		panel.beginRun(language);

		assertThat(panel.isExecutionActive()).isTrue();
		assertThat(panel.isDebugMode()).isFalse();

		// O conte\u00fado da text area foi consumido como stdin inicial
		assertThat(Input.getInstance().get(1)).isEqualTo("primeira linha");
		assertThat(Input.getInstance().get(1)).isEqualTo("segunda linha");

		// A text area foi limpa ap\u00f3s o consumo
		assertThat(panel.getInputText()).isEmpty();

		// O usu\u00e1rio ainda pode digitar mais input e enviar com Enter
		panel.setInputText("linha extra");
		assertThat(panel.submitInput()).isTrue();
		assertThat(Input.getInstance().get(1)).isEqualTo("linha extra");

		panel.finishExecution();
		assertThat(panel.isExecutionActive()).isFalse();
	}

	@Test
	void debugSendsInputAreaContentAsInitialStdin() throws Exception {
		drainInput();

		IOPanel panel = new IOPanel();

		panel.setInputText("stdin para debug\n");
		panel.beginDebug(null);

		assertThat(panel.isExecutionActive()).isTrue();
		assertThat(panel.isDebugMode()).isTrue();

		assertThat(Input.getInstance().get(1)).isEqualTo("stdin para debug");
		assertThat(panel.getInputText()).isEmpty();

		panel.finishExecution();
		assertThat(panel.isExecutionActive()).isFalse();
	}

	@Test
	void runWithEmptyInputAreaSendsNothing() throws Exception {
		drainInput();

		IOPanel panel = new IOPanel();
		TestLanguage language = new TestLanguage();

		panel.beginRun(language);

		assertThat(panel.isExecutionActive()).isTrue();
		assertThat(Input.getInstance().get(0)).isNull();

		panel.finishExecution();
		assertThat(panel.isExecutionActive()).isFalse();
	}

	private static void drainInput() throws InterruptedException {
		while (Input.getInstance().get(0) != null) {
			// Drain lines left by other tests.
		}
	}

	private static final class TestLanguage extends AbstractLanguage {

	}

}
