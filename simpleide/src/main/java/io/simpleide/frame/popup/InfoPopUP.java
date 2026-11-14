package io.simpleide.frame.popup;

import io.simpleide.frame.utils.LoadIcon;
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

/**
 * Informational dialog with an information icon.
 *
 * <p>
 * Covered by {@code InfoPopUPTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 */
@Mappeable
public class InfoPopUP extends PopUP implements MappeableSupport {

	/**
	 * Creates an information dialog with explicit parent and option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param parent parent component used to position the dialog, or {@code null}
	 * @param option initially selected option
	 */
	public InfoPopUP(String title, Object message, JComponent parent, Object option) {
		super(parent, title, message, JOptionPane.INFORMATION_MESSAGE, LoadIcon.loadingIcon("info.png"), option);
	}

	/**
	 * Creates an information dialog with the default {@code Ok} option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 */
	public InfoPopUP(String title, Object message) {
		this(title, message, null, "Ok");
	}

	/**
	 * Creates an information dialog without a parent.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param option initially selected option
	 */
	public InfoPopUP(String title, Object message, Object option) {
		this(title, message, null, option);
	}

	/**
	 * Creates an information dialog attached to a parent with the default option.
	 * @param title dialog title
	 * @param message message displayed in the dialog
	 * @param parent parent component used to position the dialog
	 */
	public InfoPopUP(String title, Object message, JComponent parent) {
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
