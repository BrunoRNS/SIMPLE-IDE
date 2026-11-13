/**
 * 
 */
package io.simpleide;

import io.simpleide.frame.MainFrame;
import io.simpleide.language.config.LangConfigHolder;
import io.simpleide.language.config.LangSetup;
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
     new LangSetup(LangConfigHolder.LANG_CONFIG).run();

    ThreadMaster threadMaster = ThreadMaster.getInstance();
    threadMaster.register(new EDTNode(() -> new MainFrame().run()));
    threadMaster.initialize();
    
  }

}
