package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

public abstract class PopUP implements Runnable {

    protected boolean on = false;
    protected String title;

    protected JComponent parent = null;
    protected JDialog jd = null;
    protected JOptionPane jp;
    

    public PopUP(
        JComponent parent,
        String title,
        Object message,
        int messageType, 
        int optionType, 
        Icon icon, 
        Object[] options,
        Object initialValue) {

            this.parent = parent;
            this.title = title;
            this.jp = new JOptionPane(
                message, messageType, optionType, icon, options, initialValue);
        
        }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String s) {
        this.title = s;
    }

    public JDialog getDialog() {
        return this.jd;
    }

    public void setDialog(JDialog jd) {
        this.jd = jd;
    }

    @Override 
    public void run() throws NullPointerException {
        if (this.jp == null) {
            throw new NullPointerException("JOptionPane can't be null!");
        }
        if (this.jd == null) {
            this.jd = jp.createDialog(this.parent, this.title);
        }

        this.on = true;
        this.jd.setVisible(true);
        this.on = false;

    }

    public Thread getThread() {
        return new Thread(this);
    }

    @Override 
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("The PopUP is on? " + this.on + "\n");
        sb.append("Class Data:\n");

        Map<String, Object> m = getMap();
        if (m == null) {
            sb.append("null\n");
        } else {
            m.forEach((name, obj) -> {sb.append(name + " : " + obj.toString() + "\n");});
        }

        return sb.toString();

    }

    public abstract Map<String, Object> getMap();
    public abstract void setMap(Map<String, Object> m);
    
}
