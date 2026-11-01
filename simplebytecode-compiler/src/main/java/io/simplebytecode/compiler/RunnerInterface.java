package io.simplebytecode.compiler;

import java.nio.file.Path;

public interface RunnerInterface extends Runnable {

    public String loadFile(Path file);
    public void prepare(String src);
    
}
