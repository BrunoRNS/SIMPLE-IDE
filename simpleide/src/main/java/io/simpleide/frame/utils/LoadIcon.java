package io.simpleide.frame.utils;

import java.net.URL;
import java.util.MissingResourceException;

import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Loads image icons bundled with the application.
 *
 * @author LuisOtavio
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
public final class LoadIcon {

	/**
	 * Loads an icon from a classpath-relative resource name.
	 * @param url resource name resolved against this class
	 * @return the loaded icon
	 * @throws MissingResourceException when the resource cannot be found
	 */
	public static Icon loadingIcon(String url) throws MissingResourceException {

		URL urlIcon = LoadIcon.class.getResource(url);

		if (urlIcon == null) {
			throw new MissingResourceException("Icon resource not found", url, url);
		}

		Icon icon = new ImageIcon(urlIcon);

		return icon;
	}

}
