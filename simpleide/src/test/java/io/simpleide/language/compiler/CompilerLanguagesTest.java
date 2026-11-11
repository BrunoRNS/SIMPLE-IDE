package io.simpleide.language.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        assertEquals("gcc", c.getCompiler());
        assertEquals("gcc", c.getLinker());
        assertEquals("as", c.getAssembler());
        assertEquals(
                isWindows(),
                c.createLinkCommand(Path.of("program.o"), Path.of("program.exe")).contains("-mconsole")
        );
        assertEquals(List.of("gcc", "as"), CompilerRequiredFiles.getRequiredFilesFromCompiler(CLanguage.class));
        assertEquals("c", c.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_C, c.getLangConstant());
        assertNotNull(c.getExecutable());
        assertEquals(List.of(c.getExecutable()), c.execute());

        assertEquals("g++", cpp.getCompiler());
        assertEquals("as", cpp.getAssembler());
        assertEquals("g++", cpp.getLinker());
        assertEquals(
                isWindows(),
                cpp.createLinkCommand(Path.of("program.o"), Path.of("program.exe")).contains("-mconsole")
        );
        assertEquals(List.of("g++", "as"), CompilerRequiredFiles.getRequiredFilesFromCompiler(CPPLanguage.class));
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
        assertThrows(IllegalArgumentException.class,
                () -> CompilerRequiredFiles.getRequiredFilesFromCompiler(null));
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

    private void runAndAssertOutput(AbstractLanguage language, String expectedOutput) throws Exception {
        RunnableNode node = language.startManaged();
        assertEquals(expectedOutput, Output.getInstance().get(30));
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
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
