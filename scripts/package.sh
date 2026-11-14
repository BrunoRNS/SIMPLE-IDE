#!/usr/bin/env sh
#
# Builds the release packages for the current system with the naming
# convention:
#
#   simpleide-v<version>-<os>-<arch>.zip     static, portable app image
#   simpleide-v<version>-<os>-<arch>.deb     Linux installer
#   simpleide-v<version>-<os>-<arch>.rpm     Linux installer
#   simpleide-v<version>-<os>-<arch>.dmg     macOS installer
#
# Default behavior per OS:
#
#   linux:   zip + deb + rpm
#   macos:   dmg only
#   windows: zip + msi (see scripts/package.ps1)
#
# Set SIMPLEIDE_PKG_TYPE to a single type (app-image, deb, rpm or dmg) to
# build only that package instead of the OS default set.

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PROPERTIES="$ROOT_DIR/simpleide/src/main/resources/simpleide.properties"
FAT_JAR="$ROOT_DIR/simpleide/target/simpleide.jar"
PKG_DIR="$ROOT_DIR/simpleide/target/pkg"
INPUT_DIR="$ROOT_DIR/simpleide/target/jpackage-input"

if [ ! -f "$FAT_JAR" ]; then
    echo "error: $FAT_JAR was not found. Run 'make jar' first." >&2
    exit 1
fi

VERSION=$(sed -n 's/^version=//p' "$PROPERTIES" | tr -d '\r' | head -n 1)
if [ -z "$VERSION" ]; then
    echo "error: unable to read the application version from $PROPERTIES" >&2
    exit 1
fi

# jpackage requires a numeric application version; the artifact name keeps the
# full version (for example 1.0.0beta).
APP_VERSION=$(printf '%s' "$VERSION" | sed 's/[^0-9.].*$//; s/\.$//')
if [ -z "$APP_VERSION" ]; then
    APP_VERSION=1.0.0
fi

detect_os() {
    case "$(uname -s)" in
        Linux*) echo "linux" ;;
        Darwin*) echo "macos" ;;
        MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
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

case "$OS" in
    linux|macos|windows) ;;
    *)
        echo "error: unsupported jpackage OS: $OS" >&2
        exit 1
        ;;
esac

case "$ARCH" in
    x86|x64|arm64) ;;
    *)
        echo "error: unsupported jpackage architecture: $ARCH" >&2
        exit 1
        ;;
esac

PKG_TYPE=${SIMPLEIDE_PKG_TYPE:-}

if [ -n "$PKG_TYPE" ]; then
    case "$PKG_TYPE" in
        app-image|deb|rpm|dmg|msi) ;;
        *)
            echo "error: unsupported package type: $PKG_TYPE" >&2
            exit 1
            ;;
    esac
    PACKAGES=$PKG_TYPE
else
    case "$OS" in
        linux) PACKAGES="app-image deb rpm" ;;
        macos) PACKAGES="dmg" ;;
        windows) PACKAGES="app-image msi" ;;
    esac
fi

rm -rf "$PKG_DIR" "$INPUT_DIR"
mkdir -p "$PKG_DIR" "$INPUT_DIR"
cp "$FAT_JAR" "$INPUT_DIR/simpleide.jar"

jpackage_args="--name SimpleIDE --app-version $APP_VERSION --input $INPUT_DIR --main-jar simpleide.jar --main-class io.simpleide.App --vendor SimpleIDE --description SimpleIDE --copyright BSD-3-Clause --java-options -Dfile.encoding=UTF-8"

build_app_image() {
    IMAGE_DIR="$PKG_DIR/image"
    rm -rf "$IMAGE_DIR"
    mkdir -p "$IMAGE_DIR"
    # shellcheck disable=SC2086
    jpackage --type app-image $jpackage_args --dest "$IMAGE_DIR"

    if [ "$OS" = "macos" ]; then
        APP_IMAGE="$IMAGE_DIR/SimpleIDE.app"
        LAUNCHER="$APP_IMAGE/Contents/MacOS/SimpleIDE"
    else
        APP_IMAGE="$IMAGE_DIR/SimpleIDE"
        LAUNCHER="$APP_IMAGE/bin/SimpleIDE"
    fi
    if [ ! -e "$APP_IMAGE" ]; then
        echo "error: jpackage did not create the expected application image: $APP_IMAGE" >&2
        exit 1
    fi
    if [ ! -e "$LAUNCHER" ]; then
        echo "error: the application image has no launcher: $LAUNCHER" >&2
        exit 1
    fi
}

build_app_image_archive() {
    if [ ! -e "${LAUNCHER:-}" ]; then
        build_app_image
    fi
    ARCHIVE="$PKG_DIR/$ARTIFACT_BASE.zip"
    rm -f "$ARCHIVE"
    (
        cd "$IMAGE_DIR"
        zip -qry "$ARCHIVE" "$(basename "$APP_IMAGE")"
    )
    echo "Package: $ARCHIVE"
}

build_installer() {
    INSTALLER_DIR="$PKG_DIR/installer-$1"
    rm -rf "$INSTALLER_DIR"
    mkdir -p "$INSTALLER_DIR"
    # shellcheck disable=SC2086
    jpackage --type "$1" $jpackage_args --dest "$INSTALLER_DIR"

    INSTALLER=$(find "$INSTALLER_DIR" -maxdepth 1 -type f | head -n 1)
    if [ -z "$INSTALLER" ]; then
        echo "error: jpackage did not create a $1 installer." >&2
        exit 1
    fi
    EXTENSION=${INSTALLER##*.}
    ARTIFACT="$PKG_DIR/$ARTIFACT_BASE.$EXTENSION"
    cp "$INSTALLER" "$ARTIFACT"
    echo "Package: $ARTIFACT"
}

SKIPPED=""
for PKG in $PACKAGES; do
    case "$PKG" in
        app-image)
            if ! command -v zip >/dev/null 2>&1; then
                echo "warning: 'zip' is not installed; skipping the static app image package." >&2
                SKIPPED="$SKIPPED app-image(zip)"
                continue
            fi
            build_app_image
            build_app_image_archive
            ;;
        deb)
            MISSING=""
            command -v dpkg-deb >/dev/null 2>&1 || MISSING="$MISSING dpkg-deb"
            command -v fakeroot >/dev/null 2>&1 || MISSING="$MISSING fakeroot"
            if [ -n "$MISSING" ]; then
                echo "warning: missing .deb build dependencies:$MISSING; skipping the .deb package." >&2
                SKIPPED="$SKIPPED deb"
                continue
            fi
            build_installer deb
            ;;
        rpm)
            if ! command -v rpmbuild >/dev/null 2>&1; then
                echo "warning: 'rpmbuild' is not installed; skipping the .rpm package." >&2
                SKIPPED="$SKIPPED rpm"
                continue
            fi
            build_installer rpm
            ;;
        dmg)
            if [ "$(uname -s)" != "Darwin" ] || ! command -v hdiutil >/dev/null 2>&1; then
                echo "warning: hdiutil is not available; skipping the .dmg package." >&2
                SKIPPED="$SKIPPED dmg"
                continue
            fi
            build_installer dmg
            ;;
        msi)
            echo "warning: .msi packages are built by scripts/package.ps1 on Windows; skipping." >&2
            SKIPPED="$SKIPPED msi"
            continue
            ;;
    esac
done

if ! find "$PKG_DIR" -maxdepth 1 -type f -name 'simpleide-v*' | grep -q .; then
    echo "error: no package could be produced for $OS-$ARCH. Install the missing dependencies and retry. Skipped:$SKIPPED" >&2
    exit 1
fi
if [ -n "${SKIPPED:-}" ]; then
    echo "warning: some packages were skipped:$SKIPPED" >&2
fi
