"""Builds the Purandara website into site/_build (Python standard library only).

Pages live in site/pages as HTML fragments. The first lines of each are
front matter in an HTML comment:

    <!--
    title: Choose your Sa
    description: One line for search results and link previews.
    -->

index.html uses its own full-width layout; everything under pages/guide/
gets the guide layout with the sidebar, in the order of GUIDE below.
Links between pages are written relative to the site root with {{root}}.

Usage:  python3 site/build.py          (then open site/_build/index.html)
"""

import html
import re
import shutil
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "_build"

# The guide, in reading order: (file under pages/guide, sidebar label).
GUIDE = [
    ("index.html", "Getting started"),
    ("choose-your-sa.html", "Choose your Sa"),
    ("varisai-player.html", "Play a varisai"),
    ("alankarams.html", "Alankarams"),
    ("gitams.html", "Gitams"),
    ("swarajatis.html", "Swarajatis"),
    ("varnams.html", "Varnams"),
    ("sing-along.html", "Sing along"),
    ("shruti-practice.html", "Shruti practice"),
    ("find-my-sa.html", "Find my Sa"),
    ("settings.html", "Practice settings"),
    ("tuning.html", "Tuning: Just and Equal"),
    ("microphones.html", "Microphones and headphones"),
    ("accessibility.html", "Accessibility"),
    ("privacy.html", "Privacy"),
    ("credits.html", "Credits"),
]

FRONT = re.compile(r"\A\s*<!--(.*?)-->\s*", re.S)


def read_page(path: Path) -> tuple[dict, str]:
    text = path.read_text(encoding="utf-8")
    meta: dict[str, str] = {}
    m = FRONT.match(text)
    if m:
        for line in m.group(1).strip().splitlines():
            key, _, value = line.partition(":")
            meta[key.strip()] = value.strip()
        text = text[m.end():]
    return meta, text


def fill(template: str, values: dict) -> str:
    for key, value in values.items():
        template = template.replace("{{" + key + "}}", value)
    return template


def sidebar(current: str, root: str) -> str:
    items = []
    for name, label in GUIDE:
        href = f"{root}guide/{name}"
        here = ' aria-current="page"' if name == current else ""
        items.append(f'<li><a href="{href}"{here}>{html.escape(label)}</a></li>')
    return "\n".join(items)


def neighbours(current: str, root: str) -> str:
    names = [n for n, _ in GUIDE]
    i = names.index(current)
    parts = []
    if i > 0:
        n, label = GUIDE[i - 1]
        parts.append(f'<a class="prev" href="{root}guide/{n}"><span>Previous</span>{html.escape(label)}</a>')
    if i < len(GUIDE) - 1:
        n, label = GUIDE[i + 1]
        parts.append(f'<a class="next" href="{root}guide/{n}"><span>Next</span>{html.escape(label)}</a>')
    return "\n".join(parts)


def main() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    shutil.copytree(HERE / "static", OUT)
    base = (HERE / "templates" / "base.html").read_text(encoding="utf-8")
    guide_layout = (HERE / "templates" / "guide.html").read_text(encoding="utf-8")

    # Landing page.
    meta, body = read_page(HERE / "pages" / "index.html")
    page = fill(base, {"title": meta["title"], "description": meta["description"], "body": body, "bodyclass": "home"})
    (OUT / "index.html").write_text(fill(page, {"root": ""}), encoding="utf-8")

    # Guide pages.
    (OUT / "guide").mkdir()
    for name, label in GUIDE:
        meta, body = read_page(HERE / "pages" / "guide" / name)
        root = "../"
        content = fill(guide_layout, {
            "content": body,
            "sidebar": sidebar(name, root),
            "neighbours": neighbours(name, root),
        })
        title = f"{meta.get('title', label)} · Purandara guide"
        page = fill(base, {"title": title, "description": meta["description"], "body": content, "bodyclass": "guide"})
        (OUT / "guide" / name).write_text(fill(page, {"root": root}), encoding="utf-8")

    leftover = [p for p in OUT.rglob("*.html") if "{{" in p.read_text(encoding="utf-8")]
    if leftover:
        raise SystemExit(f"unfilled placeholders in {leftover}")
    print(f"built {len(GUIDE) + 1} pages into {OUT}")


if __name__ == "__main__":
    main()
