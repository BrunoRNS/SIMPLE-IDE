package io.simpleide.language.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.compiler.CLanguage;
import io.simpleide.language.compiler.CPPLanguage;
import io.simpleide.language.compiler.MonoCSharpLanguage;
import io.simpleide.language.interpreter.InterpreterRequiredFile;
import io.simpleide.language.interpreter.JSLanguage;
import io.simpleide.language.interpreter.Python3Language;
import io.simpleide.language.jvmbytecode.GroovyLanguage;
import io.simpleide.language.jvmbytecode.JavaLanguage;
import io.simpleide.language.jvmbytecode.KotlinLanguage;

class LangSetupTest {

    @Test
    void registersOnlyLanguagesWhoseRequirementsAreAvailable() {
        LangConfig config = new LangConfig();
        new TestLangSetup(
                config,
                Set.of("gcc", "as", "node", InterpreterRequiredFile.getInterpreter(Python3Language.class))
        ).run();

        assertEquals(CLanguage.class, config.getLanguage(".c"));
        assertNull(config.getLanguage(".cpp"));
        assertEquals(JSLanguage.class, config.getLanguage(".js"));
        assertEquals(Python3Language.class, config.getLanguage(".py"));
        assertEquals(JavaLanguage.class, config.getLanguage(".java"));
        assertNull(config.getLanguage(".cs"));
        assertNull(config.getLanguage(".s"));
        assertNull(config.getLanguage(".asm"));
    }

    @Test
    void registersAllLanguageExtensionsWhenRequirementsAreAvailable() {
        LangConfig config = new LangConfig();
        TestLangSetup setup = new TestLangSetup(
                config,
                Set.of(
                        "gcc", "g++", "as", "mcs", "mono", "node",
                        InterpreterRequiredFile.getInterpreter(Python3Language.class)
                )
        );
        setup.run();

        assertEquals(CLanguage.class, config.getLanguage(".h"));
        assertEquals(CPPLanguage.class, config.getLanguage(".CXX"));
        assertEquals(MonoCSharpLanguage.class, config.getLanguage(".csx"));
        assertEquals(JSLanguage.class, config.getLanguage(".mjs"));
        assertEquals(Python3Language.class, config.getLanguage(".pyw"));
        assertEquals(GroovyLanguage.class, config.getLanguage(".gsh"));
        assertEquals(KotlinLanguage.class, config.getLanguage(".kts"));
        assertEquals(JavaLanguage.class, config.getLanguage(".jav"));
    }

    @Test
    void rejectsUnsupportedLanguageClassesAndNullConfiguration() {
        TestLangSetup setup = new TestLangSetup(new LangConfig(), Set.of());

        assertFalse(setup.validateLanguage(UnsupportedLanguage.class));
        assertFalse(setup.validateLanguage(null));
        assertThrows(NullPointerException.class, () -> new LangSetup(null));
    }

    private static final class TestLangSetup extends LangSetup {
        private final Set<String> availableCommands;

        private TestLangSetup(LangConfig config, Set<String> availableCommands) {
            super(config);
            this.availableCommands = availableCommands;
        }

        @Override
        protected boolean isInSystemPATH(String filename) {
            return availableCommands.contains(filename);
        }

        private boolean validateLanguage(Class<? extends AbstractLanguage> languageClass) {
            return validate(languageClass);
        }
    }

    private static final class UnsupportedLanguage extends AbstractLanguage {
    }
}
