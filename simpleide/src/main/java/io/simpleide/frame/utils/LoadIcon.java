package io.simpleide.frame.utils;

import java.net.URL;
import java.util.MissingResourceException;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public final class LoadIcon {
    public static Icon loadingIcon(String url) throws MissingResourceException{

        URL urlIcon = LoadIcon.class.getResource(url);

        if (urlIcon == null) {
            throw new MissingResourceException("Icon resource not found", url, url);
        }

        Icon icon = new ImageIcon(urlIcon);

        return icon;
    }
}
