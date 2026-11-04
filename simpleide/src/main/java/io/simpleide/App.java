/**
 * 
 */
package io.simpleide;

import io.simpleide.frame.MainFrame;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

/**
 * 
 */
public class App {

  /**
   * @param args
   */
  public static void main(String[] args) {
    ThreadMaster threadMaster = ThreadMaster.getInstance();
    threadMaster.register(new EDTNode(() -> new MainFrame().run()));
    threadMaster.initialize();
  }

}
