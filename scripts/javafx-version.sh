#!/usr/bin/env sh
#
# Selects the JavaFX version used by the optional audio/video viewer.
#
# The JavaFX-backed viewers are an optional feature. This script picks the newest
# supported JavaFX release for the current JDK and platform, and prints "none"
# when no JavaFX native artifact can run here. The Makefile forwards the result
# to Maven:
#
#   -Djavafx.version=<version>
#   -Dmedia.viewer.skip=true    (when the output is "none")
#
# Policy:
#
#   JDK 21+    JavaFX 23.0.2, when the platform glibc is new enough.
#   JDK 17-20  JavaFX 22.0.2, when the platform glibc is new enough.
#
# OpenJFX publishes native artifacts only for Linux x64/arm64, macOS x64/arm64
# and Windows x64. Linux x64 needs glibc 2.17; Linux arm64 needs glibc 2.34 for
# both JavaFX 22 and 23. Unsupported platforms (32-bit x86, Windows arm64,
# Linux arm32) print "none" so the build does not download JavaFX and media
# files fall back to the hex editor.
#
# Set SIMPLEIDE_JAVAFX_VERSION to force a version, or
# SIMPLEIDE_JAVAFX_DISABLE=1 to disable media playback.

set -eu

if [ "${SIMPLEIDE_JAVAFX_DISABLE:-}" = "1" ]; then
	echo none
	exit 0
fi

if [ -n "${SIMPLEIDE_JAVAFX_VERSION:-}" ]; then
	echo "$SIMPLEIDE_JAVAFX_VERSION"
	exit 0
fi

JAVA_BIN=java
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
	JAVA_BIN="$JAVA_HOME/bin/java"
fi

JDK_VERSION=$(
	"$JAVA_BIN" -XshowSettings:properties -version 2>&1 |
		sed -n 's/.*java\.specification\.version = //p' |
		head -n 1
)
case "$JDK_VERSION" in
	1.*) JDK_VERSION=${JDK_VERSION#1.} ;;
esac
JDK_MAJOR=${JDK_VERSION%%.*}

OS=$(uname -s 2>/dev/null || echo unknown)
ARCH=$(uname -m 2>/dev/null || echo unknown)

# Minimum glibc reported by each OpenJFX Linux classifier. OpenJFX 22 and 23
# use the same floor per architecture.
case "$ARCH" in
	x86_64|amd64) GLIBC_22=2.17; GLIBC_23=2.17 ;;
	aarch64|arm64) GLIBC_22=2.34; GLIBC_23=2.34 ;;
	*) GLIBC_22=; GLIBC_23= ;;
esac

GLIBC_VERSION=
if [ "$OS" = "Linux" ]; then
	GLIBC_VERSION=$(getconf GNU_LIBC_VERSION 2>/dev/null | awk 'NR == 1 { print $2 }') || GLIBC_VERSION=
	if [ -z "$GLIBC_VERSION" ]; then
		echo none
		exit 0
	fi
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

if [ "$OS" = "Linux" ]; then
	if [ -z "$GLIBC_22" ]; then
		# No OpenJFX native artifact exists for this architecture.
		echo none
		exit 0
	fi
	if [ "$JDK_MAJOR" -ge 21 ] && version_ge "$GLIBC_VERSION" "$GLIBC_23"; then
		echo 23.0.2
	elif [ "$JDK_MAJOR" -ge 17 ] && version_ge "$GLIBC_VERSION" "$GLIBC_22"; then
		echo 22.0.2
	else
		echo none
	fi
	exit 0
fi

# macOS and Windows have no glibc requirement, but OpenJFX publishes native
# artifacts only for macOS x64/arm64 and Windows x64.
case "$OS" in
	Darwin)
		case "$ARCH" in
			x86_64|amd64|aarch64|arm64) ;;
			*) echo none; exit 0 ;;
		esac
		;;
	MINGW*|MSYS*|CYGWIN*)
		case "$ARCH" in
			x86_64|amd64) ;;
			*) echo none; exit 0 ;;
		esac
		;;
	*) echo none; exit 0 ;;
	esac

if [ "$JDK_MAJOR" -ge 21 ]; then
	echo 23.0.2
else
	echo 22.0.2
fi
