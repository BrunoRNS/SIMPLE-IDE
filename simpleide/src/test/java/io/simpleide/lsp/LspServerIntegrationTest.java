package io.simpleide.lsp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.dowload.LspManager;
import io.simpleide.thread.ThreadMaster;

/**
 * Starts every bundled language server and checks the LSP handshake and completion.
 *
 * <p>
 * Each test resolves the same install and launch command the IDE uses, starts the real
 * server as a subprocess, waits for the {@code initialize} response and opens a real
 * document before requesting completion. The tests are tagged {@code language-tools} and
 * {@code lsp}, and skip when the language runtime or the platform-specific server is not
 * available, matching the debug adapter integration tests.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("language-tools")
@Tag("lsp")
class LspServerIntegrationTest {

	private static final String JAVA_SOURCE = """
			public class Main {
			    public static void main(String[] args) {
			        String text = "a";
			        System.out.println(text.length());
			    }
			}
			""";

	private static final String JAVASCRIPT_SOURCE = """
			const value = 1;
			console.log(value);
			""";

	private static final String PYTHON_SOURCE = """
			value = "text"
			value.upper()
			""";

	private static final String GO_SOURCE = """
			package main

			import "fmt"

			func main() {
				fmt.Println("hi")
			}
			""";

	private static final String KOTLIN_SOURCE = """
			fun main() {
			    val text = "a"
			    println(text.length)
			}
			""";

	private static final String GROOVY_SOURCE = """
			def text = "a"
			println(text.length())
			""";

	private static final String C_SOURCE = """
			#include <string.h>

			int main(void) {
			    char *text = "a";
			    strlen
			    return 0;
			}
			""";

	private static final String CPP_SOURCE = """
			#include <string>

			int main() {
			    std::string text = "a";
			    text.
			    return 0;
			}
			""";

	private static final String RUST_SOURCE = """
			fn main() {
			    let text = "a";
			    println!("{}", text.len());
			}
			""";

	private static final String CSHARP_SOURCE = """
			class Program {
			    static void Main() {
			        string text = "a";
			        text.
			    }
			}
			""";

	@BeforeAll
	@SuppressWarnings("unused")
	static void initializeThreadManager() {
		ThreadMaster.getInstance().initialize();
	}

	// The servers keep the folder they index open, and Windows refuses to delete a
	// directory with open files, so the temporary workspace is left behind.
	@TempDir(cleanup = CleanupMode.NEVER)
	Path workspace;

	@Test
	void initializesJavaAndAnswersCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("java", "-version");
		Path source = Files.writeString(workspace.resolve("Main.java"), JAVA_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("java", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "java", source, JAVA_SOURCE, new Position(3, 15));
		}
	}

	@Test
	void reportsSyntaxErrorsInALooseJavaFile() throws Exception {
		LspIntegrationHarness.requireToolchain("java", "-version");
		String source = """
				public class Broken {
				    void run() {
				        this is not java
				    }
				}
				""";
		Path file = Files.writeString(workspace.resolve("Broken.java"), source);
		List<String> diagnostics = new CopyOnWriteArrayList<>();

		List<String> command = LspManager.getComandRun("java", workspace);
		assumeTrue(!command.isEmpty(), "No Java language server is registered for this platform");
		try (LspClient client = new LspClient(command, workspace,
				params -> params.getDiagnostics().forEach(diagnostic -> diagnostics.add(diagnostic.getMessage())),
				Map.of(), null, Map.of())) {
			client.getInitialized().get(180, TimeUnit.SECONDS);
			client.didOpen(new TextDocumentItem(file.toUri().toString(), "java", 1, source));

			await().atMost(60, TimeUnit.SECONDS)
				.pollInterval(1, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(diagnostics.isEmpty() ? null : diagnostics.get(0))
					.as("syntax error reported for the loose Java file")
					.isNotNull());
		}
	}

	@Test
	void initializesJavaScriptAndAnswersCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("node", "--version");
		Path source = Files.writeString(workspace.resolve("app.js"), JAVASCRIPT_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("javascript", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "javascript", source, JAVASCRIPT_SOURCE,
					new Position(1, 8));
		}
	}

	@Test
	void initializesPythonAndAnswersCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("node", "--version");
		Path source = Files.writeString(workspace.resolve("app.py"), PYTHON_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("python", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "python", source, PYTHON_SOURCE, new Position(1, 6));
		}
	}

	@Test
	void initializesGoAndAnswersCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("go", "version");
		Files.writeString(workspace.resolve("go.mod"), "module lspinspection\n\ngo 1.21\n");
		Path source = Files.writeString(workspace.resolve("main.go"), GO_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("go", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "go", source, GO_SOURCE, new Position(5, 5));
		}
	}

	@Test
	void initializesKotlinAndAnswersCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("java", "-version");
		int javaFeature = Runtime.version().feature();
		assumeTrue(javaFeature < 25,
				"kotlin-language-server 1.3.13 bundles a compiler that cannot parse the version string of JDK "
						+ javaFeature + " (IllegalArgumentException in JavaVersion.parse)");
		Path source = Files.writeString(workspace.resolve("Main.kt"), KOTLIN_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("kotlin", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "kotlin", source, KOTLIN_SOURCE, new Position(2, 17));
		}
	}

	@Test
	void initializesGroovyAndAnswersCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("java", "-version");
		Path source = Files.writeString(workspace.resolve("script.groovy"), GROOVY_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("groovy", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "groovy", source, GROOVY_SOURCE, new Position(1, 13));
		}
	}

	@Test
	void initializesRustAnalyzerForAStandaloneFile() throws Exception {
		LspIntegrationHarness.requireToolchain("rustc", "--version");
		assumeTrue(LspIntegrationHarness.hasRustSources(),
				"rust-analyzer needs the rust-src component: rustup component add rust-src");
		Path source = Files.writeString(workspace.resolve("main.rs"), RUST_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("rust", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "rust", source, RUST_SOURCE, new Position(2, 25));
		}
	}

	@Test
	void initializesClangdForAndAnswersCCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("clang", "--version");
		Path source = Files.writeString(workspace.resolve("main.c"), C_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("c", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "c", source, C_SOURCE, new Position(4, 10));
		}
	}

	@Test
	void initializesClangdForAndAnswersCppCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("clang", "--version");
		Path source = Files.writeString(workspace.resolve("main.cpp"), CPP_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("cpp", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "cpp", source, CPP_SOURCE, new Position(4, 9));
		}
	}

	@Test
	void initializesCSharpAndAnswersCompletion() throws Exception {
		LspIntegrationHarness.requireToolchain("mono", "--version");
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		assumeTrue(!os.contains("win") && !os.contains("mac"),
				"OmniSharp reports that it located no MSBuild instance on the Windows and macOS runners "
						+ "(MSBuildLocator throws PlatformNotSupportedException under Mono there), so the C# server "
						+ "cannot answer initialize in those environments");
		assumeTrue(
				LspIntegrationHarness.isAvailable("msbuild", "--version")
						|| LspIntegrationHarness.isAvailable("dotnet", "--version"),
				"OmniSharp needs a Mono MSBuild or .NET SDK instance to load the loose file");
		Path source = Files.writeString(workspace.resolve("Program.cs"), CSHARP_SOURCE);

		try (LspClient client = LspIntegrationHarness.start("csharp", workspace, Map.of())) {
			LspIntegrationHarness.assertAnswersCompletion(client, "csharp", source, CSHARP_SOURCE, new Position(3, 13));
		}
	}

}
