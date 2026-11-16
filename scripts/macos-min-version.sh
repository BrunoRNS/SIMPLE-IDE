#!/usr/bin/env sh
#
# Helpers shared by scripts/package.sh and scripts/validate-package.sh to map
# the JDK used for packaging to the minimum macOS version declared in the
# generated .app bundle (LSMinimumSystemVersion).
#
# Simple IDE supports macOS Big Sur (11.0) when it is packaged with JDK 17.
# Newer JDKs only run on newer macOS releases, so the value follows the JDK.
# This file is sourced; it does not run anything by itself.

# Prints java.specification.version (for example "17") for the given java
# executable, or for "java" on PATH when no argument is given.
simpleide_jdk_major() {
    JAVA_BIN=${1:-java}
    "$JAVA_BIN" -XshowSettings:properties -version 2>&1 \
        | sed -n 's/^ *java\.specification\.version *= *//p' \
        | head -n 1
}

# Prints the minimum macOS version for a JDK major version. Returns non-zero
# when the JDK is not in the known table.
simpleide_macos_min_version() {
    case "${1:-}" in
        17) echo "11.0" ;;
        21) echo "12.0" ;;
        25) echo "14.0" ;;
        27) echo "15.0" ;;
        *) return 1 ;;
    esac
}
