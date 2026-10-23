package io.simpleide.frame.popup.utils;

public enum ErrorTypePopUp {
    RUNTIME_ERROR("Runtime error"),
    COMPILE_ERROR("Compile error"),
    LINKER_ERROR("Linker error"),
    INTERNAL_ERROR("Internal error"),
    FATAL_ERROR("Fatal error");

    private final String error;

    ErrorTypePopUp(String error){
        this.error = error;
    }



    @Override 
    public String toString(){
        return error;
    }
}
