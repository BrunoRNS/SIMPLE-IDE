package io.simpleide.frame.popup;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Base class for managed modal dialogs.
 *
 * <p>
 * A popup wraps a {@link JOptionPane} and can be shown either through {@link #run()} or
 * through {@link #startManaged()}, which registers the dialog with the shared thread
 * manager and returns a node. Only one instance of a popup may be visible at a time.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 */
public abstract class PopUP implements Runnable {

	protected volatile boolean on = false;

	protected volatile String title;

	protected JComponent parent = null;

	protected JDialog jd = null;

	protected JOptionPane jp;

	protected final Object[] options = { "Ok" };

	/**
	 * Creates a popup with explicit option pane settings.
	 * @param parent parent component used to position the dialog, or {@code null}
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param messageType {@link JOptionPane} message type
	 * @param optionType {@link JOptionPane} option type
	 * @param icon dialog icon, or {@code null}
	 * @param initialValue initially selected option
	 */
	public PopUP(JComponent parent, String title, Object message, int messageType, int optionType, Icon icon,
			Object initialValue) {

		this.parent = parent;
		this.title = title;
		this.jp = new JOptionPane(message, messageType, optionType, icon, options, initialValue);
	}

	/**
	 * Creates a popup with the default option type.
	 * @param parent parent component used to position the dialog, or {@code null}
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param messageType {@link JOptionPane} message type
	 * @param icon dialog icon, or {@code null}
	 * @param initialValue initially selected option
	 */
	public PopUP(JComponent parent, String title, Object message, int messageType, Icon icon, Object initialValue) {
		this(parent, title, message, messageType, JOptionPane.DEFAULT_OPTION, icon, initialValue);
	}

	/**
	 * Returns the dialog title.
	 * @return the current title
	 */
	public String getTitle() {
		return this.title;
	}

	/**
	 * Changes the dialog title.
	 * @param s new title
	 */
	public void setTitle(String s) {
		this.title = s;
	}

	/**
	 * Returns the cached dialog instance.
	 * @return the dialog, or {@code null} before it is first shown
	 */
	public JDialog getDialog() {
		return this.jd;
	}

	/**
	 * Replaces the cached dialog instance.
	 * @param jd dialog to cache
	 */
	public void setDialog(JDialog jd) {
		this.jd = jd;
	}

	/**
	 * Displays the dialog through {@link #startManaged()}.
	 * @throws NullPointerException when the option pane is missing
	 * @throws IllegalStateException when the popup is already visible
	 */
	@Override
	public void run() throws NullPointerException, IllegalStateException {
		startManaged();
	}

	/**
	 * Registers and displays the dialog on the Swing Event Dispatch Thread.
	 * @return the node that manages the dialog lifecycle
	 * @throws NullPointerException when the option pane is missing
	 * @throws IllegalStateException when the popup is already visible
	 */
	public NodePattern startManaged() {
		synchronized (this) {
			if (this.jp == null) {
				throw new NullPointerException("JOptionPane can't be null!");
			}
			if (this.on) {
				throw new IllegalStateException("PopUP is already on!");
			}
			this.on = true;
		}

		try {
			return ThreadMaster.getInstance().executeOnEDT(this::showDialog, () -> {
				synchronized (PopUP.this) {
					PopUP.this.on = false;
				}
			});
		}
		catch (RuntimeException | Error exception) {
			synchronized (this) {
				this.on = false;
			}
			throw exception;
		}
	}

	private void showDialog() {
		if (this.jd == null) {
			this.jd = jp.createDialog(this.parent, this.title);
		}
		try {
			this.jd.setVisible(true);
		}
		finally {
			onDialogClosed();
		}
	}

	/**
	 * Hook invoked after the dialog has been hidden.
	 */
	protected void onDialogClosed() {
	}

	/**
	 * Returns a human-readable description of the popup state and mapped data.
	 * @return the description
	 */
	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		sb.append("The PopUP is on? ").append(this.on).append("\n");
		sb.append("Class ").append(this.title).append(" Data:\n");

		Map<String, Object> m = getMap();
		if (m == null) {
			sb.append("null\n");
		}
		else {
			m.forEach((name, obj) -> {
				sb.append(name).append(" : ").append(obj.toString()).append("\n");
			});
		}

		return sb.toString();
	}

	/**
	 * Returns the popup data as a mutable map.
	 * @return the mapped properties
	 */
	public abstract Map<String, Object> getMap();

	/**
	 * Replaces the popup data from a map.
	 * @param m mapped properties to apply
	 */
	public abstract void setMap(Map<String, Object> m);

}
