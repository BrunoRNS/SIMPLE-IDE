#!/usr/bin/env python3
#
# Consumer contract tests for the optional JavaFX media variants.
#
# Installs the reactor into the local Maven repository (unless --skip-install is
# passed), creates a standalone consumer POM that depends on io:simpleide, and
# resolves it four times:
#
#   auto          no simpleide.media.variant property
#   nojavafx      -Dsimpleide.media.variant=nojavafx
#   javafx22      -Dsimpleide.media.variant=javafx22
#   javafx23      -Dsimpleide.media.variant=javafx23
#
# The expected result is derived from the consumer JDK and OS/architecture:
#
#   automatic platform + JDK 21+        auto -> JavaFX 23
#   automatic platform + JDK 17-20      auto -> JavaFX 22
#   forced platform/Linux arm64         javafx22/javafx23 -> requested version
#   unsupported platform/forced nojavafx -> no OpenJFX dependencies
#
# Linux arm64 has no automatic JavaFX profile (the glibc 2.28 baseline), but it
# does have forced javafx22/javafx23 profiles for glibc 2.34+ consumers.
#
# This catches profile-activation regressions that unit tests cannot see because
# they do not exercise the published (dependency) POM.
#
# Usage:
#
#   python3 scripts/ci/test-consumer-profiles.py [--skip-install] [--verbose]
#
# The script is also available as `make test-consumer` and runs in CI on every
# supported platform. It has no third-party dependencies.

from __future__ import annotations

import argparse
import os
import platform
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PROPERTIES = REPO / "simpleide" / "src" / "main" / "resources" / "simpleide.properties"
OPENJFX_ARTIFACTS = ("javafx-controls", "javafx-media", "javafx-swing")
OPENJFX_PATTERN = re.compile(
    r"org\.openjfx:(javafx-(?:controls|media|swing)):jar:(?:[^:]+:)?([0-9][^:]*):"
)
VARIANT_PROPERTY = "simpleide.media.variant"


def run(command: list[str], cwd: Path, verbose: bool = False) -> subprocess.CompletedProcess[str]:
    """Runs a command and returns its completed process, failing with context."""
    if verbose:
        print("+ " + " ".join(str(part) for part in command))
    result = subprocess.run(
        command,
        cwd=cwd,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if verbose or result.returncode != 0:
        print(result.stdout)
    if result.returncode != 0:
        raise SystemExit(f"command failed with exit code {result.returncode}: {' '.join(command)}")
    return result


def read_version() -> str:
    """Reads the application version from simpleide.properties."""
    for line in PROPERTIES.read_text(encoding="utf-8").splitlines():
        if line.startswith("version="):
            version = line.split("=", 1)[1].strip()
            if version:
                return version
    raise SystemExit(f"unable to read the version from {PROPERTIES}")


def java_command() -> str:
    """Returns the java executable that matches the Maven JAVA_HOME, if set."""
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if candidate.is_file():
            return str(candidate)
    return "java"


def java_properties() -> dict[str, str]:
    """Reads the JDK system properties used by Maven profile activation."""
    result = run([java_command(), "-XshowSettings:properties", "-version"], REPO)
    properties: dict[str, str] = {}
    pattern = re.compile(r"^\s*([A-Za-z0-9_.]+)\s*=\s*(.*)$")
    for line in result.stdout.splitlines():
        match = pattern.match(line)
        if match:
            properties[match.group(1)] = match.group(2).strip()
    return properties


def jdk_major(properties: dict[str, str]) -> int:
    """Returns the JDK feature release from java.specification.version."""
    value = properties.get("java.specification.version", "")
    if value.startswith("1."):
        value = value[2:]
    try:
        return int(value.split(".")[0])
    except ValueError:
        raise SystemExit(f"unable to parse the JDK version from {value!r}")


def platform_key(properties: dict[str, str]) -> tuple[str, str]:
    """Normalizes os.name/os.arch to the names used in the variant matrix."""
    os_name = properties.get("os.name", platform.system()).lower()
    arch = properties.get("os.arch", platform.machine()).lower()
    if os_name.startswith("linux"):
        operating_system = "linux"
    elif os_name.startswith("mac") or os_name.startswith("darwin"):
        operating_system = "macos"
    elif os_name.startswith("windows"):
        operating_system = "windows"
    else:
        operating_system = os_name

    if arch in ("amd64", "x86_64"):
        normalized_arch = "x64"
    elif arch in ("aarch64", "arm64"):
        normalized_arch = "arm64"
    elif arch in ("x86", "i386", "i486", "i586", "i686"):
        normalized_arch = "x86"
    else:
        normalized_arch = arch
    return operating_system, normalized_arch


def is_auto_supported(operating_system: str, arch: str) -> bool:
    """Mirrors the automatic platform profiles in simpleide/pom.xml."""
    if operating_system == "linux":
        # Linux arm64 stays nojavafx automatically to preserve the glibc 2.28
        # baseline; Linux x86 has no OpenJFX natives.
        return arch == "x64"
    if operating_system == "windows":
        return arch == "x64"
    if operating_system == "macos":
        return arch in ("x64", "arm64")
    return False


def is_forced_supported(operating_system: str, arch: str) -> bool:
    """Mirrors the forced javafx22/javafx23 profiles in simpleide/pom.xml."""
    if is_auto_supported(operating_system, arch):
        return True
    # Forced JavaFX is available on Linux arm64 for glibc 2.34+ consumers.
    return operating_system == "linux" and arch == "arm64"


def expected_version(case: str, supported: bool, major: int, versions: dict[str, str]) -> str | None:
    """Returns the expected JavaFX version for a consumer case, or None."""
    if case == "nojavafx":
        return None
    if not supported:
        return None
    if case == "javafx22":
        return versions["javafx22"]
    if case == "javafx23":
        return versions["javafx23"]
    if major >= 21:
        return versions["javafx23"]
    return versions["javafx22"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-install", action="store_true", help="use the artifact already in ~/.m2")
    parser.add_argument("--verbose", action="store_true", help="print Maven command output")
    args = parser.parse_args()

    version = read_version()
    properties = java_properties()
    major = jdk_major(properties)
    operating_system, arch = platform_key(properties)
    auto_supported = is_auto_supported(operating_system, arch)
    forced_supported = is_forced_supported(operating_system, arch)
    versions = {"javafx22": "22.0.2", "javafx23": "23.0.2"}

    maven = REPO / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    if not maven.is_file():
        raise SystemExit(f"Maven wrapper not found: {maven}")

    print(f"Consumer variant tests: JDK {major}, {operating_system}-{arch}")
    print(f"Automatic JavaFX: {'yes' if auto_supported else 'no (nojavafx baseline)'}")
    print(f"Forced JavaFX: {'yes' if forced_supported else 'no'}")

    if not args.skip_install:
        run([str(maven), "-B", "-DskipTests", "install"], REPO, args.verbose)

    pom = f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>io.simpleide.tests</groupId>
    <artifactId>consumer-media-contract</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <dependencies>
        <dependency>
            <groupId>io</groupId>
            <artifactId>simpleide</artifactId>
            <version>{version}</version>
        </dependency>
    </dependencies>
</project>
"""

    failures: list[str] = []
    with tempfile.TemporaryDirectory(prefix="simpleide-consumer-") as directory:
        consumer = Path(directory)
        (consumer / "pom.xml").write_text(pom, encoding="utf-8")

        for case in ("auto", "nojavafx", "javafx22", "javafx23"):
            output = f"tree-{case}.txt"
            command = [
                str(maven),
                "-B",
                "-f",
                str(consumer / "pom.xml"),
                "dependency:tree",
                f"-DoutputFile={output}",
            ]
            if case != "auto":
                command.append(f"-D{VARIANT_PROPERTY}={case}")
            run(command, consumer, args.verbose)

            tree = (consumer / output).read_text(encoding="utf-8")
            resolved: dict[str, set[str]] = {}
            for match in OPENJFX_PATTERN.finditer(tree):
                resolved.setdefault(match.group(1), set()).add(match.group(2))

            case_supported = auto_supported if case == "auto" else forced_supported
            expected = expected_version(case, case_supported, major, versions)
            if expected is None:
                if resolved:
                    failures.append(f"{case}: expected no OpenJFX dependencies, found {resolved}")
                else:
                    print(f"  {case}: no OpenJFX dependencies (expected)")
                continue

            case_failures: list[str] = []
            for artifact in OPENJFX_ARTIFACTS:
                found = resolved.get(artifact)
                if found != {expected}:
                    case_failures.append(
                        f"{case}: expected org.openjfx:{artifact}:{expected}, found {sorted(found or [])}"
                    )
            extra = sorted(set(resolved) - set(OPENJFX_ARTIFACTS))
            if extra:
                case_failures.append(f"{case}: unexpected OpenJFX artifacts {extra}")
            failures.extend(case_failures)
            if not case_failures:
                print(f"  {case}: JavaFX {expected} (expected)")

    if failures:
        for failure in failures:
            print(f"FAILED: {failure}", file=sys.stderr)
        return 1
    print("Consumer media variant contract passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
