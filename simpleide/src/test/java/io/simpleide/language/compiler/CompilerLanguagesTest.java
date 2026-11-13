package io.simpleide.language.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.utils.OSpathUtil;

import static org.awaitility.Awaitility.await;

class CompilerLanguagesTest {

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void clearOutput() throws InterruptedException {
        while (Input.getInstance().get(0) != null) {
            // Clear input from prior process tests.
        }
        while (Output.getInstance().get(0) != null) {
            // Clear output from prior process tests.
        }
    }

    @Test
    void compilerMetadataAndPathConstructorsAreConfigured() {
        CLanguage c = new CLanguage(tempDirectory.resolve("main.c"));
        CPPLanguage cpp = new CPPLanguage(tempDirectory.resolve("main.cpp"));
        MonoCSharpLanguage csharp = new MonoCSharpLanguage(tempDirectory.resolve("main.cs"));
        GoLanguage go = new GoLanguage(tempDirectory.resolve("main.go"));
        RustLanguage rust = new RustLanguage(tempDirectory.resolve("main.rs"));

        assertEquals("gcc", c.getCompiler());
        assertEquals("gcc", c.getLinker());
        assertNull(c.getAssembler());
        assertEquals(List.of("gcc"), CompilerRequiredFiles.getRequiredFilesFromCompiler(CLanguage.class));
        assertEquals("c", c.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_C, c.getLangConstant());
        assertNotNull(c.getExecutable());
        assertEquals(List.of(c.getExecutable()), c.execute());

        assertEquals("g++", cpp.getCompiler());
        assertNull(cpp.getAssembler());
        assertEquals("g++", cpp.getLinker());
        assertEquals(List.of("g++"), CompilerRequiredFiles.getRequiredFilesFromCompiler(CPPLanguage.class));
        assertEquals("c++", cpp.getLangFriendly());
        assertEquals(List.of(cpp.getExecutable()), cpp.execute());

        assertEquals("mcs", csharp.getCompiler());
        assertNull(csharp.getAssembler());
        assertNull(csharp.getLinker());
        assertEquals("mono", csharp.getRuntime());
        Path monoLauncher = OSpathUtil.which("mono");
        boolean usesMonoExecutable = isWindows()
                && monoLauncher != null
                && !monoLauncher.toString().toLowerCase().endsWith(".bat")
                && !monoLauncher.toString().toLowerCase().endsWith(".cmd");
        assertEquals(usesMonoExecutable ? monoLauncher.toString() : "mono", csharp.execute().get(0));
        List<String> csharpCompilerCommand = csharp.compilerCommand(
                tempDirectory.resolve("main.cs").toAbsolutePath().normalize(),
                Path.of(csharp.getExecutable())
        );
        boolean usesManagedCompiler = usesMonoExecutable;
        Path managedCompiler = null;
        if (usesManagedCompiler) {
            managedCompiler = monoLauncher.getParent().getParent()
                    .resolve("lib").resolve("mono").resolve("4.5").resolve("mcs.exe");
            usesManagedCompiler = Files.isRegularFile(managedCompiler);
        }
        assertEquals(usesManagedCompiler ? monoLauncher.toString() : "mcs", csharpCompilerCommand.get(0));
        if (usesManagedCompiler) {
            assertEquals(managedCompiler.toString(), csharpCompilerCommand.get(1));
        }
        assertEquals(List.of("mcs", "mono"),
                CompilerRequiredFiles.getRequiredFilesFromCompiler(MonoCSharpLanguage.class));
        assertEquals("go", go.getCompiler());
        assertEquals("go", go.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_GO, go.getLangConstant());
        assertEquals(List.of(go.getExecutable()), go.execute());
        assertEquals(List.of("go"), CompilerRequiredFiles.getRequiredFilesFromCompiler(GoLanguage.class));
        assertEquals("rustc", rust.getCompiler());
        assertEquals("rust", rust.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_RUST, rust.getLangConstant());
        assertEquals(List.of(rust.getExecutable()), rust.execute());
        assertEquals(List.of("rustc"), CompilerRequiredFiles.getRequiredFilesFromCompiler(RustLanguage.class));
        assertThrows(IllegalArgumentException.class,
                () -> CompilerRequiredFiles.getRequiredFilesFromCompiler(null));
    }

    @Test
    void selects32BitWindowsRustTarget() {
        assertEquals("i686-pc-windows-gnu", RustLanguage.windows32Target("Windows 11", "x86"));
        assertEquals("i686-pc-windows-gnu", RustLanguage.windows32Target("Windows 11", "i686"));
        assertNull(RustLanguage.windows32Target("Windows 11", "amd64"));
        assertNull(RustLanguage.windows32Target("Linux", "x86"));
    }

    @Test
    void windowsCAndCppUseStaticRuntimeLinkerFlags() {
        withOperatingSystem("Windows 11", () -> {
            List<String> cCommand = new CLanguage(tempDirectory.resolve("main.c"))
                    .getProcessCommands().get(0);
            assertTrue(cCommand.contains("-static-libgcc"));
            assertFalse(cCommand.contains("-static-libstdc++"));

            List<String> cppCommand = new CPPLanguage(tempDirectory.resolve("main.cpp"))
                    .getProcessCommands().get(0);
            assertTrue(cppCommand.contains("-static-libgcc"));
            assertTrue(cppCommand.contains("-static-libstdc++"));
        });
    }

    @Test
    void nonWindowsCAndCppDoNotUseStaticRuntimeLinkerFlags() {
        withOperatingSystem("Linux", () -> {
            List<String> cCommand = new CLanguage(tempDirectory.resolve("main.c"))
                    .getProcessCommands().get(0);
            assertFalse(cCommand.contains("-static-libgcc"));
            assertFalse(cCommand.contains("-static-libstdc++"));

            List<String> cppCommand = new CPPLanguage(tempDirectory.resolve("main.cpp"))
                    .getProcessCommands().get(0);
            assertFalse(cppCommand.contains("-static-libgcc"));
            assertFalse(cppCommand.contains("-static-libstdc++"));
        });
    }

    @Test
    @Tag("language-tools")
    void compilesAndRunsCWithManagedAsynchronousOutput() throws Exception {
        requireLanguageTool("gcc");
        Path source = Files.writeString(
                tempDirectory.resolve("main.c"),
                "#include <stdio.h>\nint main(void) { puts(\"c-output\"); return 0; }\n"
        );

        CLanguage language = new CLanguage(source);
        Path executable = Path.of(language.getExecutable());
        runAndAssertOutput(language, "c-output");
        assertFalse(Files.exists(executable));
    }

    @Test
    @Tag("language-tools")
    void forwardsInputToCompiledCProgram() throws Exception {
        requireLanguageTool("gcc");
        Path source = Files.writeString(
                tempDirectory.resolve("input.c"),
                "#include <stdio.h>\nint main(void) { char value[64]; "
                        + "if (fgets(value, sizeof value, stdin)) printf(\"c-received:%s\", value); return 0; }\n"
        );
        Input.getInstance().set("message");

        runAndAssertOutput(new CLanguage(source), "c-received:message");
    }

    @Test
    @Tag("language-tools")
    void compilesAndRunsCppWithManagedAsynchronousOutput() throws Exception {
        requireLanguageTool("g++");
        Path source = Files.writeString(
                tempDirectory.resolve("main.cpp"),
                "#include <iostream>\nint main() { std::cout << \"cpp-output\" << std::endl; }\n"
        );

        CPPLanguage language = new CPPLanguage(source);
        Path executable = Path.of(language.getExecutable());
        runAndAssertOutput(language, "cpp-output");
        assertFalse(Files.exists(executable));
    }

    @Test
    @Tag("language-tools")
    void forwardsInputToCompiledCppProgram() throws Exception {
        requireLanguageTool("g++");
        Path source = Files.writeString(
                tempDirectory.resolve("input.cpp"),
                "#include <iostream>\n#include <string>\n"
                        + "int main() { std::string value; std::getline(std::cin, value); "
                        + "std::cout << \"cpp-received:\" << value << std::endl; }\n"
        );
        Input.getInstance().set("message");

        runAndAssertOutput(new CPPLanguage(source), "cpp-received:message");
    }

    @Test
    @Tag("language-tools")
    void compilesAndRunsCSharpWithMonoWhenInstalled() throws Exception {
        requireLanguageTool("mcs");
        requireLanguageTool("mono");
        Path source = Files.writeString(
                tempDirectory.resolve("main.cs"),
                "using System; public class Program { public static void Main() { Console.WriteLine(\"cs-output\"); } }"
        );

        runAndAssertOutput(new MonoCSharpLanguage(source), "cs-output");
    }

    @Test
    @Tag("language-tools")
    void forwardsInputToCompiledCSharpProgram() throws Exception {
        requireLanguageTool("mcs");
        requireLanguageTool("mono");
        Path source = Files.writeString(
                tempDirectory.resolve("input.cs"),
                "using System; public class Program { public static void Main() { Console.WriteLine(\"got:\" + Console.ReadLine()); } }"
        );
        Input.getInstance().set("message");

        runAndAssertOutput(new MonoCSharpLanguage(source), "got:message");
    }

    @Test
    @Tag("language-tools")
    void compilesAndRunsGoWithManagedAsynchronousOutput() throws Exception {
        requireLanguageTool("go");
        Path source = Files.writeString(
                tempDirectory.resolve("main.go"),
                "package main\nimport \"fmt\"\nfunc main() { fmt.Println(\"go-output\") }\n"
        );

        GoLanguage language = new GoLanguage(source);
        Path executable = Path.of(language.getExecutable());
        runAndAssertOutput(language, "go-output");
        assertFalse(Files.exists(executable));
    }

    @Test
    @Tag("language-tools")
    void forwardsInputToCompiledGoProgram() throws Exception {
        requireLanguageTool("go");
        Path source = Files.writeString(
                tempDirectory.resolve("input.go"),
                "package main\nimport (\"bufio\"; \"fmt\"; \"os\")\n"
                        + "func main() { value, _ := bufio.NewReader(os.Stdin).ReadString('\\n'); "
                        + "fmt.Print(\"go-received:\", value) }\n"
        );
        Input.getInstance().set("message");

        runAndAssertOutput(new GoLanguage(source), "go-received:message");
    }

    @Test
    @Tag("language-tools")
    void compilesAndRunsRustWithManagedAsynchronousOutput() throws Exception {
        requireLanguageTool("rustc");
        Path source = Files.writeString(
                tempDirectory.resolve("main.rs"),
                "fn main() { println!(\"rust-output\"); }\n"
        );

        RustLanguage language = new RustLanguage(source);
        Path executable = Path.of(language.getExecutable());
        runAndAssertOutput(language, "rust-output");
        assertFalse(Files.exists(executable));
    }

    @Test
    @Tag("language-tools")
    void forwardsInputToCompiledRustProgram() throws Exception {
        requireLanguageTool("rustc");
        Path source = Files.writeString(
                tempDirectory.resolve("input.rs"),
                "use std::io::{self, BufRead};\n"
                        + "fn main() { let mut value = String::new(); "
                        + "io::stdin().lock().read_line(&mut value).unwrap(); "
                        + "print!(\"rust-received:{}\", value.trim()); }\n"
        );
        Input.getInstance().set("message");

        runAndAssertOutput(new RustLanguage(source), "rust-received:message");
    }

    private void withOperatingSystem(String osName, Runnable action) {
        String originalOsName = System.getProperty("os.name");
        try {
            System.setProperty("os.name", osName);
            action.run();
        } finally {
            if (originalOsName == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", originalOsName);
            }
        }
    }

    private void runAndAssertOutput(AbstractLanguage language, String expectedOutput) throws Exception {
        RunnableNode node = language.startManaged();
        String actualOutput = Output.getInstance().get(30);
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
        assertEquals(expectedOutput, actualOutput);
    }

    private void requireLanguageTool(String executable) {
        String message = executable + " is not installed";
        if (System.getenv("SIMPLEIDE_EXPECTED_PLATFORM") != null) {
            assertNotNull(OSpathUtil.which(executable), message + " on CI");
        } else {
            assumeTrue(OSpathUtil.which(executable) != null, message);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
