package io.simpleide.frame.popup;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;

/**
 * Error dialog with an error category as its title.
 *
 * <p>
 * Covered by {@code ErrorPopUPTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class ErrorPopUP extends PopUP implements MappeableSupport {

	/**
	 * Creates an error dialog attached to a parent component.
	 * @param error error message
	 * @param parent parent component used to position the dialog, or {@code null}
	 * @param type error category used as the dialog title
	 */
	public ErrorPopUP(String error, JComponent parent, ErrorTypePopUp type) {
		super(parent, type.toString(), error, JOptionPane.ERROR_MESSAGE, LoadIcon.loadingIcon("error.png"), "Ok");
	}

	/**
	 * Creates an error dialog without a parent component.
	 * @param error error message
	 * @param typeError error category used as the dialog title
	 */
	public ErrorPopUP(String error, ErrorTypePopUp typeError) {
		this(error, null, typeError);
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
