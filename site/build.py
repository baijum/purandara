"""Builds the Purandara website into site/_build (Python standard library only).

Pages live in site/pages as HTML fragments. The first lines of each are
front matter in an HTML comment:

    <!--
    title: Choose your Sa
    description: One line for search results and link previews.
    -->

index.html uses its own full-width layout. The site has two sections with
the sidebar layout, in the order of SECTIONS below: Learn (pages/learn/, the
music) and the Guide (pages/guide/, the app). search.html at the root is the
search page, with both sections in its sidebar.
Links between pages are written relative to the site root with {{root}}.

The build also writes search.json, the index for the search page
(static/search.js): one entry per <h2> section of each section page, as
plain text. An <h2> without an id gets one from its text, so every result
can link to its heading.

Usage:  python3 site/build.py          (then open site/_build/index.html)
"""

import html
import json
import re
import shutil
from html.parser import HTMLParser
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "_build"

# Learn: the music, for beginners. (file under pages/learn, sidebar label)
LEARN = [
    ("index.html", "Start here"),
    ("shruti.html", "Shruti and Sa"),
    ("swaras.html", "The seven swaras"),
    ("mayamalavagowla.html", "Mayamalavagowla"),
    ("tala.html", "Adi tala"),
    ("speeds.html", "The speeds"),
    ("singing-in-tune.html", "Singing in tune"),
    ("varisais.html", "The varisai sets"),
    ("glossary.html", "Words used here"),
]

# The guide to the app, in reading order: (file under pages/guide, sidebar label).
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

# (directory under pages, sidebar title, page-title suffix, pages). Search
# results keep this order on ties.
SECTIONS = [
    ("learn", "Learn", "Learn · Purandara", LEARN),
    ("guide", "Guide", "Purandara guide", GUIDE),
]

FRONT = re.compile(r"\A\s*<!--(.*?)-->\s*", re.S)

SEARCH_FORM = """<form class="guide-search" role="search" action="{{root}}search.html">
      <label for="side-q">Search the site</label>
      <div class="search-row">
        <input id="side-q" name="q" type="search" autocomplete="off" spellcheck="false">
        <button type="submit">Search</button>
      </div>
    </form>"""

# The search page was first at guide/search.html; keep that address working.
OLD_SEARCH = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Search · Purandara</title>
<meta name="robots" content="noindex">
<script>location.replace("../search.html" + location.search);</script>
<meta http-equiv="refresh" content="0; url=../search.html">
</head>
<body><p>Search has moved: <a href="../search.html">search the site</a>.</p></body>
</html>
"""

# Tags that run on in a line of text; any other tag separates words.
INLINE = {"a", "abbr", "b", "code", "em", "i", "kbd", "mark", "small", "span", "strong", "sub", "sup"}
SKIP = {"script", "style", "noscript"}
# Learn pages end with lists of links; they would crowd out real sections.
LINK_SECTIONS = {"practise-this", "further-reading"}


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


def sidebar(folder: str, title: str, pages: list, current: str, root: str) -> str:
    items = []
    for name, label in pages:
        href = f"{root}{folder}/{name}"
        here = ' aria-current="page"' if name == current else ""
        items.append(f'        <li><a href="{href}"{here}>{html.escape(label)}</a></li>')
    return (f'<nav class="guide-nav" aria-label="{title}">\n'
            f'      <p class="guide-nav-title">{title}</p>\n      <ul>\n'
            + "\n".join(items) + "\n      </ul>\n    </nav>")


def neighbours(folder: str, pages: list, current: str, root: str) -> str:
    names = [n for n, _ in pages]
    i = names.index(current)
    parts = []
    if i > 0:
        n, label = pages[i - 1]
        parts.append(f'<a class="prev" href="{root}{folder}/{n}"><span>Previous</span>{html.escape(label)}</a>')
    if i < len(pages) - 1:
        n, label = pages[i + 1]
        parts.append(f'<a class="next" href="{root}{folder}/{n}"><span>Next</span>{html.escape(label)}</a>')
    return "\n".join(parts)


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def plain(fragment: str) -> str:
    return " ".join(html.unescape(re.sub(r"<[^>]+>", "", fragment)).split())


def add_heading_ids(body: str, name: str) -> str:
    """Gives every <h2> without attributes an id from its text; fails on duplicate ids."""
    def with_id(m: re.Match) -> str:
        return f'<h2 id="{slug(plain(m.group(1)))}">{m.group(1)}</h2>'
    body = re.sub(r"<h2>(.*?)</h2>", with_id, body, flags=re.S)
    ids = re.findall(r'\bid="([^"]+)"', body)
    dupes = sorted({i for i in ids if ids.count(i) > 1})
    if dupes:
        raise SystemExit(f"duplicate ids in {name}: {dupes}")
    return body


class Sections(HTMLParser):
    """Splits a guide page into (heading, id, text) at each <h2>."""

    def __init__(self, page_title: str):
        super().__init__(convert_charrefs=True)
        self.sections = [[page_title, "", []]]
        self.skipping = 0
        self.heading: list[str] | None = None
        self.in_h1 = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in SKIP:
            self.skipping += 1
        elif tag == "h1":
            self.in_h1 = True
        elif tag == "h2":
            self.heading = []
            self.sections.append(["", attrs.get("id", ""), []])
        elif tag == "img" and attrs.get("alt"):
            self.sections[-1][2].append(f" {attrs['alt']} ")
        if tag not in INLINE:
            self.sections[-1][2].append(" ")

    def handle_endtag(self, tag):
        if tag in SKIP:
            self.skipping -= 1
        elif tag == "h1":
            self.in_h1 = False
        elif tag == "h2" and self.heading is not None:
            self.sections[-1][0] = " ".join("".join(self.heading).split())
            self.heading = None
        if tag not in INLINE:
            self.sections[-1][2].append(" ")

    def handle_data(self, data):
        if self.skipping or self.in_h1:
            return
        if self.heading is not None:
            self.heading.append(data)
        else:
            self.sections[-1][2].append(data)


def index_entries(body: str, path: str, area: str, label: str) -> list[dict]:
    parser = Sections(label)
    parser.feed(body)
    entries = []
    for section, anchor, parts in parser.sections:
        text = " ".join("".join(parts).split())
        if anchor in LINK_SECTIONS:
            continue
        if text or anchor:
            entries.append({"page": path, "area": area, "pageTitle": label,
                            "section": section, "anchor": anchor, "text": text})
    return entries


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

    # Learn and the guide, and the search index from them.
    index: list[dict] = []
    root = "../"
    count = 1
    for folder, title, suffix, pages in SECTIONS:
        (OUT / folder).mkdir()
        for name, label in pages:
            meta, body = read_page(HERE / "pages" / folder / name)
            body = add_heading_ids(body, f"{folder}/{name}")
            index += index_entries(body, f"{folder}/{name}", title, label)
            content = fill(guide_layout, {
                "search": SEARCH_FORM,
                "navs": sidebar(folder, title, pages, name, root),
                "content": body,
                "neighbours": neighbours(folder, pages, name, root),
            })
            page = fill(base, {"title": f"{meta.get('title', label)} · {suffix}", "description": meta["description"],
                               "body": content, "bodyclass": "guide"})
            (OUT / folder / name).write_text(fill(page, {"root": root}), encoding="utf-8")
            count += 1

    # The search page: its own, larger box, and both sections in the sidebar.
    meta, body = read_page(HERE / "pages" / "search.html")
    content = fill(guide_layout, {
        "search": "",
        "navs": "\n    ".join(sidebar(f, t, p, "", "") for f, t, _, p in SECTIONS),
        "content": body,
        "neighbours": "",
    })
    page = fill(base, {"title": f"{meta['title']} · Purandara", "description": meta["description"],
                       "body": content, "bodyclass": "guide"})
    (OUT / "search.html").write_text(fill(page, {"root": ""}), encoding="utf-8")
    (OUT / "guide" / "search.html").write_text(OLD_SEARCH, encoding="utf-8")
    (OUT / "search.json").write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    count += 1

    leftover = [p for p in OUT.rglob("*.html") if "{{" in p.read_text(encoding="utf-8")]
    if leftover:
        raise SystemExit(f"unfilled placeholders in {leftover}")
    print(f"built {count} pages and {len(index)} search entries into {OUT}")


if __name__ == "__main__":
    main()
