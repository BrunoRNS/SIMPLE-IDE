#!/usr/bin/env sh
#
# Validates the packages produced by scripts/package.sh for the current
# OS/architecture. Used by CI after `make pkg` so a broken executable never
# reaches a release unnoticed.
#
# Checks:
#   .zip  the portable app image extracts and its launcher is runnable
#   .deb  dpkg-deb can read the control metadata and file list
#   .rpm  rpm can read the package metadata
#   .dmg  hdiutil can read the image
#
# Exits non-zero when a package is missing or invalid.

set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PROPERTIES="$ROOT_DIR/simpleide/src/main/resources/simpleide.properties"
PKG_DIR="$ROOT_DIR/simpleide/target/pkg"

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

validate_zip() {
    PACKAGE="$PKG_DIR/$ARTIFACT_BASE.zip"
    [ -f "$PACKAGE" ] || fail "$PACKAGE was not produced."
    EXTRACT_DIR="$PKG_DIR/validate-zip"
    rm -rf "$EXTRACT_DIR"
    mkdir -p "$EXTRACT_DIR"
    unzip -q "$PACKAGE" -d "$EXTRACT_DIR" || fail "the app image zip is not a readable zip: $PACKAGE"
    if [ "$OS" = "macos" ]; then
        LAUNCHER="$EXTRACT_DIR/SimpleIDE.app/Contents/MacOS/SimpleIDE"
    else
        LAUNCHER="$EXTRACT_DIR/SimpleIDE/bin/SimpleIDE"
    fi
    [ -f "$LAUNCHER" ] || fail "the app image zip has no launcher at $LAUNCHER: $PACKAGE"
    [ -x "$LAUNCHER" ] || fail "the app image launcher is not executable: $LAUNCHER"
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

validate_dmg() {
    PACKAGE="$PKG_DIR/$ARTIFACT_BASE.dmg"
    [ -f "$PACKAGE" ] || fail "$PACKAGE was not produced."
    command -v hdiutil >/dev/null 2>&1 || fail "hdiutil is not available to validate $PACKAGE"
    hdiutil imageinfo "$PACKAGE" >/dev/null 2>&1 || fail "hdiutil cannot read $PACKAGE"
    ok "$PACKAGE"
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
