package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;

/**
 * Warning dialog with a warning icon.
 *
 * <p>
 * Covered by {@code WarningPopUPTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class WarningPopUP extends PopUP implements MappeableSupport {

	/**
	 * Creates a warning dialog with explicit parent and option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param parent parent component used to position the dialog, or {@code null}
	 * @param option initially selected option
	 */
	public WarningPopUP(String title, String message, JComponent parent, Object option) {
		super(parent, title, message, JOptionPane.WARNING_MESSAGE, LoadIcon.loadingIcon("warning.png"), option);
	}

	/**
	 * Creates a warning dialog with the default {@code Ok} option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 */
	public WarningPopUP(String title, String message) {
		this(title, message, null, "Ok");
	}

	/**
	 * Creates a warning dialog without a parent.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param option initially selected option
	 */
	public WarningPopUP(String title, String message, Object option) {
		this(title, message, null, option);
	}

	/**
	 * Creates a warning dialog attached to a parent with the default option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param parent parent component used to position the dialog
	 */
	public WarningPopUP(String title, String message, JComponent parent) {
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
