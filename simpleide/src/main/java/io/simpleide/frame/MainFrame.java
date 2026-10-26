package io.simpleide.frame;

import javax.swing.JFrame;

import io.simpleide.frame.popup.SuccessPopUP;
import io.simpleide.frame.popup.PopUP;

public class MainFrame extends JFrame implements Runnable {

  @Override
  public void run() {
    PopUP pop = new SuccessPopUP("Title", "message", null, "Ok");
    pop.run();
    System.out.println(pop.getMap());
  }

}
