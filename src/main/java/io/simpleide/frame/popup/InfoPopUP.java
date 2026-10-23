package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.Icon;
import javax.swing.JComponent;

public class InfoPopUP extends PopUP {

    public InfoPopUP(JComponent parent, String title, Object message, int messageType, int optionType, Icon icon,
             Object initialValue) {
        super(parent, title, message, messageType, optionType, icon, initialValue);
        //TODO Auto-generated constructor stub
    }

    @Override
    public Map<String, Object> getMap() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMap'");
    }

    @Override
    public void setMap(Map<String, Object> m) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setMap'");
    }
    
}
