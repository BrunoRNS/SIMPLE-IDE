#!/usr/bin/env bash
#
# Runs scripts/ci/linux-debian.sh inside a Debian 10 container.
#
# The linux/386 container needs --security-opt seccomp=unconfined: newer
# GitHub runner images ship Docker versions whose default seccomp profile
# denies socketcall(2). Debian's i386 glibc still creates sockets through
# socketcall, so without this option the 32-bit container has no network and
# apt reports a bogus "Temporary failure resolving archive.debian.org".
# See actions/runner-images#14790.
#
# Usage:
#
#   bash scripts/ci/run-linux-debian.sh <docker-platform> [task ...]
#
# Environment variables forwarded to the container:
#
#   SIMPLEIDE_EXPECTED_PLATFORM
#   SIMPLEIDE_CI_JAVA
#   SIMPLEIDE_CI_ARCH
#   SIMPLEIDE_MEDIA_VARIANT
#
# The container image defaults to debian:10. Set SIMPLEIDE_DEBIAN_IMAGE to run
# the same tasks on a newer glibc. Linux arm64 uses debian:10 for the nojavafx
# baseline and debian:12 for forced javafx22/javafx23 builds, whose OpenJFX
# natives need glibc 2.34.

set -euo pipefail

if [ "$#" -lt 1 ]; then
	echo "usage: $0 <docker-platform> [task ...]" >&2
	exit 2
fi

PLATFORM=$1
shift

TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
	TASKS=(test)
fi

# The i386 glibc uses the legacy socketcall syscall, which Docker's default
# seccomp profile blocks on affected runner images.
SECURITY_ARGS=()
if [ "$PLATFORM" = "linux/386" ]; then
	SECURITY_ARGS=(--security-opt seccomp=unconfined)
fi

IMAGE=${SIMPLEIDE_DEBIAN_IMAGE:-debian:10}
mkdir -p "$HOME/.m2"

docker run --rm \
	--platform "$PLATFORM" \
	--network host \
	"${SECURITY_ARGS[@]}" \
	--shm-size=1g \
	-e SIMPLEIDE_EXPECTED_PLATFORM \
	-e SIMPLEIDE_CI_JAVA \
	-e SIMPLEIDE_CI_ARCH \
	-e SIMPLEIDE_MEDIA_VARIANT \
	-v "$PWD:/workspace" \
	-v "$HOME/.m2:/root/.m2" \
	-w /workspace \
	"$IMAGE" \
	bash scripts/ci/linux-debian.sh "${TASKS[@]}"
