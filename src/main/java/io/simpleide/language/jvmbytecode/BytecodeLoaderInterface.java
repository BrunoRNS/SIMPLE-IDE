package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;

public interface BytecodeLoaderInterface extends Runnable {

    void loadFromSource(String source);
    void loadFromFile(Path filePath);

    void setInput(String input);
    String getOutput();

}
