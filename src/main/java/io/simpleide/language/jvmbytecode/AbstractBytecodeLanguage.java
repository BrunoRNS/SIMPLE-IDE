package io.simpleide.language.jvmbytecode;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import io.simpleide.language.AbstractLanguage;

public abstract class AbstractBytecodeLanguage extends AbstractLanguage {

    protected String source;
    protected Path filePath;
    protected String[] ext;
    protected Class<? extends BytecodeLoaderInterface> loaderClass;

    public AbstractBytecodeLanguage(
        Class<? extends BytecodeLoaderInterface> loaderClass,
        String source,
        String... ext
    ) {
        super();

        this.loaderClass = loaderClass;
        this.ext = ext;
        this.source = source;
    }

    public AbstractBytecodeLanguage(
        Class<? extends BytecodeLoaderInterface> loaderClass,
        Path filePath,
        String... ext
    ) {
        super();

        this.loaderClass = loaderClass;
        this.ext = ext;
        this.filePath = filePath;
    }

    @Override 
    public void execute() {

        if (this.source == null && this.filePath == null) {
            throw new RuntimeException(
                "No source code or file path provided for execution."
            );
        }

        if (this.source == null) {
            try {
                this.source = java.nio.file.Files.readString(this.filePath);
            } catch (java.io.IOException e) {
                throw new RuntimeException(
                    "Error reading file: " + this.filePath, e
                );
            }
        }

        Thread thread = getThread();
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "Execution interrupted.", e
            );
        }

    }

    @Override
    protected Thread getThread() {

        BytecodeLoaderInterface r;
        try {
            r = this.loaderClass.getDeclaredConstructor().newInstance();
            r.setInput(input);
            r.loadFromSource(this.source);
        } catch (
            InstantiationException    | 
            IllegalAccessException    | 
            InvocationTargetException | 
            NoSuchMethodException e
        ) {
            throw new RuntimeException("Failed to create an instance of the loader class.", e);
        }

        return new Thread(r);

    }


    public String[] getExtensions() {
        return ext;
    }

    public void setExtensions(String[] ext) {
        this.ext = ext;
    }

    public Class<? extends BytecodeLoaderInterface> getLoaderClass() {
        return loaderClass;
    }

    public void setLoaderClass(Class<? extends BytecodeLoaderInterface> loaderClass) {
        this.loaderClass = loaderClass;
    }
    
}


