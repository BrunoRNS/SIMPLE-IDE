#!/usr/bin/env sh
#
# Selects the optional JavaFX media variant for the current JDK and platform.
#
# The audio/video viewers are optional. This script prints one of:
#
#   nojavafx   build without JavaFX; media files use the hex editor
#   javafx22   compile and package with JavaFX 22
#   javafx23   compile and package with JavaFX 23
#
# The Makefile forwards the result to Maven as:
#
#   -Dsimpleide.media.variant=<variant>
#
# Automatic policy ("auto"):
#
#   JDK 21+    javafx23 on a supported platform
#   JDK 17-20  javafx22 on a supported platform
#   otherwise  nojavafx
#
# OpenJFX publishes native artifacts for Linux x64, Windows x64, macOS x64 and
# macOS arm64. Linux arm64 is always nojavafx by project policy: the arm64
# natives need a much newer glibc than the release baseline, so the default and
# release builds must not depend on them. Linux x86, Windows x86 and Windows
# arm64 have no usable OpenJFX natives and also use nojavafx.
#
# Environment:
#
#   SIMPLEIDE_MEDIA_VARIANT=auto|nojavafx|javafx22|javafx23
#       Force a variant. On an unsupported platform (including Linux arm64)
#       the result is still nojavafx.
#   SIMPLEIDE_JAVAFX_DISABLE=1
#       Backwards-compatible alias for SIMPLEIDE_MEDIA_VARIANT=nojavafx.
#   SIMPLEIDE_JAVAFX_VERSION=<version>
#       Backwards-compatible version override. A value starting with 22 maps to
#       javafx22 and a value starting with 23 maps to javafx23; "none" maps to
#       nojavafx.
#
# The project policy and Maven profile architecture are documented in
# docs/reference/JAVAFX-MEDIA.md.

set -eu

if [ "${SIMPLEIDE_JAVAFX_DISABLE:-}" = "1" ]; then
	echo nojavafx
	exit 0
fi

REQUESTED=${SIMPLEIDE_MEDIA_VARIANT:-auto}
if [ "$REQUESTED" = "auto" ] && [ -n "${SIMPLEIDE_JAVAFX_VERSION:-}" ]; then
	case "$SIMPLEIDE_JAVAFX_VERSION" in
		22|22.*) REQUESTED=javafx22 ;;
		23|23.*) REQUESTED=javafx23 ;;
		none|nojavafx|disabled) REQUESTED=nojavafx ;;
		auto) REQUESTED=auto ;;
		*)
			echo "error: unsupported SIMPLEIDE_JAVAFX_VERSION '$SIMPLEIDE_JAVAFX_VERSION'" >&2
			exit 2
			;;
	esac
fi

case "$REQUESTED" in
	auto|nojavafx|javafx22|javafx23) ;;
	*)
		echo "error: unsupported SIMPLEIDE_MEDIA_VARIANT '$REQUESTED'" >&2
		exit 2
		;;
esac

JAVA_BIN=java
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
	JAVA_BIN="$JAVA_HOME/bin/java"
fi

JAVA_PROPERTIES=$("$JAVA_BIN" -XshowSettings:properties -version 2>&1)
JDK_VERSION=$(printf '%s\n' "$JAVA_PROPERTIES" | sed -n 's/.*java\.specification\.version = //p' | head -n 1)
case "$JDK_VERSION" in
	1.*) JDK_VERSION=${JDK_VERSION#1.} ;;
esac
JDK_MAJOR=${JDK_VERSION%%.*}

# Read the platform from the JDK so the selection matches Maven's os.arch and
# os.name activation, even when a 64-bit shell drives a 32-bit JDK.
JAVA_OS=$(printf '%s\n' "$JAVA_PROPERTIES" | sed -n 's/.*os\.name = //p' | head -n 1)
JAVA_ARCH=$(printf '%s\n' "$JAVA_PROPERTIES" | sed -n 's/.*os\.arch = //p' | head -n 1)

case "$JAVA_OS" in
	Linux*) OS=Linux ;;
	Mac*|Darwin*) OS=Darwin ;;
	Windows*) OS=Windows ;;
	*) OS=unknown ;;
esac

case "$JAVA_ARCH" in
	amd64|x86_64) ARCH=x86_64 ;;
	aarch64|arm64) ARCH=aarch64 ;;
	x86|i386|i486|i586|i686) ARCH=x86 ;;
	*) ARCH=$JAVA_ARCH ;;
esac

# Linux arm64 is always built without JavaFX. Keep this check before the forced
# variant branch so the policy cannot be overridden accidentally.
if [ "$OS" = "Linux" ] && [ "$ARCH" = "aarch64" ]; then
	echo nojavafx
	exit 0
fi

version_ge() {
	awk -v a="$1" -v b="$2" 'BEGIN {
		na = split(a, x, ".")
		nb = split(b, y, ".")
		for (i = 1; i <= 3; i++) {
			xv = (i <= na ? x[i] + 0 : 0)
			yv = (i <= nb ? y[i] + 0 : 0)
			if (xv > yv) exit 0
			if (xv < yv) exit 1
		}
		exit 0
	}'
}

SUPPORTED=0
case "$OS" in
	Linux)
		# OpenJFX x64 natives use glibc 2.17; this also matches the minimum that
		# the project already requires from the JDK.
		if [ "$ARCH" = "x86_64" ]; then
			GLIBC_VERSION=$(getconf GNU_LIBC_VERSION 2>/dev/null | awk 'NR == 1 { print $2 }') || GLIBC_VERSION=
			if [ -n "$GLIBC_VERSION" ] && version_ge "$GLIBC_VERSION" 2.17; then
				SUPPORTED=1
			fi
		fi
		;;
	Darwin)
		case "$ARCH" in
			x86_64|aarch64) SUPPORTED=1 ;;
		esac
		;;
	Windows)
		if [ "$ARCH" = "x86_64" ]; then
			SUPPORTED=1
		fi
		;;
esac

if [ "$REQUESTED" != "auto" ]; then
	if [ "$SUPPORTED" = "1" ]; then
		echo "$REQUESTED"
	else
		echo nojavafx
	fi
	exit 0
fi

if [ "$SUPPORTED" != "1" ]; then
	echo nojavafx
	exit 0
fi

if [ "$JDK_MAJOR" -ge 21 ]; then
	echo javafx23
else
	echo javafx22
fi
