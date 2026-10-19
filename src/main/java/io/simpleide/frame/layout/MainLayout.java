package io.simpleide.frame.layout;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JComponent;

public class MainLayout extends GridBagLayout {

    public MainLayout() {
        super();
    }

    public void addComponent(JComponent component, int gridx, int gridy, int gridwidth, int gridheight, double weightx, double weighty) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.gridwidth = gridwidth;
        gbc.gridheight = gridheight;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.fill = GridBagConstraints.BOTH;
        this.setConstraints(component, gbc);
    }

    public void addComponent(JComponent component, int gridx, int gridy, int gridwidth, int gridheight) {
        this.addComponent(component, gridx, gridy, gridwidth, gridheight, 0.0, 0.0);
    }

    public void addComponent(JComponent component, int gridx, int gridy) {
        this.addComponent(component, gridx, gridy, 1, 1);
    }

    public void addComponent(JComponent component, int gridx, int gridy, double weightx, double weighty) {
        this.addComponent(component, gridx, gridy, 1, 1, weightx, weighty);
    }

    public void removeComponent(JComponent component) {
        this.removeLayoutComponent(component);
    }

    public void removeAllComponents() {
        this.removeLayoutComponent(null);
    }

    public void updateLayout() {
        this.invalidateLayout(null);
    }

    public void refreshLayout() {
        this.updateLayout();
        this.layoutContainer(null);
    }
    
}
