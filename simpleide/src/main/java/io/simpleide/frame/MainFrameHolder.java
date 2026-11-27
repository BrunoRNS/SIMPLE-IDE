package io.simpleide.frame;

public class MainFrameHolder {

	private static MainFrame mainFrame;

	public static MainFrame getMainFrame() {
		if (mainFrame == null) {
			mainFrame = new MainFrame();
		}
		return mainFrame;
	}

	public static MainFrame getMainFrameIfPresent() {
		return mainFrame;
	}

}