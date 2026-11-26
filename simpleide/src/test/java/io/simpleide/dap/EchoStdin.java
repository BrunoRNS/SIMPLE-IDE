package io.simpleide.dap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Small program debugged by {@link FakeDapServer} in the DAP integration tests.
 *
 * <p>
 * The program echoes every line read from standard input and exits on {@code quit}, so a
 * test can prove that the IDE forwards typed input to a live debuggee and that its output
 * reaches the I/O tab.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class EchoStdin {

	private EchoStdin() {
	}

	/**
	 * Reads standard input until it is closed or the line {@code quit} arrives.
	 * @param args ignored
	 * @throws IOException when standard input cannot be read
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static void main(String[] args) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println("ECHO: " + line);
			System.out.flush();
			if ("quit".equals(line.strip())) {
				return;
			}
		}
	}

}
