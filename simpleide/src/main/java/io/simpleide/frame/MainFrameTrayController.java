package io.simpleide.frame;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.Locale;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.utils.ApplicationIcon;
import io.simpleide.frame.utils.LinuxStatusNotifierTray;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * Manages the desktop tray controls associated with the main window.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class MainFrameTrayController {

	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrameTrayController.class);

	private final Runnable showWindow;

	private final Runnable requestClose;

	private final Object trayLock = new Object();

	private TrayIcon trayIcon;

	private AutoCloseable statusNotifierTray;

	private boolean trayDisposed;

	MainFrameTrayController(Runnable showWindow, Runnable requestClose) {
		this.showWindow = showWindow;
		this.requestClose = requestClose;
	}

	void install() {
		synchronized (trayLock) {
			if (trayDisposed || trayIcon != null || statusNotifierTray != null) {
				return;
			}
		}
		// Icon loading and D-Bus registration can block, so keep both away from the
		// EDT and never leave the frame unpainted while the desktop tray is contacted.
		ThreadMaster.getInstance().register(new RunnableNode(this::installTrayIconInBackground));
	}

	private void installTrayIconInBackground() {
		// Use the full-resolution source so high-density StatusNotifierItem pixmaps are
		// downscaled instead of being enlarged from the small AWT tray image.
		Image iconImage = ApplicationIcon.loadImage();
		if (iconImage == null) {
			logger.warn("The SIMPLE-IDE application icon is missing; no tray icon can be installed.");
			return;
		}
		if (isLinux()) {
			try {
				AutoCloseable notifier = LinuxStatusNotifierTray.install((BufferedImage) iconImage, showWindow);
				if (notifier != null) {
					synchronized (trayLock) {
						if (!trayDisposed) {
							statusNotifierTray = notifier;
							logger.info("SIMPLE-IDE registered a StatusNotifierItem tray icon.");
							return;
						}
					}
					closeTray(notifier);
					return;
				}
			}
			catch (DBusException | RuntimeException error) {
				logger.warn("Unable to register SIMPLE-IDE with the Linux StatusNotifierWatcher; trying AWT.", error);
			}
		}
		installAwtTrayIcon(iconImage);
	}

	private void installAwtTrayIcon(Image iconImage) {
		try {
			if (!SystemTray.isSupported()) {
				logger.warn("This desktop does not expose a compatible system tray for SIMPLE-IDE.");
				return;
			}

			PopupMenu menu = new PopupMenu();
			MenuItem show = new MenuItem("Show SIMPLE-IDE");
			show.addActionListener(new ActionNode(event -> showWindow.run()));
			menu.add(show);
			MenuItem quit = new MenuItem("Quit SIMPLE-IDE");
			quit.addActionListener(new ActionNode(event -> requestClose.run()));
			menu.add(quit);
			TrayIcon icon = new TrayIcon(iconImage, "SIMPLE-IDE", menu);
			icon.setImageAutoSize(true);
			icon.addActionListener(new ActionNode(event -> showWindow.run()));
			SystemTray tray = SystemTray.getSystemTray();
			tray.add(icon);
			synchronized (trayLock) {
				if (!trayDisposed) {
					trayIcon = icon;
					logger.info("SIMPLE-IDE registered an AWT system tray icon.");
					return;
				}
			}
			tray.remove(icon);
		}
		catch (AWTException | RuntimeException error) {
			logger.warn("Unable to add SIMPLE-IDE to the system tray.", error);
		}
	}

	void close() {
		AutoCloseable notifier;
		TrayIcon icon;
		synchronized (trayLock) {
			trayDisposed = true;
			notifier = statusNotifierTray;
			statusNotifierTray = null;
			icon = trayIcon;
			trayIcon = null;
		}
		if (icon != null) {
			try {
				if (SystemTray.isSupported()) {
					SystemTray.getSystemTray().remove(icon);
				}
			}
			catch (RuntimeException error) {
				logger.warn("Unable to remove SIMPLE-IDE from the AWT system tray.", error);
			}
		}
		if (notifier != null) {
			closeTray(notifier);
		}
	}

	private void closeTray(AutoCloseable tray) {
		try {
			tray.close();
		}
		catch (Exception error) {
			logger.warn("Unable to remove SIMPLE-IDE from the system tray.", error);
		}
	}

	private boolean isLinux() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
	}

}
