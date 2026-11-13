package io.simpleide.language.config;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.compiler.AbstractCompilerLanguage;
import io.simpleide.language.compiler.CLanguage;
import io.simpleide.language.compiler.CPPLanguage;
import io.simpleide.language.compiler.CompilerRequiredFiles;
import io.simpleide.language.compiler.GoLanguage;
import io.simpleide.language.compiler.MonoCSharpLanguage;
import io.simpleide.language.compiler.RustLanguage;
import io.simpleide.language.interpreter.AbstractInterpreterLanguage;
import io.simpleide.language.interpreter.InterpreterRequiredFile;
import io.simpleide.language.interpreter.JSLanguage;
import io.simpleide.language.interpreter.Python3Language;
import io.simpleide.language.jvmbytecode.AbstractBytecodeLanguage;
import io.simpleide.language.jvmbytecode.GroovyLanguage;
import io.simpleide.language.jvmbytecode.KotlinLanguage;
import io.simpleide.language.jvmbytecode.JavaLanguage;
import io.simpleide.utils.OSpathUtil;

public class LangSetup implements Runnable {

    protected static final Logger LOGGER = LoggerFactory.getLogger(LangSetup.class);

    protected LangConfig langConfig;

    public LangSetup(LangConfig instance) {
        this.langConfig = Objects.requireNonNull(instance, "Language configuration must not be null.");
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

        } catch (RuntimeException exception) {
            LOGGER.error("Error occurred while setting up languages.", exception);
            throw exception;

        } finally {

            LOGGER.info("Language setup process finished.");

        }

    }

    protected void setupCompilerLanguages() {

        if (this.validate(CLanguage.class)) {
            this.setupLanguage(
                CLanguage.class,
                ".c", ".h"
            );
        }
        if (this.validate(CPPLanguage.class)) {
            this.setupLanguage(
                CPPLanguage.class,
                ".cpp", ".hpp", ".cc", ".cxx", ".hxx"
            );
        }
        if (this.validate(MonoCSharpLanguage.class)) {
            this.setupLanguage(
                MonoCSharpLanguage.class,
                ".cs", ".csx"
            );
        }
        if (this.validate(GoLanguage.class)) {
            this.setupLanguage(
                GoLanguage.class,
                ".go"
            );
        }
        if (this.validate(RustLanguage.class)) {
            this.setupLanguage(
                RustLanguage.class,
                ".rs"
            );
        }

    }

    protected void setupInterpreterLanguages() {

        if (this.validate(JSLanguage.class)) {
            this.setupLanguage(
                JSLanguage.class,
                ".js", ".jsx", ".mjs", ".cjs", ".jscript"
            );
        }
        if (this.validate(Python3Language.class)) {
            this.setupLanguage(
                Python3Language.class,
                ".py", ".pyi", ".pyw", ".py3"
            );
        }

    }

    protected void setupBytecodeLanguages() {

        if (this.validate(GroovyLanguage.class)) {
            this.setupLanguage(
                GroovyLanguage.class,
                ".groovy", ".gvy", ".gy", ".gsh"
            );
        }
        if (this.validate(KotlinLanguage.class)) {
            this.setupLanguage(
                KotlinLanguage.class,
                ".kt", ".kts"
            );
        }
        if (this.validate(JavaLanguage.class)) {
            this.setupLanguage(
                JavaLanguage.class,
                ".jav", ".java"
            );
        }

    }

    protected void setupLanguage(
        Class<? extends AbstractLanguage> langClass,
        String... extensions
    ) {
        this.langConfig.addLanguage(langClass, extensions);
    }

    protected boolean validate(Class<? extends AbstractLanguage> langClass) {

        if (langClass == null) {
            LOGGER.error("Language class is null.");
            return false;
        }

        if (AbstractBytecodeLanguage.class.isAssignableFrom(langClass)) {
            return AbstractBytecodeLanguage.isAvailable();
        }

        if (AbstractCompilerLanguage.class.isAssignableFrom(langClass)) {
            LOGGER.info("Validating compiler language class: {}", langClass.getName());

            Class<? extends AbstractCompilerLanguage> compilerClass = 
                    langClass.asSubclass(AbstractCompilerLanguage.class);

            List<String> requiredFiles = CompilerRequiredFiles.getRequiredFilesFromCompiler(
                compilerClass
            );

            return !requiredFiles.isEmpty() && requiredFiles.stream().allMatch(this::isInSystemPATH);
        }

        if (AbstractInterpreterLanguage.class.isAssignableFrom(langClass)) {
            LOGGER.info("Validating interpreter language class: {}", langClass.getName());

            Class<? extends AbstractInterpreterLanguage> interpreterClass =
                    langClass.asSubclass(AbstractInterpreterLanguage.class);

            String requiredFile = InterpreterRequiredFile.getInterpreter(
                interpreterClass
            );

            return isInSystemPATH(requiredFile);
        }


        return false;

    }

    protected boolean isInSystemPATH(String filename) {
        return OSpathUtil.which(filename) != null;
    }

}
