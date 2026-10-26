package io.simpleide.frame.popup;

import java.util.Map;


import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;

@Mappeable 
public class ErrorPopUP extends PopUP implements  MappeableSupport {

   
    
    public ErrorPopUP(
        String error,
        JComponent parent,
        ErrorTypePopUp type
    ){
        super(parent, type.toString(), error, JOptionPane.ERROR_MESSAGE, LoadIcon.loadingIcon("error.png"), "Ok");
    }

    public ErrorPopUP(String error, ErrorTypePopUp typeError){
        this(error, null, typeError);
    }

    @Override
    public Map<String, Object> getMap() {
        return getMaping();
    }

    @Override
    public void setMap(Map<String, Object> m) {
        setMaping(m);
    }
    
}
