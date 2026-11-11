package io.simpleide.language.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.jvmbytecode.JavaLanguage;
import io.simpleide.language.jvmbytecode.KotlinLanguage;

class LangConfigTest {

    @TempDir
    Path tempDirectory;

    @Test
    void registersAndLooksUpExtensionsWithoutCaseSensitivity() {
        LangConfig config = new LangConfig();
        config.addLanguage(JavaLanguage.class, ".java", ".jav");

        assertSame(JavaLanguage.class, config.getLanguage(".JAVA"));
        assertSame(JavaLanguage.class, config.getLanguage(".jav"));
        assertNull(config.getLanguage(".unknown"));
        assertNull(config.getLanguage(null));
    }

    @Test
    void createsConfiguredLanguageUsingSourcePath() {
        LangConfig config = new LangConfig();
        Path sourceFile = tempDirectory.resolve("Program.java");
        config.addLanguage(JavaLanguage.class, ".java");

        AbstractLanguage language = config.createLanguage(".java", sourceFile);

        assertInstanceOf(JavaLanguage.class, language);
        assertEquals("java", language.getLangFriendly());
    }

    @Test
    void unavailableExtensionReturnsNullAndAvailableExtensionRequiresPath() {
        LangConfig config = new LangConfig();
        config.addLanguage(JavaLanguage.class, ".java");

        assertNull(config.createLanguage(".txt", tempDirectory.resolve("readme.txt")));
        assertThrows(NullPointerException.class, () -> config.createLanguage(".java", null));
    }

    @Test
    void returnsAnUnmodifiableLanguagesSnapshot() {
        LangConfig config = new LangConfig();
        config.addLanguage(JavaLanguage.class, ".java");
        Map<String, LangRefer> languages = config.getLanguages();

        assertTrue(languages.containsKey(".java"));
        assertThrows(UnsupportedOperationException.class, () -> languages.put(".kt", new LangRefer(KotlinLanguage.class)));
    }

    @Test
    void referCanReplaceItsLanguageClass() {
        LangRefer refer = new LangRefer(JavaLanguage.class);
        refer.setLangClass(KotlinLanguage.class);

        assertSame(KotlinLanguage.class, refer.getLangClass());
    }

    @Test
    void languageConfigHolderIsShared() {
        assertSame(LangConfigHolder.LANG_CONFIG, new LangConfig().getInstance());
    }
}
