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

The build also writes search.json, the index for the guide's search page
(pages/guide/search.html, static/search.js): one entry per <h2> section of
each guide page, as plain text. An <h2> without an id gets one from its text,
so every result can link to its heading.

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

# The search page: a guide page that is not in the sidebar or the index.
SEARCH_PAGE = "search.html"

SEARCH_FORM = """<form class="guide-search" role="search" action="{{root}}guide/search.html">
      <label for="guide-q">Search the guide</label>
      <div class="search-row">
        <input id="guide-q" name="q" type="search" autocomplete="off" spellcheck="false">
        <button type="submit">Search</button>
      </div>
    </form>"""

# Tags that run on in a line of text; any other tag separates words.
INLINE = {"a", "abbr", "b", "code", "em", "i", "kbd", "mark", "small", "span", "strong", "sub", "sup"}
SKIP = {"script", "style", "noscript"}


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


def index_entries(body: str, name: str, label: str) -> list[dict]:
    parser = Sections(label)
    parser.feed(body)
    entries = []
    for section, anchor, parts in parser.sections:
        text = " ".join("".join(parts).split())
        if text or anchor:
            entries.append({"page": f"guide/{name}", "pageTitle": label,
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

    # Guide pages, and the search index from them.
    (OUT / "guide").mkdir()
    index: list[dict] = []
    root = "../"
    for name, label in GUIDE:
        meta, body = read_page(HERE / "pages" / "guide" / name)
        body = add_heading_ids(body, name)
        index += index_entries(body, name, label)
        content = fill(guide_layout, {
            "search": SEARCH_FORM,
            "content": body,
            "sidebar": sidebar(name, root),
            "neighbours": neighbours(name, root),
        })
        title = f"{meta.get('title', label)} · Purandara guide"
        page = fill(base, {"title": title, "description": meta["description"], "body": content, "bodyclass": "guide"})
        (OUT / "guide" / name).write_text(fill(page, {"root": root}), encoding="utf-8")

    # The search page has its own, larger search box instead of the sidebar's.
    meta, body = read_page(HERE / "pages" / "guide" / SEARCH_PAGE)
    content = fill(guide_layout, {"search": "", "content": body, "sidebar": sidebar(SEARCH_PAGE, root), "neighbours": ""})
    page = fill(base, {"title": f"{meta['title']} · Purandara guide", "description": meta["description"],
                       "body": content, "bodyclass": "guide"})
    (OUT / "guide" / SEARCH_PAGE).write_text(fill(page, {"root": root}), encoding="utf-8")
    (OUT / "search.json").write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    leftover = [p for p in OUT.rglob("*.html") if "{{" in p.read_text(encoding="utf-8")]
    if leftover:
        raise SystemExit(f"unfilled placeholders in {leftover}")
    print(f"built {len(GUIDE) + 2} pages and {len(index)} search entries into {OUT}")


if __name__ == "__main__":
    main()
