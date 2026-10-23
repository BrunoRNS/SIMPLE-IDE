package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.Icon;
import javax.swing.JComponent;

public class SuccessPopUP extends PopUP{

    public SuccessPopUP(JComponent parent, String title, Object message, int messageType, int optionType, Icon icon, Object initialValue) {
        super(parent, title, message, messageType, optionType, icon, initialValue);
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
