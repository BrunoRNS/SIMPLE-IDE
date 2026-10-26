/**
 * 
 */
package io.simpleide;

import io.simpleide.frame.MainFrame;
import javax.swing.SwingUtilities;

/**
 * 
 */
public class App {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		Runnable frame = new MainFrame();
		SwingUtilities.invokeLater(frame);

	}

}
