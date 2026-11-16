package io.simpleide.lsp.client;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.utils.JavaRuntime;

/**
 * Tests {@link io.simpleide.lsp.client.LspClient}.
 */
class LspClientShutdownTest {

	@BeforeAll
	@SuppressWarnings("unused")
	static void initializeThreadManager() {
		ThreadMaster.getInstance().initialize();
	}

	@Test
	@SuppressWarnings("ConvertToTryWithResources")
	void waitsForShutdownToFinishAfterClose() throws Exception {
		Path javaExecutable = JavaRuntime.javaExecutable().orElseThrow();
		LspClient client = new LspClient(List.of(javaExecutable.toString(), "-cp",
				System.getProperty("java.class.path"), SleepingProcess.class.getName()), null, null);
		try {
			await().atMost(10, TimeUnit.SECONDS).until(client::isProcessAlive);

			client.close();

			assertThat(client.awaitShutdown(10, TimeUnit.SECONDS)).isTrue();
			assertThat(client.isProcessAlive()).isFalse();
		}
		finally {
			client.close();
			client.awaitShutdown(2, TimeUnit.SECONDS);
		}
	}

	static final class SleepingProcess {

		public static void main(String[] args) throws InterruptedException {
			Thread.sleep(60_000);
		}

	}

}
