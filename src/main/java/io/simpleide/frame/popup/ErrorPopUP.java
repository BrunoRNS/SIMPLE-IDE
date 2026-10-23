package io.simpleide.frame.popup;

import java.util.Map;


import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.frame.utils.LoadIcon;

public class ErrorPopUP extends PopUP {

   
    
    public ErrorPopUP(
        String error,
        JComponent parent,
        ErrorTypePopUp type
    ){
        super(parent, type.toString(), error, JOptionPane.ERROR_MESSAGE, LoadIcon.loadingIcon("error.png"), "Ok");
    }

    @Override
    public Map<String, Object> getMap() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setMap(Map<String, Object> m) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
