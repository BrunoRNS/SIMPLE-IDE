package io.simpleide;

import io.simpleide.frame.MainFrame;
import io.simpleide.language.config.LangConfigHolder;
import io.simpleide.language.config.LangSetup;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

/**
 * Application bootstrap for the Simple IDE desktop client.
 *
 * <p>
 * The entry point prepares the shared language configuration and starts the managed
 * thread tree. The main window is created on the Swing Event Dispatch Thread through an
 * {@code EDTNode}, so the application follows the same lifecycle rules as the rest of the
 * IDE.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/application.md">
 * docs/api/ui/application.md</a>
 */
public class App {

	/**
	 * Prepares the language configuration and launches the main application window.
	 * @param args command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		new LangSetup(LangConfigHolder.LANG_CONFIG).run();

		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.register(new EDTNode(() -> new MainFrame().run()));
		threadMaster.initialize();
	}

}
