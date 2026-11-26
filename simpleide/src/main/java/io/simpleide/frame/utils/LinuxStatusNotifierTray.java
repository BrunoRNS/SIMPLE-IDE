package io.simpleide.frame.utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusBoundProperty;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusInterface;

import io.simpleide.thread.ThreadMaster;

/**
 * Linux tray registration for desktops that expose the StatusNotifierItem protocol.
 *
 * <p>
 * The implementation follows the KDE StatusNotifierItem specification, which is also
 * implemented by Waybar, KDE Plasma, LXQt, Cinnamon, MATE, XFCE plugins, GNOME's
 * AppIndicator extension and several other desktops. When no watcher is available the
 * caller can fall back to AWT's {@code SystemTray} or run without a tray icon.
 *
 * <p>
 * The registration performs blocking D-Bus calls. Callers must invoke
 * {@link #install(BufferedImage, Runnable)} from a background thread, never from the
 * Swing Event Dispatch Thread.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class LinuxStatusNotifierTray implements AutoCloseable {

	private static final String WATCHER_NAME = "org.kde.StatusNotifierWatcher";

	private static final String WATCHER_PATH = "/StatusNotifierWatcher";

	private static final String ITEM_PATH = "/StatusNotifierItem";

	private static final String ITEM_BUS_PREFIX = "org.kde.StatusNotifierItem";

	private static final int[] PIXMAP_SIZES = { 16, 22, 24, 32, 48, 64, 128 };

	private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger();

	private final DBusConnection connection;

	private final AtomicBoolean closed = new AtomicBoolean();

	private LinuxStatusNotifierTray(DBusConnection connection) {
		this.connection = connection;
	}

	/**
	 * Registers the application with the current desktop's StatusNotifierWatcher.
	 * @param image bundled application image
	 * @param activate action invoked when the tray item is activated
	 * @return the active registration, or {@code null} when no watcher is available
	 * @throws DBusException when a detected watcher rejects registration
	 */
	public static LinuxStatusNotifierTray install(BufferedImage image, Runnable activate) throws DBusException {
		if (image == null || !hasSessionBus()) {
			return null;
		}

		DBusConnection connection = DBusConnectionBuilder.forSessionBus().build();
		try {
			DBus bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class, false);
			if (!bus.NameHasOwner(WATCHER_NAME)) {
				closeConnection(connection);
				return null;
			}
			StatusNotifierWatcher watcher = connection.getRemoteObject(WATCHER_NAME, WATCHER_PATH,
					StatusNotifierWatcher.class, false);
			String busName = requestItemBusName(connection);
			StatusNotifierItem item = new StatusNotifierItem(image, activate);
			connection.exportObject(ITEM_PATH, item);
			registerWithWatcher(watcher, busName);
			return new LinuxStatusNotifierTray(connection);
		}
		catch (DBusException | RuntimeException error) {
			closeConnection(connection);
			throw error;
		}
	}

	private static void registerWithWatcher(StatusNotifierWatcher watcher, String busName) throws DBusException {
		try {
			watcher.RegisterStatusNotifierItem(busName);
		}
		catch (RuntimeException busNameFailure) {
			// The specification also accepts an object path. The watcher then uses the
			// unique connection name of the sender, which works with hosts that reject
			// well-known item bus names.
			try {
				watcher.RegisterStatusNotifierItem(ITEM_PATH);
			}
			catch (RuntimeException objectPathFailure) {
				objectPathFailure.addSuppressed(busNameFailure);
				throw objectPathFailure;
			}
		}
	}

	private static String requestItemBusName(DBusConnection connection) throws DBusException {
		long pid = ProcessHandle.current().pid();
		DBusException lastFailure = null;
		for (int attempt = 0; attempt < 8; attempt++) {
			String busName = ITEM_BUS_PREFIX + "-" + pid + "-" + NEXT_ITEM_ID.getAndIncrement();
			try {
				connection.requestBusName(busName);
				return busName;
			}
			catch (DBusException error) {
				lastFailure = error;
			}
		}
		throw lastFailure == null ? new DBusException("Unable to acquire a StatusNotifierItem bus name.") : lastFailure;
	}

	private static boolean hasSessionBus() {
		String address = System.getenv("DBUS_SESSION_BUS_ADDRESS");
		return address != null && !address.isBlank();
	}

	private static void closeConnection(DBusConnection connection) {
		try {
			connection.close();
		}
		catch (IOException ignored) {
			// The connection is already unavailable and cannot keep a tray item alive.
		}
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		closeConnection(connection);
	}

	static List<IconPixmap> createPixmaps(BufferedImage source) {
		List<IconPixmap> pixmaps = new ArrayList<>(PIXMAP_SIZES.length);
		for (int size : PIXMAP_SIZES) {
			BufferedImage image = scale(source, size);
			byte[] argb = new byte[size * size * 4];
			int offset = 0;
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int pixel = image.getRGB(x, y);
					argb[offset++] = (byte) (pixel >>> 24);
					argb[offset++] = (byte) (pixel >>> 16);
					argb[offset++] = (byte) (pixel >>> 8);
					argb[offset++] = (byte) pixel;
				}
			}
			pixmaps.add(new IconPixmap(size, size, argb));
		}
		return List.copyOf(pixmaps);
	}

	private static BufferedImage scale(BufferedImage source, int size) {
		BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, size, size, null);
		}
		finally {
			graphics.dispose();
		}
		return scaled;
	}

	@DBusInterfaceName("org.kde.StatusNotifierWatcher")
	public interface StatusNotifierWatcher extends DBusInterface {

		void RegisterStatusNotifierItem(String service);

		@Override
		default String getObjectPath() {
			return WATCHER_PATH;
		}

	}

	@DBusInterfaceName("org.kde.StatusNotifierItem")
	public interface StatusNotifierItemInterface extends DBusInterface {

		@DBusBoundProperty(name = "Category", access = DBusProperty.Access.READ)
		String getCategory();

		@DBusBoundProperty(name = "Id", access = DBusProperty.Access.READ)
		String getId();

		@DBusBoundProperty(name = "Title", access = DBusProperty.Access.READ)
		String getTitle();

		@DBusBoundProperty(name = "Status", access = DBusProperty.Access.READ)
		String getStatus();

		@DBusBoundProperty(name = "WindowId", access = DBusProperty.Access.READ)
		int getWindowId();

		@DBusBoundProperty(name = "IconName", access = DBusProperty.Access.READ)
		String getIconName();

		@DBusBoundProperty(name = "IconPixmap", access = DBusProperty.Access.READ)
		List<IconPixmap> getIconPixmap();

		@DBusBoundProperty(name = "OverlayIconName", access = DBusProperty.Access.READ)
		String getOverlayIconName();

		@DBusBoundProperty(name = "OverlayIconPixmap", access = DBusProperty.Access.READ)
		List<IconPixmap> getOverlayIconPixmap();

		@DBusBoundProperty(name = "AttentionIconName", access = DBusProperty.Access.READ)
		String getAttentionIconName();

		@DBusBoundProperty(name = "AttentionIconPixmap", access = DBusProperty.Access.READ)
		List<IconPixmap> getAttentionIconPixmap();

		@DBusBoundProperty(name = "AttentionMovieName", access = DBusProperty.Access.READ)
		String getAttentionMovieName();

		@DBusBoundProperty(name = "ToolTip", access = DBusProperty.Access.READ)
		IconToolTip getToolTip();

		@DBusBoundProperty(name = "ItemIsMenu", access = DBusProperty.Access.READ)
		boolean isItemIsMenu();

		void Activate(int x, int y);

		void ContextMenu(int x, int y);

		void SecondaryActivate(int x, int y);

		void Scroll(int delta, String orientation);

		@Override
		default String getObjectPath() {
			return ITEM_PATH;
		}

	}

	public static final class IconPixmap extends Struct {

		@Position(0)
		public final int width;

		@Position(1)
		public final int height;

		@Position(2)
		public final byte[] argb;

		public IconPixmap(int width, int height, byte[] argb) {
			this.width = width;
			this.height = height;
			this.argb = argb;
		}

	}

	public static final class IconToolTip extends Struct {

		@Position(0)
		public final String iconName;

		@Position(1)
		public final List<IconPixmap> iconPixmap;

		@Position(2)
		public final String title;

		@Position(3)
		public final String description;

		public IconToolTip(String iconName, List<IconPixmap> iconPixmap, String title, String description) {
			this.iconName = iconName;
			this.iconPixmap = iconPixmap;
			this.title = title;
			this.description = description;
		}

	}

	private static final class StatusNotifierItem implements StatusNotifierItemInterface {

		private final List<IconPixmap> pixmaps;

		private final Runnable activate;

		private StatusNotifierItem(BufferedImage image, Runnable activate) {
			this.pixmaps = createPixmaps(image);
			this.activate = activate;
		}

		@Override
		public String getCategory() {
			return "ApplicationStatus";
		}

		@Override
		public String getId() {
			return "simpleide";
		}

		@Override
		public String getTitle() {
			return "SIMPLE-IDE";
		}

		@Override
		public String getStatus() {
			return "Active";
		}

		@Override
		public int getWindowId() {
			return 0;
		}

		@Override
		public String getIconName() {
			return "";
		}

		@Override
		public List<IconPixmap> getIconPixmap() {
			return pixmaps;
		}

		@Override
		public String getOverlayIconName() {
			return "";
		}

		@Override
		public List<IconPixmap> getOverlayIconPixmap() {
			return List.of();
		}

		@Override
		public String getAttentionIconName() {
			return "";
		}

		@Override
		public List<IconPixmap> getAttentionIconPixmap() {
			return List.of();
		}

		@Override
		public String getAttentionMovieName() {
			return "";
		}

		@Override
		public IconToolTip getToolTip() {
			return new IconToolTip("", pixmaps, "SIMPLE-IDE", "Application is running");
		}

		@Override
		public boolean isItemIsMenu() {
			return false;
		}

		@Override
		public void Activate(int x, int y) {
			invokeActivate();
		}

		@Override
		public void ContextMenu(int x, int y) {
			invokeActivate();
		}

		@Override
		public void SecondaryActivate(int x, int y) {
			invokeActivate();
		}

		@Override
		public void Scroll(int delta, String orientation) {
			// Scrolling over the tray icon does not have an application action.
		}

		private void invokeActivate() {
			ThreadMaster.getInstance().executeOnEDT(activate);
		}

	}

}
