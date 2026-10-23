package io.simpleide.frame;

import javax.swing.JFrame;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.PopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;

public class MainFrame extends JFrame implements Runnable {

    @Override
    public void run() {
        PopUP pop = new ErrorPopUP("Sla", null, ErrorTypePopUp.RUNTIME_ERROR);
        pop.run();
    }
    
}
