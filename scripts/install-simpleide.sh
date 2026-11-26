#!/usr/bin/env bash
#
# Downloads the best published Simple IDE package for this Linux/macOS system.
#
# This script only validates the host, finds the latest GitHub release and
# downloads one asset into the current directory (or SIMPLEIDE_OUTPUT_DIR).
# It never extracts, installs or executes the downloaded package.
#
# Selection order: javafx23 -> javafx22 -> nojavafx, when such a package was
# published for the detected OS/architecture. Linux arm64 JavaFX packages need
# glibc 2.34+ (Debian 12+); the nojavafx baseline needs glibc 2.28+.
# macOS javafx23 packages need macOS 12+; javafx22 needs macOS 11+.
#
# Usage:
#
#   bash scripts/install-simpleide.sh
#   SIMPLEIDE_OUTPUT_DIR="$HOME/Downloads" bash scripts/install-simpleide.sh
#   SIMPLEIDE_TAG=v1.1.0 bash scripts/install-simpleide.sh
#   SIMPLEIDE_FORCE=1 bash scripts/install-simpleide.sh
#
# Environment:
#
#   SIMPLEIDE_TAG         Use a specific release tag instead of the latest one.
#   SIMPLEIDE_OUTPUT_DIR  Destination directory (default: current directory).
#   SIMPLEIDE_FORCE       Set to 1 to overwrite an existing file.
#
# Requires: curl, and getconf or ldd on Linux. The script is documented in
# docs/reference/INSTALL.md.

set -euo pipefail

REPOSITORY="BrunoRNS/SIMPLE-IDE"
API_URL="https://api.github.com/repos/${REPOSITORY}/releases/latest"
RELEASES_URL="https://github.com/${REPOSITORY}/releases/download"

log() {
	printf '%s\n' "$*"
}

die() {
	echo "error: $*" >&2
	exit 1
}

usage() {
	cat <<'EOF'
Usage: install-simpleide.sh [options]

Downloads the newest Simple IDE package that fits this system. The file is
left in the output directory; it is not extracted, installed or executed.

Options:
  -h, --help    Show this help and exit.

Environment:
  SIMPLEIDE_TAG         Release tag to use (default: latest GitHub release).
  SIMPLEIDE_OUTPUT_DIR  Destination directory (default: current directory).
  SIMPLEIDE_FORCE       Set to 1 to overwrite an existing downloaded file.
EOF
}

case "${1:-}" in
	-h|--help)
		usage
		exit 0
		;;
	'') ;;
	*)
		die "unknown option '$1' (try --help)"
		;;
esac

command -v curl >/dev/null 2>&1 || die "curl is required to query GitHub and download the package"

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

detect_os() {
	case "$(uname -s)" in
		Linux*) echo linux ;;
		Darwin*) echo macos ;;
		*) die "unsupported operating system: $(uname -s)" ;;
	esac
}

detect_arch() {
	case "$(uname -m)" in
		x86_64|amd64) echo x64 ;;
		aarch64|arm64) echo arm64 ;;
		i386|i486|i586|i686|x86) echo x86 ;;
		*) die "unsupported architecture: $(uname -m)" ;;
	esac
}

glibc_version() {
	local output
	output=$(getconf GNU_LIBC_VERSION 2>/dev/null || true)
	if [ -n "$output" ]; then
		printf '%s\n' "$output" | awk 'NR == 1 { print $2 }'
		return 0
	fi

	output=$(ldd --version 2>&1 || true)
	case "$output" in
		*musl*) die "musl-based systems are not supported (the packages link against glibc)" ;;
	esac
	printf '%s\n' "$output" | awk 'NR == 1 {
		for (i = NF; i >= 1; i--) {
			if ($i ~ /^[0-9]+(\.[0-9]+)+$/) { print $i; exit }
		}
	}'
}

latest_tag() {
	local json
	json=$(curl -fsSL --retry 3 --connect-timeout 10 \
		-H 'User-Agent: install-simpleide.sh' "$API_URL") \
		|| die "unable to read the latest release from $API_URL"
	printf '%s\n' "$json" | tr ',' '\n' |
		sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' |
		head -n 1
}

asset_exists() {
	curl -fsSIL --retry 2 --connect-timeout 10 -o /dev/null "$1" 2>/dev/null
}

OS=$(detect_os)
ARCH=$(detect_arch)
OUTPUT_DIR=${SIMPLEIDE_OUTPUT_DIR:-.}
FORCE=${SIMPLEIDE_FORCE:-0}

[ -d "$OUTPUT_DIR" ] || die "output directory does not exist: $OUTPUT_DIR"

VARIANTS=()
EXTENSIONS=()

if [ "$OS" = "linux" ]; then
	GLIBC=$(glibc_version)
	[ -n "$GLIBC" ] || die "unable to determine the glibc version; only glibc-based Linux is supported"
	version_ge "$GLIBC" 2.28 || die "Simple IDE requires glibc 2.28+; found $GLIBC"

	case "$ARCH" in
		x64)
			VARIANTS=(javafx23 javafx22 nojavafx)
			EXTENSIONS=(tar.gz zip)
			;;
		arm64)
			# Automatic arm64 packages stay on the Debian 10 baseline. The
			# JavaFX arm64 natives require glibc 2.34 (Debian 12+).
			if version_ge "$GLIBC" 2.34; then
				VARIANTS=(javafx23 javafx22 nojavafx)
			else
				VARIANTS=(nojavafx)
			fi
			EXTENSIONS=(tar.gz zip)
			;;
		x86)
			# No usable OpenJFX natives exist for 32-bit Linux.
			VARIANTS=(nojavafx)
			EXTENSIONS=(tar.gz zip)
			;;
	esac
else
	MACOS_VERSION=$(sw_vers -productVersion 2>/dev/null || true)
	[ -n "$MACOS_VERSION" ] || die "unable to determine the macOS version with sw_vers"
	version_ge "$MACOS_VERSION" 11.0 \
		|| die "Simple IDE requires macOS 11 (Big Sur) or newer; found $MACOS_VERSION"

	case "$ARCH" in
		x64|arm64)
			if version_ge "$MACOS_VERSION" 12.0; then
				VARIANTS=(javafx23 javafx22 nojavafx)
			else
				# The javafx23 .dmg is built with JDK 21 and declares macOS 12.
				VARIANTS=(javafx22 nojavafx)
			fi
			EXTENSIONS=(dmg)
			;;
		*)
			die "unsupported macOS architecture: $ARCH"
			;;
	esac
fi

TAG=${SIMPLEIDE_TAG:-$(latest_tag)}
case "$TAG" in
	v*) VERSION=${TAG#v} ;;
	*) die "unexpected release tag '$TAG' (expected a leading 'v')" ;;
esac
case "$VERSION" in
	''|*[!A-Za-z0-9._+-]*)
		die "unexpected version '$VERSION' in tag '$TAG'"
		;;
esac

log "Repository: $REPOSITORY"
log "Release: $TAG"
if [ "$OS" = "linux" ]; then
	log "System: $OS-$ARCH (glibc $GLIBC)"
else
	log "System: $OS-$ARCH (macOS $MACOS_VERSION)"
fi

ASSET=""
ASSET_URL=""
BASE="simpleide-v${VERSION}-${OS}-${ARCH}"

for VARIANT in "${VARIANTS[@]}"; do
	for EXTENSION in "${EXTENSIONS[@]}"; do
		NAME="${BASE}-${VARIANT}.${EXTENSION}"
		URL="${RELEASES_URL}/${TAG}/${NAME}"
		if asset_exists "$URL"; then
			ASSET=$NAME
			ASSET_URL=$URL
			break 2
		fi
		log "Not published: $NAME" >&2
	done
done

[ -n "$ASSET" ] || die "no compatible package was published in release $TAG (tried: ${VARIANTS[*]})"

DESTINATION="${OUTPUT_DIR}/${ASSET}"
if [ -e "$DESTINATION" ] && [ "$FORCE" != "1" ]; then
	die "$DESTINATION already exists; move it or set SIMPLEIDE_FORCE=1 to overwrite"
fi

TMP_FILE=$(mktemp "${OUTPUT_DIR}/.simpleide-download.XXXXXX")
cleanup() {
	rm -f "$TMP_FILE"
}
trap cleanup EXIT HUP INT TERM

log "Downloading: $ASSET_URL"
curl -fSL --retry 3 --retry-delay 1 --connect-timeout 10 \
	-o "$TMP_FILE" "$ASSET_URL" || die "failed to download $ASSET_URL"
[ -s "$TMP_FILE" ] || die "the downloaded file is empty: $ASSET"

mv -f "$TMP_FILE" "$DESTINATION"
trap - EXIT HUP INT TERM

log "Saved: $DESTINATION"
log "The archive was not extracted, installed or executed."
