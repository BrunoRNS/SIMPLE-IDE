#!/usr/bin/env sh
#
# Changes the project version everywhere it is declared:
#
#   pom.xml                          parent project version
#   simpleide/pom.xml                module parent reference
#   simplebytecode-compiler/pom.xml  module parent reference
#   simpleide/src/main/resources/simpleide.properties  application version
#
# Usage: sh scripts/change-version.sh <new-version>
#
# The version is validated before anything is changed, the affected files are
# listed and the change must be confirmed with y (default is NO).

set -eu

if [ "$#" -ne 1 ] || [ -z "$1" ]; then
    echo "error: usage: make change-version VERSAO" >&2
    exit 1
fi

NEW_VERSION=$1
if ! printf '%s' "$NEW_VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(beta|alpha|rc[0-9]*)?$'; then
    echo "error: invalid version '$NEW_VERSION'." >&2
    echo "       Expected semantic version like 1.2.3, optionally followed by" >&2
    echo "       beta, alpha or rc<number>, for example 1.0.0beta." >&2
    exit 1
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PROPERTIES="$ROOT_DIR/simpleide/src/main/resources/simpleide.properties"
OLD_VERSION=$(sed -n 's/^version=//p' "$PROPERTIES" | tr -d '\r' | head -n 1)

if [ -z "$OLD_VERSION" ]; then
    echo "error: unable to read the current version from $PROPERTIES" >&2
    exit 1
fi

if [ "$OLD_VERSION" = "$NEW_VERSION" ]; then
    echo "The version is already $NEW_VERSION. Nothing to do."
    exit 0
fi

FILES="
pom.xml
simpleide/pom.xml
simplebytecode-compiler/pom.xml
simpleide/src/main/resources/simpleide.properties
"

echo "Changing the project version:"
echo "  from: $OLD_VERSION"
echo "  to:   $NEW_VERSION"
echo ""
echo "Files that will be modified:"
for FILE in $FILES; do
    if grep -q "$OLD_VERSION" "$ROOT_DIR/$FILE"; then
        echo "  $FILE"
    fi
done
echo ""

# shellcheck disable=SC3037
printf 'Apply this change? [y/N] '
read -r ANSWER
case "$ANSWER" in
    y|Y|yes|YES)
        ;;
    *)
        echo "Aborted. The version was not changed."
        exit 1
        ;;
esac

for FILE in $FILES; do
    PATH_="$ROOT_DIR/$FILE"
    TMP="$PATH_.version-tmp"
    awk -v OLD="$OLD_VERSION" -v NEW="$NEW_VERSION" '
        {
            gsub(OLD, NEW)
            print
        }
    ' "$PATH_" > "$TMP" || {
        rm -f "$TMP"
        echo "error: unable to update $FILE" >&2
        exit 1
    }
    mv "$TMP" "$PATH_"
    echo "updated: $FILE"
done

echo ""
echo "The version is now $NEW_VERSION."
echo "Next steps:"
echo "  - run 'make clean' to rebuild with the new version"
echo "  - tag 'v$NEW_VERSION' and push it to publish the release"
