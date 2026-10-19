package io.simpleide.frame.layout;

import java.awt.FlowLayout;

import javax.swing.JComponent;

public class SimpleLayout extends FlowLayout {

    public SimpleLayout() {
        super();
    }

    public void addLeftComponent(JComponent comp) {
        this.setAlignment(FlowLayout.LEFT);
        this.addLayoutComponent(null, comp);
    }

    public void addRightComponent(JComponent comp) {
        this.setAlignment(FlowLayout.RIGHT);
        this.addLayoutComponent(null, comp);
    }

    public void addCenterComponent(JComponent comp) {
        this.setAlignment(FlowLayout.CENTER);
        this.addLayoutComponent(null, comp);
    }
    
}
