#!/usr/bin/env bash
#
# Runs scripts/ci/linux-debian.sh inside a Debian 10 container.
#
# GitHub-hosted runners use systemd-resolved, whose stub resolver listens on
# 127.0.0.53 and is not reachable from inside a container with the default
# bridge network. Using the host network namespace lets the container reuse the
# runner resolver, so apt can reach archive.debian.org. This is simpler and more
# reliable than pinning public DNS servers, which CI networks may block.
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

mkdir -p "$HOME/.m2"

docker run --rm \
	--platform "$PLATFORM" \
	--network host \
	--shm-size=1g \
	-e SIMPLEIDE_EXPECTED_PLATFORM \
	-e SIMPLEIDE_CI_JAVA \
	-e SIMPLEIDE_CI_ARCH \
	-v "$PWD:/workspace" \
	-v "$HOME/.m2:/root/.m2" \
	-w /workspace \
	debian:10 \
	bash scripts/ci/linux-debian.sh "${TASKS[@]}"
