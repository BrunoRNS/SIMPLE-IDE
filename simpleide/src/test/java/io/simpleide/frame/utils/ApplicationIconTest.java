package io.simpleide.frame.utils;

import java.awt.Image;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

/** Checks runtime and packaged application icon variants for every supported target. */
class ApplicationIconTest {

	private static final List<Target> TARGETS = List.of(new Target("linux", "x86", ".png"),
			new Target("linux", "x64", ".png"), new Target("linux", "arm64", ".png"),
			new Target("windows", "x86", ".ico"), new Target("windows", "x64", ".ico"),
			new Target("windows", "arm64", ".ico"), new Target("macos", "x64", ".icns"),
			new Target("macos", "arm64", ".icns"));

	@Test
	void runtimeProvidesProportionallyReducedWindowAndTaskbarImages() {
		BufferedImage applicationIcon = ApplicationIcon.loadImage();
		assertThat(applicationIcon).isNotNull();
		assertThat(applicationIcon.getWidth()).isEqualTo(applicationIcon.getHeight());
		assertThat(ApplicationIcon.loadImage(64).getWidth()).isEqualTo(64);
		assertThat(ApplicationIcon.loadImage(64).getHeight()).isEqualTo(64);
		List<Image> windowImages = ApplicationIcon.loadWindowImages();
		assertThat(windowImages.stream().map(image -> image.getWidth(null)).toList()).containsExactly(16, 20, 24, 32,
				40, 48, 64, 128, 256);
		ApplicationIcon.setTaskbarIcon();
	}

	@Test
	void jpackageHasACompatibleIconAndPackageScriptForEveryOperatingSystemAndArchitecture() throws Exception {
		Path root = repositoryRoot();
		for (Target target : TARGETS) {
			assertTargetIcon(root, target);
		}
	}

	private void assertTargetIcon(Path root, Target target) throws Exception {
		String os = target.os();
		String arch = target.arch();
		String extension = target.extension();
		Path icon = root.resolve(
				"assets/icons/" + (extension.equals(".png") ? "simpleide_icon_256.png" : "simpleide_icon" + extension));
		assertThat(icon).as("icon for %s-%s", os, arch).exists();
		if (extension.equals(".png")) {
			BufferedImage linuxIcon = ImageIO.read(icon.toFile());
			assertThat(linuxIcon.getWidth()).isEqualTo(256);
			assertThat(linuxIcon.getHeight()).isEqualTo(256);
		}
		else {
			byte[] signature = Files.readAllBytes(icon);
			if (extension.equals(".ico")) {
				assertThat(signature[0]).isZero();
				assertThat(signature[1]).isZero();
				assertThat(signature[2]).isEqualTo((byte) 1);
				assertThat(signature[3]).isZero();
				assertThat(signature[4]).as("multi-resolution Windows icon").isGreaterThan((byte) 1);
			}
			else {
				assertThat(new String(signature, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("icns");
			}
		}
		String script = Files
			.readString(root.resolve(os.equals("windows") ? "scripts/package.ps1" : "scripts/package.sh"));
		assertThat(script).contains("--icon");
		assertThat(script).contains(extension.equals(".png") ? "simpleide_icon_256.png"
				: extension.equals(".ico") ? "simpleide_icon.ico" : "simpleide_icon.icns");
	}

	@Test
	void statusNotifierUsesKdeProtocolNamesAndASignedWindowId() throws Exception {
		assertThat(LinuxStatusNotifierTray.StatusNotifierItemInterface.class
			.getAnnotation(org.freedesktop.dbus.annotations.DBusInterfaceName.class)
			.value()).isEqualTo("org.kde.StatusNotifierItem");
		assertThat(LinuxStatusNotifierTray.StatusNotifierWatcher.class
			.getAnnotation(org.freedesktop.dbus.annotations.DBusInterfaceName.class)
			.value()).isEqualTo("org.kde.StatusNotifierWatcher");
		assertThat(LinuxStatusNotifierTray.StatusNotifierItemInterface.class.getMethod("getWindowId").getReturnType())
			.isEqualTo(int.class);
	}

	@Test
	void statusNotifierPixmapsIncludeSmallAndHighDensityCopiesInArgbOrder() {
		BufferedImage icon = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < icon.getHeight(); y++) {
			for (int x = 0; x < icon.getWidth(); x++) {
				icon.setRGB(x, y, 0x80402010);
			}
		}

		List<LinuxStatusNotifierTray.IconPixmap> pixmaps = LinuxStatusNotifierTray.createPixmaps(icon);

		assertThat(pixmaps).extracting(pixmap -> pixmap.width).containsExactly(16, 22, 24, 32, 48, 64, 128);
		assertThat(pixmaps).allSatisfy(pixmap -> {
			assertThat(pixmap.height).isEqualTo(pixmap.width);
			assertThat(pixmap.argb).hasSize(pixmap.width * pixmap.height * 4);
		});
		assertThat(pixmaps.get(2).argb).containsSubsequence((byte) 0x80, (byte) 0x40, (byte) 0x20, (byte) 0x10);
	}

	@Test
	void registersWithTheDesktopStatusNotifierWatcherWhenAvailable() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		assumeTrue(System.getenv("DBUS_SESSION_BUS_ADDRESS") != null);
		LinuxStatusNotifierTray tray = LinuxStatusNotifierTray.install(ApplicationIcon.loadImage(64), () -> {
		});
		assumeTrue(tray != null, "No StatusNotifierWatcher is active in this desktop session");
		tray.close();
	}

	private Path repositoryRoot() {
		Path current = Path.of("").toAbsolutePath();
		while (current != null && !Files.exists(current.resolve("assets/icons/simpleide_icon.ico"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("Unable to locate repository package icons");
		}
		return current;
	}

	private record Target(String os, String arch, String extension) {
	}

}
