package io.simpleide.language.interpreter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.simpleide.language.AbstractLanguage;

public abstract class AbstractInterpreterLanguage extends AbstractLanguage {

    protected List<String> additionalArgs = List.of();
    protected String interpreter;

    protected AbstractInterpreterLanguage() {
        super();
    }

    protected final void configure(
            Path file,
            String friendlyName,
            String languageConstant,
            String interpreter,
            List<String> additionalArgs) {
        if (file == null) {
            throw new IllegalArgumentException("Source file path must not be null.");
        }

        this.interpreter = interpreter;
        this.additionalArgs = List.copyOf(additionalArgs);
        this.langFriendly = friendlyName;
        this.langConstant = languageConstant;

        List<String> command = new ArrayList<>();
        command.add(interpreter);
        command.addAll(this.additionalArgs);
        command.add(file.toAbsolutePath().normalize().toString());
        this.pb = new ProcessBuilder(command);
    }

    public String getInterpreter() {
        return this.interpreter;
    }

    public List<String> getAdditionalArgs() {
        return additionalArgs;
    }
}
