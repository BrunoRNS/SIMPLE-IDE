/**
 * 
 */
package io.simpleide;

import javax.swing.SwingUtilities;

import io.simpleide.frame.MainFrame;

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
