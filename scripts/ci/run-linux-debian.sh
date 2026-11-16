#!/usr/bin/env bash
#
# Runs scripts/ci/linux-debian.sh inside a Debian 10 container.
#
# GitHub-hosted runners use a local stub resolver (systemd-resolved on
# 127.0.0.53) that Docker cannot reach from inside the container. Docker then
# falls back to public resolvers, which the CI network may block, so apt fails
# to resolve archive.debian.org. Detect the runner's real upstream nameservers
# and pass them to the container explicitly.
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

# Prints the runner's usable IPv4 upstream DNS servers, most specific first.
host_nameservers() {
	{
		if command -v resolvectl >/dev/null 2>&1; then
			resolvectl dns 2>/dev/null | awk '{ for (i = 2; i <= NF; i++) print $i }'
		fi
		if [ -r /run/systemd/resolve/resolv.conf ]; then
			awk '/^nameserver[[:space:]]/ { print $2 }' /run/systemd/resolve/resolv.conf
		fi
		if [ -r /etc/resolv.conf ]; then
			awk '/^nameserver[[:space:]]/ { print $2 }' /etc/resolv.conf
		fi
	} | grep -E '^[0-9]+(\.[0-9]+){3}$' | grep -vE '^127\.' | awk '!seen[$0]++'
}

DNS_ARGS=()
while IFS= read -r nameserver; do
	if [ -n "$nameserver" ]; then
		DNS_ARGS+=(--dns "$nameserver")
	fi
done < <(host_nameservers)

NETWORK_ARGS=(--hostname debian10 --shm-size=1g)
if [ ${#DNS_ARGS[@]} -gt 0 ]; then
	NETWORK_ARGS+=("${DNS_ARGS[@]}")
	echo "Container DNS: ${DNS_ARGS[*]}"
else
	echo "warning: no upstream nameservers found; using host networking" >&2
	NETWORK_ARGS=(--network host --shm-size=1g)
fi

mkdir -p "$HOME/.m2"

docker run --rm \
	--platform "$PLATFORM" \
	"${NETWORK_ARGS[@]}" \
	-e SIMPLEIDE_EXPECTED_PLATFORM \
	-e SIMPLEIDE_CI_JAVA \
	-e SIMPLEIDE_CI_ARCH \
	-v "$PWD:/workspace" \
	-v "$HOME/.m2:/root/.m2" \
	-w /workspace \
	debian:10 \
	bash scripts/ci/linux-debian.sh "${TASKS[@]}"
