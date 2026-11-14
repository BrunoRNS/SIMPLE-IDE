package io.simpleide.ui.terminal.core.busybox;

import io.simpleide.language.config.LangPlatform;
import java.io.IOException;
import java.util.Objects;

public final class BusyBoxPlatform {

  private BusyBoxPlatform() {}

  public static String getResourcePath(LangPlatform platform) throws IOException {
    Objects.requireNonNull(platform, "Platform must not be null.");
    return switch (platform) {
      case WINDOWSX86 -> "windows/windows-x86/bin/busybox.exe";
      case WINDOWSX64UNICODE -> "windows/windows-x64-unicode/bin/busybox.exe";
      case WINDOWSARM64 -> "windows/windows-arm64/bin/busybox.exe";
      case LINUXX86 -> "linux/linux-x86/bin/busybox";
      case LINUXX64 -> "linux/linux-x64/bin/busybox";
      case LINUXARM64 -> "linux/linux-arm64/bin/busybox";
      case MACOSINTEL -> "macos/macos-intel/bin/busybox";
      case MACOSAARCH64 -> "macos/macos-arm64/bin/busybox";
      case UNKNOWN -> throw new IOException("BusyBox is not available for an unknown platform.");
    };
  }

  public static LangPlatform getCompatiblePlatform(LangPlatform platform, String windowsVersion) {
    Objects.requireNonNull(platform, "Platform must not be null.");
    if (platform != LangPlatform.WINDOWSX64UNICODE || isWindows10OrLater(windowsVersion)) {
      return platform;
    }
    return LangPlatform.WINDOWSX86;
  }

  public static BusyBoxExecutable extract(LangPlatform platform) throws IOException {
    String resourcePath = getResourcePath(platform);
    String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
    BusyBoxExecutable executable = BusyBoxExecutable.create(fileName);
    try {
      executable.copyFrom(BusyBoxPlatform.class, resourcePath);
      if (!"windows".equals(platform.getOs())) {
        executable.makeExecutable();
      }
      return executable;
    } catch (IOException | RuntimeException exception) {
      try {
        executable.close();
      } catch (IOException cleanupException) {
        exception.addSuppressed(cleanupException);
      }
      throw exception;
    }
  }

  private static boolean isWindows10OrLater(String windowsVersion) {
    if (windowsVersion == null) {
      return false;
    }
    try {
      int majorVersion = Integer.parseInt(windowsVersion.split("\\.", 2)[0]);
      return majorVersion >= 10;
    } catch (NumberFormatException exception) {
      return false;
    }
  }
}
