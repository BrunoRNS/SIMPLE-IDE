#!/usr/bin/env bash
#
# Prepares a Debian 10 (buster) container and runs one or more CI tasks.
#
# Debian 10 is the oldest distribution whose glibc (2.28) satisfies every
# bundled dependency, including the 32-bit pty4j native library and the newer
# JDKs. Alpine is intentionally not supported: its musl libc cannot run the
# glibc-linked JDK and native terminal binaries.
#
# Usage (inside the container, from the repository root):
#
#   bash scripts/ci/linux-debian.sh [test] [pkg] [verify]
#
#   test    Run the full Maven test suite.
#   pkg     Build and validate every Linux package for this architecture.
#   verify  Print the installed toolchain versions and exit.
#
# Environment:
#
#   SIMPLEIDE_CI_JAVA   JDK feature release to install (default: 17).
#   SIMPLEIDE_CI_ARCH   Expected package architecture: x86, x64 or arm64.
#                       Derived from dpkg when omitted.
#
# The container runs as root; the repository is expected to be bind-mounted at
# the current working directory.

set -euo pipefail

TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
	TASKS=(test)
fi

JAVA_VERSION=${SIMPLEIDE_CI_JAVA:-17}
GO_VERSION=1.27.4

case "$(dpkg --print-architecture)" in
	amd64)
		ARCH=x64
		GO_ARCH=amd64
		NODE_VERSION=v20.11.1
		NODE_ARCHIVE=node-v20.11.1-linux-x64
		AZUL_ARCH=x64
		EXPECTED_GCC=x86_64-linux-gnu
		EXPECTED_GOARCH=amd64
		EXPECTED_NODE_ARCH=x64
		EXPECTED_RUST_HOST=x86_64-unknown-linux-gnu
		EXPECTED_BITS=64
		;;
	arm64)
		ARCH=arm64
		GO_ARCH=arm64
		NODE_VERSION=v20.11.1
		NODE_ARCHIVE=node-v20.11.1-linux-arm64
		AZUL_ARCH=aarch64
		EXPECTED_GCC=aarch64-linux-gnu
		EXPECTED_GOARCH=arm64
		EXPECTED_NODE_ARCH=arm64
		EXPECTED_RUST_HOST=aarch64-unknown-linux-gnu
		EXPECTED_BITS=64
		;;
	i386)
		ARCH=x86
		GO_ARCH=386
		# Node.js stopped publishing 32-bit Linux builds after the 8.x line.
		NODE_VERSION=v8.17.0
		NODE_ARCHIVE=node-v8.17.0-linux-x86
		AZUL_ARCH=i686
		EXPECTED_GCC=i686-linux-gnu
		EXPECTED_GOARCH=386
		EXPECTED_NODE_ARCH=ia32
		EXPECTED_RUST_HOST=i686-unknown-linux-gnu
		EXPECTED_BITS=32
		;;
	*)
		echo "error: unsupported Debian architecture: $(dpkg --print-architecture)" >&2
		exit 1
		;;
esac

if [ -n "${SIMPLEIDE_CI_ARCH:-}" ] && [ "$SIMPLEIDE_CI_ARCH" != "$ARCH" ]; then
	echo "error: requested $SIMPLEIDE_CI_ARCH but the container is $ARCH" >&2
	exit 1
fi

echo "=== Debian ==="
cat /etc/debian_version
echo "architecture: $ARCH ($(dpkg --print-architecture))"

# ------------------------------------------------------------------
# Debian archive
# ------------------------------------------------------------------

# Debian 10 is archived; the regular mirrors no longer serve buster.
cat >/etc/apt/sources.list <<'EOF'
deb http://archive.debian.org/debian buster main
deb http://archive.debian.org/debian buster-updates main
deb http://archive.debian.org/debian-security buster/updates main
EOF

rm -f /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources 2>/dev/null || true

cat >/etc/apt/apt.conf.d/99simpleide <<'EOF'
Acquire::Check-Valid-Until "false";
Acquire::Retries "5";
EOF

echo "=== Installing Debian packages ==="

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
	bash \
	build-essential \
	ca-certificates \
	curl \
	dpkg \
	fakeroot \
	file \
	git \
	libstdc++6 \
	make \
	mono-devel \
	python3 \
	rpm \
	rustc \
	unzip \
	xz-utils \
	zip

# ------------------------------------------------------------------
# Java
# ------------------------------------------------------------------

echo "=== Installing Zulu JDK $JAVA_VERSION for $AZUL_ARCH ==="

METADATA_URL="https://api.azul.com/metadata/v1/zulu/packages/?java_version=${JAVA_VERSION}&os=linux&arch=${AZUL_ARCH}&archive_type=tar.gz&java_package_type=jdk&latest=true&release_status=ga&availability_types=CA"

JDK_URL=$(curl -fsSL "$METADATA_URL" | python3 -c '
import json
import sys

arch = sys.argv[1]
for item in json.load(sys.stdin):
    name = item.get("name", "")
    if "-ca-jdk" not in name or "musl" in name:
        continue
    if name.endswith("linux_%s.tar.gz" % arch):
        print(item["download_url"])
        break
' "$AZUL_ARCH")

if [ -z "$JDK_URL" ]; then
	echo "error: no Zulu JDK $JAVA_VERSION is available for $AZUL_ARCH" >&2
	exit 1
fi

echo "JDK: $JDK_URL"

rm -rf /opt/jdk
mkdir -p /opt/jdk
curl \
	--retry 5 \
	--retry-connrefused \
	--fail \
	--location \
	--silent \
	--show-error \
	"$JDK_URL" \
	-o /tmp/jdk.tar.gz
tar -xzf /tmp/jdk.tar.gz -C /opt/jdk --strip-components=1
rm -f /tmp/jdk.tar.gz

export JAVA_HOME=/opt/jdk
export PATH="$JAVA_HOME/bin:$PATH"

cat >/etc/profile.d/simpleide-jdk.sh <<'EOF'
export JAVA_HOME=/opt/jdk
export PATH="$JAVA_HOME/bin:$PATH"
EOF

# ------------------------------------------------------------------
# Go
# ------------------------------------------------------------------

echo "=== Installing Go $GO_VERSION for linux/$GO_ARCH ==="

rm -rf /opt/go
curl \
	--retry 5 \
	--retry-connrefused \
	--fail \
	--location \
	--silent \
	--show-error \
	"https://go.dev/dl/go${GO_VERSION}.linux-${GO_ARCH}.tar.gz" \
	-o /tmp/go.tar.gz
tar -xzf /tmp/go.tar.gz -C /opt
rm -f /tmp/go.tar.gz

ln -sf /opt/go/bin/go /usr/local/bin/go
export PATH="/opt/go/bin:$PATH"

cat >/etc/profile.d/simpleide-go.sh <<'EOF'
export PATH="/opt/go/bin:$PATH"
EOF

# ------------------------------------------------------------------
# Node.js
# ------------------------------------------------------------------

echo "=== Installing $NODE_ARCHIVE ==="

curl \
	--retry 5 \
	--retry-connrefused \
	--fail \
	--location \
	--silent \
	--show-error \
	"https://nodejs.org/dist/${NODE_VERSION}/${NODE_ARCHIVE}.tar.xz" \
	-o /tmp/node.tar.xz
tar -xJf /tmp/node.tar.xz -C /opt
rm -f /tmp/node.tar.xz

ln -sf "/opt/${NODE_ARCHIVE}/bin/node" /usr/local/bin/node
ln -sf "/opt/${NODE_ARCHIVE}/bin/npm" /usr/local/bin/npm

cat >/etc/profile.d/simpleide-node.sh <<'EOF'
export PATH="/usr/local/bin:$PATH"
EOF

# ------------------------------------------------------------------
# Verification
# ------------------------------------------------------------------

echo "=== Java ==="
java -version
java -XshowSettings:properties -version 2>&1 |
	grep -q "sun.arch.data.model = ${EXPECTED_BITS}"

echo "=== GCC / G++ ==="
command -v gcc
command -v g++
gcc -dumpmachine | grep -q "^${EXPECTED_GCC}$"
g++ -dumpmachine | grep -q "^${EXPECTED_GCC}$"
gcc --version | head -n 1
g++ --version | head -n 1

echo "=== GNU Make ==="
command -v make
make --version | head -n 1
make --version | grep -q "GNU Make"

echo "=== Mono ==="
command -v mcs
command -v mono
mono --version | head -n 1
mcs --version

echo "=== Go ==="
command -v go
test "$(go env GOOS)" = linux
test "$(go env GOARCH)" = "$EXPECTED_GOARCH"
go version

echo "=== Rust ==="
command -v rustc
rustc -vV | grep -q "^host: ${EXPECTED_RUST_HOST}$"
rustc --version

echo "=== Node.js ==="
command -v node
test "$(node -p 'process.arch')" = "$EXPECTED_NODE_ARCH"
node --version

echo "=== Python ==="
command -v python3
test "$(python3 -c 'import struct; print(struct.calcsize("P") * 8)')" = "$EXPECTED_BITS"
python3 --version

echo "=== Package tools ==="
command -v fakeroot
command -v rpmbuild
command -v dpkg-deb
fakeroot --version
rpmbuild --version
dpkg-deb --version | head -n 1

echo "=== Toolchain ready ==="

# ------------------------------------------------------------------
# Tasks
# ------------------------------------------------------------------

for TASK in "${TASKS[@]}"; do
	case "$TASK" in
		test)
			echo "=== Running tests ==="
			make test
			;;
		pkg)
			echo "=== Building and validating Linux packages ==="
			SIMPLEIDE_PKG_OS=linux SIMPLEIDE_PKG_ARCH="$ARCH" make pkg-verify
			;;
		verify)
			echo "=== Verification only ==="
			;;
		*)
			echo "error: unknown task: $TASK" >&2
			echo "usage: $0 [test] [pkg] [verify]" >&2
			exit 1
			;;
	esac
done
