/**
 * 
 */
package io.simpleide;

import io.simpleide.frame.MainFrame;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * 
 */
public class App {

  /**
   * @param args
   */
  public static void main(String[] args) {

    FlatDarkLaf.setup();
    Runnable frame = new MainFrame();
    SwingUtilities.invokeLater(frame);

  }

}
