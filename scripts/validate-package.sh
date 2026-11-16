#!/usr/bin/env sh
#
# Validates the packages produced by scripts/package.sh for the current
# OS/architecture. Used by CI after `make pkg` so a broken executable never
# reaches a release unnoticed.
#
# Checks:
#   .zip  the portable app image extracts, its launcher and bundled JDK are valid
#   .deb  dpkg-deb can read the control metadata and file list
#   .rpm  rpm can read the package metadata
#   .dmg  hdiutil can read the image and its app image has a bundled JDK
#
# Exits non-zero when a package is missing or invalid.

set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PROPERTIES="$ROOT_DIR/simpleide/src/main/resources/simpleide.properties"
PKG_DIR="$ROOT_DIR/simpleide/target/pkg"

# shellcheck source=scripts/macos-min-version.sh
. "$SCRIPT_DIR/macos-min-version.sh"

VERSION=$(sed -n 's/^version=//p' "$PROPERTIES" | tr -d '\r' | head -n 1)
if [ -z "$VERSION" ]; then
    echo "error: unable to read the application version from $PROPERTIES" >&2
    exit 1
fi

detect_os() {
    case "$(uname -s)" in
        Linux*) echo "linux" ;;
        Darwin*) echo "macos" ;;
        *) echo "unknown" ;;
    esac
}

detect_arch() {
    case "$(uname -m)" in
        x86_64|amd64) echo "x64" ;;
        aarch64|arm64) echo "arm64" ;;
        i386|i486|i586|i686|x86) echo "x86" ;;
        *) echo "$(uname -m)" ;;
    esac
}

OS=${SIMPLEIDE_PKG_OS:-$(detect_os)}
ARCH=${SIMPLEIDE_PKG_ARCH:-$(detect_arch)}
ARTIFACT_BASE="simpleide-v${VERSION}-${OS}-${ARCH}"

fail() {
    echo "PACKAGE VALIDATION FAILED: $1" >&2
    exit 1
}

ok() {
    echo "VALID: $1"
}

[ -d "$PKG_DIR" ] || fail "the package directory was not found: $PKG_DIR"

# Prints the LSMinimumSystemVersion expected for the JDK used to build the
# package, or nothing when the JDK is not in the known table.
expected_macos_min_version() {
    if [ -n "${SIMPLEIDE_MACOS_MIN_VERSION:-}" ]; then
        echo "$SIMPLEIDE_MACOS_MIN_VERSION"
        return 0
    fi
    JPACKAGE_BIN=$(command -v jpackage 2>/dev/null || true)
    JAVA_BIN=java
    if [ -n "$JPACKAGE_BIN" ] && [ -x "${JPACKAGE_BIN%/*}/java" ]; then
        JAVA_BIN="${JPACKAGE_BIN%/*}/java"
    fi
    simpleide_macos_min_version "$(simpleide_jdk_major "$JAVA_BIN")"
}

versions_equal() {
    awk -v a="$1" -v b="$2" 'BEGIN {
        na = split(a, x, ".");
        nb = split(b, y, ".");
        xa = (na > 1 ? x[2] + 0 : 0);
        yb = (nb > 1 ? y[2] + 0 : 0);
        exit !(x[1] + 0 == y[1] + 0 && xa == yb);
    }'
}

validate_zip() {
    PACKAGE="$PKG_DIR/$ARTIFACT_BASE.zip"
    [ -f "$PACKAGE" ] || fail "$PACKAGE was not produced."
    EXTRACT_DIR="$PKG_DIR/validate-zip"
    rm -rf "$EXTRACT_DIR"
    mkdir -p "$EXTRACT_DIR"
    unzip -q "$PACKAGE" -d "$EXTRACT_DIR" || fail "the app image zip is not a readable zip: $PACKAGE"
    if [ "$OS" = "macos" ]; then
        LAUNCHER="$EXTRACT_DIR/SimpleIDE.app/Contents/MacOS/SimpleIDE"
        RUNTIME_HOME="$EXTRACT_DIR/SimpleIDE.app/Contents/runtime/Contents/Home"
    else
        LAUNCHER="$EXTRACT_DIR/SimpleIDE/bin/SimpleIDE"
        RUNTIME_HOME="$EXTRACT_DIR/SimpleIDE/lib/runtime"
    fi
    [ -f "$LAUNCHER" ] || fail "the app image zip has no launcher at $LAUNCHER: $PACKAGE"
    [ -x "$LAUNCHER" ] || fail "the app image launcher is not executable: $LAUNCHER"
    [ -x "$RUNTIME_HOME/bin/java" ] \
        || fail "the app image zip has no bundled Java launcher at $RUNTIME_HOME/bin/java: $PACKAGE"
    grep -q 'jdk.compiler' "$RUNTIME_HOME/release" \
        || fail "the app image zip runtime does not provide the jdk.compiler module: $PACKAGE"
    rm -rf "$EXTRACT_DIR"
    ok "$PACKAGE"
}

validate_deb() {
    PACKAGE="$PKG_DIR/$ARTIFACT_BASE.deb"
    [ -f "$PACKAGE" ] || fail "$PACKAGE was not produced."
    command -v dpkg-deb >/dev/null 2>&1 || fail "dpkg-deb is not available to validate $PACKAGE"
    dpkg-deb -I "$PACKAGE" >/dev/null 2>&1 || fail "dpkg-deb cannot read the metadata of $PACKAGE"
    dpkg-deb -c "$PACKAGE" >/dev/null 2>&1 || fail "dpkg-deb cannot list the contents of $PACKAGE"
    dpkg-deb -f "$PACKAGE" Package | grep -q simpleide || fail "$PACKAGE does not declare the simpleide package name."
    dpkg-deb -c "$PACKAGE" | grep -E '/runtime/bin/java$' >/dev/null \
        || fail "$PACKAGE does not bundle the Java launcher (jpackage stripped native commands)."
    dpkg-deb -c "$PACKAGE" | grep -E '/runtime/release$' >/dev/null \
        || fail "$PACKAGE does not bundle the Java runtime release file."
    ok "$PACKAGE"
}

validate_rpm() {
    PACKAGE="$PKG_DIR/$ARTIFACT_BASE.rpm"
    [ -f "$PACKAGE" ] || fail "$PACKAGE was not produced."
    if command -v rpm >/dev/null 2>&1; then
        rpm -qp --info "$PACKAGE" >/dev/null 2>&1 || fail "rpm cannot read the metadata of $PACKAGE"
    elif command -v rpm2cpio >/dev/null 2>&1; then
        rpm2cpio "$PACKAGE" >/dev/null 2>&1 || fail "rpm2cpio cannot decode $PACKAGE"
    else
        fail "neither rpm nor rpm2cpio is available to validate $PACKAGE"
    fi
    ok "$PACKAGE"
}

dmg_fail() {
    hdiutil detach "$MOUNT_DIR" >/dev/null 2>&1 || true
    rm -rf "$MOUNT_DIR"
    fail "$1"
}

validate_macho_arch() {
    MACHO_PATH=$1
    MACHO_LABEL=$2
    case "$ARCH" in
        x64) EXPECTED_MACHO=x86_64 ;;
        arm64) EXPECTED_MACHO=arm64 ;;
        *) return 0 ;;
    esac
    DESCRIPTION=$(file -L -b "$MACHO_PATH" 2>/dev/null || true)
    case "$DESCRIPTION" in
        *"$EXPECTED_MACHO"*) ok "$MACHO_LABEL is valid $EXPECTED_MACHO" ;;
        *) dmg_fail "$MACHO_LABEL has the wrong architecture (expected $EXPECTED_MACHO): $DESCRIPTION" ;;
    esac
}

validate_dmg() {
    PACKAGE="$PKG_DIR/$ARTIFACT_BASE.dmg"
    [ -f "$PACKAGE" ] || fail "$PACKAGE was not produced."
    command -v hdiutil >/dev/null 2>&1 || fail "hdiutil is not available to validate $PACKAGE"
    hdiutil imageinfo "$PACKAGE" >/dev/null 2>&1 || fail "hdiutil cannot read $PACKAGE"

    MOUNT_DIR="$PKG_DIR/validate-dmg"
    rm -rf "$MOUNT_DIR"
    mkdir -p "$MOUNT_DIR"
    hdiutil attach -nobrowse -readonly -mountpoint "$MOUNT_DIR" "$PACKAGE" >/dev/null 2>&1 \
        || fail "hdiutil cannot mount $PACKAGE"

    APP_DIR="$MOUNT_DIR/SimpleIDE.app"
    PLIST="$APP_DIR/Contents/Info.plist"
    LAUNCHER="$APP_DIR/Contents/MacOS/SimpleIDE"
    RUNTIME_HOME="$APP_DIR/Contents/runtime/Contents/Home"
    LIBJVM="$RUNTIME_HOME/lib/server/libjvm.dylib"
    LIBJLI="$RUNTIME_HOME/lib/libjli.dylib"
    JAVA_LAUNCHER="$RUNTIME_HOME/bin/java"
    RUNTIME_RELEASE="$RUNTIME_HOME/release"

    [ -f "$PLIST" ] || dmg_fail "$PACKAGE has no SimpleIDE.app/Contents/Info.plist."
    [ -f "$LAUNCHER" ] || dmg_fail "$PACKAGE has no SimpleIDE.app/Contents/MacOS/SimpleIDE launcher."
    [ -x "$LAUNCHER" ] || dmg_fail "$PACKAGE launcher is not executable."
    [ -d "$RUNTIME_HOME" ] || dmg_fail "$PACKAGE has no bundled Java runtime at SimpleIDE.app/Contents/runtime/Contents/Home."
    [ -f "$LIBJVM" ] || dmg_fail "$PACKAGE has no bundled JVM library at $LIBJVM."
    [ -f "$LIBJLI" ] || dmg_fail "$PACKAGE has no bundled Java launcher library at $LIBJLI."
    [ -x "$JAVA_LAUNCHER" ] || dmg_fail "$PACKAGE has no bundled Java launcher at $JAVA_LAUNCHER (jpackage stripped native commands)."
    grep -q 'jdk.compiler' "$RUNTIME_RELEASE" \
        || dmg_fail "$PACKAGE bundled Java runtime does not provide the jdk.compiler module."

    validate_macho_arch "$LAUNCHER" "launcher"
    validate_macho_arch "$LIBJVM" "bundled JVM library"
    validate_macho_arch "$LIBJLI" "bundled Java launcher library"
    validate_macho_arch "$JAVA_LAUNCHER" "bundled Java launcher"

    DECLARED=$(plutil -extract LSMinimumSystemVersion raw "$PLIST" 2>/dev/null || true)
    hdiutil detach "$MOUNT_DIR" >/dev/null 2>&1 || true
    rm -rf "$MOUNT_DIR"

    [ -n "$DECLARED" ] || fail "$PACKAGE does not declare LSMinimumSystemVersion in SimpleIDE.app/Contents/Info.plist."
    EXPECTED=$(expected_macos_min_version || true)
    if [ -n "$EXPECTED" ]; then
        versions_equal "$DECLARED" "$EXPECTED" \
            || fail "$PACKAGE declares macOS $DECLARED, but JDK 17/21/25/27 packaging expects macOS $EXPECTED."
        ok "$PACKAGE (macOS $DECLARED+)"
    else
        ok "$PACKAGE (macOS $DECLARED+)"
    fi
}

case "$OS" in
    linux)
        validate_zip
        validate_deb
        validate_rpm
        ;;
    macos)
        validate_dmg
        ;;
    *)
        echo "error: unsupported validation OS: $OS (use scripts/validate-package.ps1 on Windows)" >&2
        exit 1
        ;;
esac

echo "All packages for $OS-$ARCH are valid."
