package io.simpleide.ui.io;

/**
 * Long-running program used by {@link IOPanelRunStopIntegrationTest} to prove that the
 * I/O tab stops a program that runs without debugging.
 *
 * <p>
 * The process prints one line and then stays alive until the IDE kills it, so the test
 * can press Stop and observe the interruption.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class RunStopProbe {

	private RunStopProbe() {
	}

	/**
	 * Prints a marker and waits to be killed.
	 * @param args ignored
	 * @throws InterruptedException when the wait is interrupted
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static void main(String[] args) throws InterruptedException {
		System.out.println("PROBE started");
		System.out.flush();
		Thread.sleep(120_000L);
	}

}
