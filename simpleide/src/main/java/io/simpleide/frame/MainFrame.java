package io.simpleide.frame;

import javax.swing.JFrame;



public class MainFrame extends JFrame implements Runnable {

  @Override
  public void run() {
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    pack();
    setVisible(true);
  }

}
