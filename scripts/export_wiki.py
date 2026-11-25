#!/usr/bin/env python3
"""Flatten the source documentation into GitHub Wiki page files."""

from pathlib import Path
import urllib.parse
import re

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
WIKI = DOCS / "wiki"
REPO_WIKI = "https://github.com/BrunoRNS/SIMPLE-IDE/wiki/"

SEGMENTS = {
    "api": "API",
    "ui": "UI",
    "util": "Utilities",
    "third-party": "Third-Party",
    "development": "Development",
    "reference": "Reference",
    "build": "Build",
    "screenshots": "Screenshots",
    "compiler": "Compiler",
    "file-tree": "File-Tree",
    "getting-started": "Getting-Started",
    "isolated-modules": "Isolated-Modules",
    "rsyntax": "RSyntax",
    "threading": "Threading",
    "terminal": "Terminal",
    "languages": "Languages",
    "artifacts": "Artifacts",
    "examples": "Examples",
    "lsp": "LSP",
    "popups": "Popups",
    "logging": "Logging",
    "menubar": "Menubar",
    "application": "Application",
    "utilities": "Utilities",
    "makefile": "Makefile",
    "testing": "Testing",
    "contributing": "Contributing",
    "branching": "Branching",
    "code-style": "Code-Style",
    "code_style": "Code-Style",
    "releases": "Releases",
    "linux": "Linux",
    "windows": "Windows",
    "macos": "macOS",
    "install": "Install",
    "javafx-media": "JavaFX-Media",
    "threadmaster": "ThreadMaster",
    "runtime": "Runtime",
    "busybox": "BusyBox",
    "maven": "Maven",
    "licenses": "Licenses",
}


def page_name(path: Path) -> str:
    relative = path.relative_to(DOCS).with_suffix("")
    parts = list(relative.parts)
    if parts == ["api", "util", "utilities"]:
        return "API-Utilities"
    if parts[-1].lower() == "readme":
        parts.pop()
        if not parts:
            return "Home"
    return "-".join(SEGMENTS.get(part.lower(), part[:1].upper() + part[1:]) for part in parts)


def wiki_url(path: Path, fragment: str = "") -> str:
    return REPO_WIKI + urllib.parse.quote(page_name(path), safe="-_") + fragment


def resolve_target(source: Path, target: str) -> tuple[Path | None, str]:
    parsed = urllib.parse.urlsplit(target)
    if parsed.scheme or parsed.netloc or not parsed.path.lower().endswith(".md"):
        return None, ""
    target_path = (source.parent / urllib.parse.unquote(parsed.path)).resolve()
    try:
        target_path.relative_to(DOCS.resolve())
    except ValueError:
        return None, ""
    if target_path.is_file():
        return target_path, parsed.fragment
    return None, ""


sources = sorted(path for path in DOCS.rglob("*.md") if WIKI not in path.parents)
page_names = [page_name(path) for path in sources]
if len(page_names) != len(set(page_names)):
    raise SystemExit("Wiki page name collision detected")

link_pattern = re.compile(r"(?<!!)\[([^\]]+)\]\(([^)]+)\)")
for source in sources:
    original = source.read_text(encoding="utf-8")

    def replace_link(match: re.Match[str]) -> str:
        label, destination = match.groups()
        destination = destination.strip()
        if destination.startswith("<") and destination.endswith(">"): 
            destination = destination[1:-1]
        target, fragment = resolve_target(source, destination)
        if target is None:
            return match.group(0)
        return f"[{label}]({wiki_url(target, ('#' + fragment) if fragment else '')})"

    updated = link_pattern.sub(replace_link, original)
    if updated != original:
        source.write_text(updated, encoding="utf-8")

WIKI.mkdir(parents=True, exist_ok=True)
for old_page in WIKI.glob("*.md"):
    old_page.unlink()
for source in sources:
    content = source.read_text(encoding="utf-8")
    if source.parent.name == "screenshots":
        content = re.sub(
            r"!\[([^\]]+)\]\((?!https?://)([^)#]+\.(?:png|jpe?g|gif|svg))\)",
            lambda match: f"![{match.group(1)}](https://raw.githubusercontent.com/BrunoRNS/SIMPLE-IDE/main/docs/screenshots/{urllib.parse.quote(Path(match.group(2)).name)})",
            content,
            flags=re.IGNORECASE,
        )
    (WIKI / f"{page_name(source)}.md").write_text(content, encoding="utf-8")

sidebar = """## SIMPLE-IDE Wiki\n\n- [Home](Home)\n- [Screenshots](Screenshots)\n\n### API\n- [API overview](API)\n- [Getting started](API-Getting-Started)\n- [Artifacts and imports](API-Artifacts)\n- [Standalone RSyntaxTextArea](API-RSyntax)\n- [Isolated modules](API-Isolated-Modules)\n- [Embedded editor](API-Editor)\n- [Threading](API-Threading)\n- [Languages](API-Languages)\n- [LSP](API-LSP)\n- [Terminal](API-Terminal)\n- [UI helpers](API-UI)\n- [Application frame](API-UI-Application)\n- [Menu bar and themes](API-UI-Menubar)\n- [File tree](API-UI-File-Tree)\n- [Popups](API-UI-Popups)\n- [Logging](API-UI-Logging)\n- [Utilities](API-Utilities)\n- [Compiler](API-Compiler)\n- [Examples](API-Examples)\n\n### Reference\n- [Languages](Reference-Languages)\n- [LSP](Reference-LSP)\n- [Thread management](Reference-ThreadMaster)\n- [JavaFX media variants](Reference-JavaFX-Media)\n- [Script installation](Reference-Install)\n- [Linux](Reference-Linux)\n- [Windows](Reference-Windows)\n- [macOS](Reference-macOS)\n\n### Development and builds\n- [Contributing](Development-Contributing)\n- [Branching](Development-Branching)\n- [Code style](Development-Code-Style)\n- [Testing](Development-Testing)\n- [Makefile](Build-Makefile)\n- [Releases](Build-Releases)\n\n### Third-party\n- [Overview](Third-Party)\n- [Maven dependencies](Third-Party-Maven)\n- [BusyBox](Third-Party-BusyBox)\n- [Runtime downloads](Third-Party-Runtime)\n- [Licenses](Third-Party-Licenses)\n"""
(WIKI / "_Sidebar.md").write_text(sidebar, encoding="utf-8")
(WIKI / "_Footer.md").write_text("[SIMPLE-IDE source repository](https://github.com/BrunoRNS/SIMPLE-IDE) · [Report an issue](https://github.com/BrunoRNS/SIMPLE-IDE/issues)\n", encoding="utf-8")
