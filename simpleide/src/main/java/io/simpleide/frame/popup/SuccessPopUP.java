package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.MappeableSupport;

/**
 * Success dialog with a success icon.
 *
 * <p>
 * Covered by {@code SuccessPopUPTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class SuccessPopUP extends PopUP implements MappeableSupport {

	/**
	 * Creates a success dialog with explicit parent and option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param parent parent component used to position the dialog, or {@code null}
	 * @param option initially selected option
	 */
	public SuccessPopUP(String title, String message, JComponent parent, Object option) {
		super(parent, title, message, JOptionPane.INFORMATION_MESSAGE, LoadIcon.loadingIcon("sucess.png"), option);
	}

	/**
	 * Creates a success dialog with the default {@code Ok} option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 */
	public SuccessPopUP(String title, String message) {
		this(title, message, null, "Ok");
	}

	/**
	 * Creates a success dialog without a parent.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param option initially selected option
	 */
	public SuccessPopUP(String title, String message, Object option) {
		this(title, message, null, option);
	}

	/**
	 * Creates a success dialog attached to a parent with the default option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param parent parent component used to position the dialog
	 */
	public SuccessPopUP(String title, String message, JComponent parent) {
		this(title, message, parent, "Ok");
	}

	/**
	 * Returns the popup data as a mutable map.
	 * @return the mapped properties
	 */
	@Override
	public Map<String, Object> getMap() {
		return getMaping();
	}

	/**
	 * Replaces the popup data from a map.
	 * @param m mapped properties to apply
	 */
	@Override
	public void setMap(Map<String, Object> m) {
		setMaping(m);
	}

}
