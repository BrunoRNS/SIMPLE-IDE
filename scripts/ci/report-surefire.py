#!/usr/bin/env python3
"""Summarizes Surefire failures and emits GitHub Actions annotations.

The Windows, macOS and Linux workflows run the full Maven suite. When a test
fails, GitHub only shows "Process completed with exit code 1" unless the
workflow prints the failure details itself. This script walks every
``target/surefire-reports/TEST-*.xml`` file and prints a compact report. When
``GITHUB_ACTIONS`` is set it also emits ``::error`` workflow commands so the
failing class and message appear next to the job.

Usage::

    python3 scripts/ci/report-surefire.py [--root .] [--max-stack-lines 25]

The script always exits with status 0: it is a reporting aid, not a gate.
"""

from __future__ import annotations

import argparse
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MAX_MESSAGE_LENGTH = 400


def escape_command(value: str) -> str:
    """Escapes a value for a GitHub Actions workflow command."""
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def case_name(testcase: ET.Element) -> str:
    """Returns ``Class#method`` for a Surefire test case element."""
    classname = testcase.get("classname") or ""
    name = testcase.get("name") or ""
    return f"{classname}#{name}" if classname else name


def first_line(value: str) -> str:
    """Returns the first non-empty line of a possibly long message."""
    for line in value.splitlines():
        if line.strip():
            return line.strip()[:MAX_MESSAGE_LENGTH]
    return ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="repository root (default: .)")
    parser.add_argument(
        "--max-stack-lines",
        type=int,
        default=20,
        help="stack lines to print per failure (default: 20)",
    )
    args = parser.parse_args()

    root = Path(args.root)
    reports = sorted(root.glob("**/target/surefire-reports/TEST-*.xml"))
    if not reports:
        print("No Surefire XML reports found; nothing to report.")
        return 0

    total = 0
    passed = 0
    skipped = 0
    problems: list[tuple[Path, str, str, str, str]] = []

    for report in reports:
        try:
            suite = ET.parse(report).getroot()
        except (ET.ParseError, OSError) as error:
            print(f"warning: unable to read {report}: {error}", file=sys.stderr)
            continue
        for testcase in suite.iter("testcase"):
            total += 1
            if testcase.find("skipped") is not None:
                skipped += 1
                continue
            failure = testcase.find("failure")
            error_element = testcase.find("error")
            node = failure if failure is not None else error_element
            if node is None:
                passed += 1
                continue
            kind = "failure" if failure is not None else "error"
            message = (node.get("message") or node.get("type") or "").strip()
            stack = (node.text or "").strip()
            problems.append((report, case_name(testcase), kind, message, stack))

    print("Surefire summary")
    print(f"  reports: {len(reports)}")
    print(f"  tests:   {total}")
    print(f"  passed:  {passed}")
    print(f"  skipped: {skipped}")
    print(f"  failed:  {len(problems)}")

    if not problems:
        return 0

    github_actions = os.environ.get("GITHUB_ACTIONS") == "true"
    for report, name, kind, message, stack in problems:
        summary = first_line(message) or first_line(stack) or kind
        print()
        print(f"{kind.upper()}: {name}")
        if message:
            print(f"  message: {message}")
        for line in stack.splitlines()[: max(0, args.max_stack_lines)]:
            print(f"  {line}")
        if github_actions:
            title = escape_command(f"{kind}: {name}")
            body = escape_command(f"{summary} ({report})")
            print(f"::error file={report},line=1,title={title}::{body}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
