package io.simpleide.language.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.compiler.AbstractCompilerLanguage;
import io.simpleide.language.compiler.Assembly65xLanguage;
import io.simpleide.language.compiler.CLanguage;
import io.simpleide.language.compiler.CPPLanguage;
import io.simpleide.language.compiler.CompilerRequiredFiles;
import io.simpleide.language.interpreter.AbstractInterpreterLanguage;
import io.simpleide.language.jvmbytecode.AbstractBytecodeLanguage;
import io.simpleide.utils.OSpathUtil;

public class LangSetup implements Runnable {

    protected static final Logger LOGGER = LoggerFactory.getLogger(LangSetup.class);

    protected LangConfig langConfig;

    public LangSetup(LangConfig instance) {
        this.langConfig = instance;
    }

    @Override
    public void run() {

        LOGGER.info("Setting up languages...");

        try {
            this.setupCompilerLanguages();
            LOGGER.info("Compiler languages setup complete.");

            this.setupInterpreterLanguages();
            LOGGER.info("Interpreter languages setup complete.");

            this.setupBytecodeLanguages();
            LOGGER.info("Bytecode languages setup complete.");

        } catch (Exception e) {

            LOGGER.error("Error occurred while setting up languages: {0}" + e.getMessage());

        } finally {

            LOGGER.info("Language setup process finished.");

        }

    }

    protected void setupCompilerLanguages() {

        this.setupCompilerLanguage(
            Assembly65xLanguage.class,
            ".s", ".asm", ".asm65x"
        );
        this.setupCompilerLanguage(
            CLanguage.class,
            ".c", ".h"
        );
        this.setupCompilerLanguage(
            CPPLanguage.class,
            ".cpp", ".hpp", ".cc", ".cxx", ".hxx"
        );

    }

    protected void setupCompilerLanguage(
        Class<? extends AbstractCompilerLanguage> langClass,
        String... extensions
    ) {
        this.langConfig.addLanguage(langClass, extensions);

    }

    protected void setupInterpreterLanguages() {

    }

    protected void setupInterpreterLanguage(
        Class<? extends AbstractInterpreterLanguage> langClass,
        String... extensions
    ) {
        this.langConfig.addLanguage(langClass, extensions);
    }

    protected void setupBytecodeLanguages() {

    }

    protected void setupBytecodeLanguage(
        Class<? extends AbstractBytecodeLanguage> langClass,
        String... extensions
    ) {
        this.langConfig.addLanguage(langClass, extensions);
    }

    private boolean validate(Class<? extends AbstractLanguage> langClass) {

        if (langClass == null) {
            LOGGER.error("Language class is null.");
            return false;
        }

        if (AbstractBytecodeLanguage.class.isAssignableFrom(langClass)) {
            LOGGER.info(
                "Bytecode Language classes are always pre-installed and do not require setup.");
            return true;
        }

        if (AbstractCompilerLanguage.class.isAssignableFrom(langClass)) {
            LOGGER.info("Validating compiler language class: {0}" + langClass.getName());

            Class<? extends AbstractCompilerLanguage> compilerClass = 
                    langClass.asSubclass(AbstractCompilerLanguage.class);

            List<String> requiredFiles = CompilerRequiredFiles.getRequiredFilesFromCompiler(
                compilerClass
            );

            boolean valid = true;

            for (String file : requiredFiles) {

                if (!valid) return false;
                if (file != null) valid = valid && isInSystemPATH(file);
                
            }

            return valid;
        }

        return false;

    }

    private boolean isInSystemPATH(String filename) {
        return OSpathUtil.which(filename) != null;
    }

}
