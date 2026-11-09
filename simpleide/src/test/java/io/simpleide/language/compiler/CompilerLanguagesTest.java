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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.AbstractLanguage;
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
        while (Output.getInstance().get(0) != null) {
            // Clear output from prior process tests.
        }
    }

    @Test
    void compilerMetadataAndPathConstructorsAreConfigured() {
        CLanguage c = new CLanguage(tempDirectory.resolve("main.c"));
        CPPLanguage cpp = new CPPLanguage(tempDirectory.resolve("main.cpp"));
        Assembly65xLanguage assembly = new Assembly65xLanguage(tempDirectory.resolve("main.s"));
        MonoCSharpLanguage csharp = new MonoCSharpLanguage(tempDirectory.resolve("main.cs"));

        assertEquals("gcc", c.getCompiler());
        assertEquals("gcc", c.getLinker());
        assertEquals("as", c.getAssembler());
        assertEquals(List.of("gcc", "as"), CompilerRequiredFiles.getRequiredFilesFromCompiler(CLanguage.class));
        assertEquals("c", c.getLangFriendly());
        assertEquals(SyntaxConstants.SYNTAX_STYLE_C, c.getLangConstant());
        assertNotNull(c.getExecutable());
        assertEquals(List.of(c.getExecutable()), c.execute());

        assertEquals("g++", cpp.getCompiler());
        assertEquals("as", cpp.getAssembler());
        assertEquals("g++", cpp.getLinker());
        assertEquals(List.of("g++", "as"), CompilerRequiredFiles.getRequiredFilesFromCompiler(CPPLanguage.class));
        assertEquals("c++", cpp.getLangFriendly());
        assertEquals(List.of(cpp.getExecutable()), cpp.execute());

        assertEquals(List.of("wla65816", "wlalink", "Mesen-S"),
                CompilerRequiredFiles.getRequiredFilesFromCompiler(Assembly65xLanguage.class));
        assertNull(assembly.getCompiler());
        assertEquals("Mesen-S", assembly.execute().get(0));
        assertEquals("mcs", csharp.getCompiler());
        assertNull(csharp.getAssembler());
        assertNull(csharp.getLinker());
        assertEquals("mono", csharp.getRuntime());
        assertEquals("mono", csharp.execute().get(0));
        assertEquals(List.of("mcs", "mono"),
                CompilerRequiredFiles.getRequiredFilesFromCompiler(MonoCSharpLanguage.class));
        assertThrows(IllegalArgumentException.class,
                () -> CompilerRequiredFiles.getRequiredFilesFromCompiler(null));
    }

    @Test
    void compilesAndRunsCWithManagedAsynchronousOutput() throws Exception {
        assumeTrue(OSpathUtil.which("gcc") != null, "gcc is not installed");
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
    void compilesAndRunsCppWithManagedAsynchronousOutput() throws Exception {
        assumeTrue(OSpathUtil.which("g++") != null, "g++ is not installed");
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
    void compilesAndRunsCSharpWithMonoWhenInstalled() throws Exception {
        assumeTrue(OSpathUtil.which("mcs") != null, "mcs is not installed");
        assumeTrue(OSpathUtil.which("mono") != null, "mono is not installed");
        Path source = Files.writeString(
                tempDirectory.resolve("main.cs"),
                "using System; public class Program { public static void Main() { Console.WriteLine(\"cs-output\"); } }"
        );

        runAndAssertOutput(new MonoCSharpLanguage(source), "cs-output");
    }

    private void runAndAssertOutput(AbstractLanguage language, String expectedOutput) throws Exception {
        RunnableNode node = language.startManaged();
        assertEquals(expectedOutput, Output.getInstance().get(30));
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(ThreadMaster.getInstance().isNodeRunning(node)));
    }
}
