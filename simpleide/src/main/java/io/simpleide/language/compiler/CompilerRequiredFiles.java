package io.simpleide.language.compiler;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompilerRequiredFiles {

    protected static final Logger LOGGER = LoggerFactory.getLogger(CompilerRequiredFiles.class);

    public static List<String> getRequiredFilesFromCompiler(
        Class<? extends AbstractCompilerLanguage> c
    ) {

        List<String> l = new ArrayList<>(3);

        try {
            AbstractCompilerLanguage c_instance = c.getDeclaredConstructor().newInstance(
                new Object[] {}
            );
        
            l.add(c_instance.getCompiler());
            l.add(c_instance.getAssembler());
            l.add(c_instance.getLinker());

        } catch (
            NoSuchMethodException | SecurityException | 
            IllegalAccessException | IllegalArgumentException | InvocationTargetException |
            InstantiationError | InstantiationException ex) {
            LOGGER.error(
                "Error while requesting necessary files from compiler - " + ex.getMessage());
            return null;
        }

        return l;

    }
    
}
