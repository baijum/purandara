# Plan: full-text search for the website guide

## Context

The guide (`site/pages/guide/`, 16 pages, about 75 KB of HTML) has grown
long enough that finding something like "tempo", "echo cancellation" or
"kattai" means guessing which page it is on. GitHub Pages serves static
files only, so search must run in the browser.

What the user decided (2026-11-10):
1. **Client-side search, no third-party library.** The index is made by
   `site/build.py` (still standard library only); the search code is ours.
2. **The search box lives in the guide sidebar**, not the masthead. The
   landing page gets no box (its *Guide* link leads to one).
3. **Plan first** (this document), then build.

Outcome: from any guide page you can type a word and get a list of the
sections that mention it, each linking straight to its heading.

This plan was agreed and built on 2026-11-10. Built as planned, except:

- The sidebar box is in its own block above the page list (not inside the
  *Guide* navigation), and the search page leaves it out, so a page never
  has two search landmarks.
- Front-matter descriptions are not indexed: they repeat each page's
  introduction.
- `page` in `search.json` is the path from the site root
  (`guide/varisai-player.html`).
- An apostrophe does not start a word ("S" does not find "Dasa’s"); no
  lookbehind in the patterns, for older Safari.
- The site has no dark theme, so `<mark>` is gold-soft on ink in light only.
- Not yet checked with VoiceOver; checked in Chrome at desktop and narrow
  widths.

## Design

### The index (`site/build.py`)

After rendering the guide pages, `build.py` writes `_build/search.json`:

```json
[
  {"page": "guide/varisai-player.html", "pageTitle": "Play a varisai",
   "section": "Building up speed", "anchor": "building-speed",
   "text": "How fast you sing depends on tempo and speed together: …"},
  …
]
```

- **One entry per section.** A page is split at each `<h2>`; the text
  before the first `<h2>` is an entry with the page title as its section
  and no anchor. An `<h2>` without an `id` gets one generated from its text
  (lower case, hyphens), and the id is written into the page too, so every
  result can link to its heading. Existing ids are kept.
- **Plain text only.** Tags are stripped with `html.parser` (standard
  library); `alt` text of images is kept (it describes the screenshots);
  entities are decoded; whitespace is collapsed. Front-matter descriptions
  are added to the page's first entry.
- **Size:** about 60 KB, about 15 KB gzipped by GitHub Pages. No
  pre-built inverted index: at this size a linear scan in the browser is
  instant, and the JSON stays readable.
- The landing page is not indexed (it repeats the guide).

### The search box (guide sidebar)

`templates/guide.html` gets, above the page list:

```html
<form class="guide-search" role="search" action="{{root}}guide/search.html">
  <label for="guide-q">Search the guide</label>
  <input id="guide-q" name="q" type="search" autocomplete="off">
  <button type="submit">Search</button>
</form>
```

It is a plain GET form, so submitting it (Enter or the button) opens the
results page with `?q=…`, with or without JavaScript. On narrow screens,
where the sidebar sits above the page as a wrapped list, the box sits on
its own line at full width.

### The results page (`pages/guide/search.html`)

A guide page like the others (sidebar, guide layout), but not in the
sidebar list or the previous/next links. It holds:

- a larger search box (same form, prefilled from `?q=`), which updates the
  results as you type (after a short pause, about 150 ms);
- a status line, `<p role="status">`: "5 results for *tempo*", "No
  results for *xyz*", or, before a search, a hint;
- the results as an ordered list: each item a link "*Page* › *Section*"
  to `page#anchor`, and a snippet of about 25 words around the first match
  with the matched words in `<mark>`;
- without JavaScript: a `<noscript>` line saying search needs JavaScript,
  and pointing to the page list in the sidebar.

The address bar follows the query (`history.replaceState`), so a search
can be bookmarked or shared.

### Matching (`static/search.js`, about 120 lines)

- **Words:** the query is split on spaces; every word must match (AND).
  There is no phrase syntax. Single letters are ignored unless they are the
  whole query (so "S" or "R" alone still finds the swara pages).
- **Folding:** case and diacritics are folded on both sides (`normalize
  ("NFD")` and dropping combining marks), so "kala" finds "kāla" and "sa"
  finds "Sa". Typographic quotes and dashes fold to plain ones.
- **Prefix:** each word matches at the start of a word in the text
  ("tem" finds "tempo"), so results appear while typing. Matches inside a
  word are not counted ("ala" does not find "tala").
- **Ranking:** a match in the section title scores 10, in the page title
  5, in the text 1 per occurrence (capped at 5). Ties keep reading order
  (the order of `GUIDE` in `build.py`). At most 30 results are shown.
- The index is fetched once, on the first keystroke or when the results
  page opens with a query.

### Accessibility

- The form is a search landmark with a visible label (not a placeholder).
- The status line is a live region, so the count is announced; the list
  itself is not live (it would be read on every keystroke).
- Results are ordinary links in a list; headings stay in order (the page
  has one `h1`, "Search the guide").
- `<mark>` keeps the default high-contrast colours, checked in light and
  dark against the site palette; matches are also not shown by colour
  alone (they are bold).
- The search box is at least 44 px tall; focus outlines as elsewhere.
- Honour `prefers-reduced-motion` (no animation is planned anyway).

## Out of scope

- A masthead search box on every page (the user chose the sidebar).
- Searching the landing page, the app itself, or the notation catalog.
- Spelling correction, stemming ("speeds" vs "speed" is handled by prefix
  only one way), synonyms ("kala" ↔ "speed").
- Any third-party search library or hosted search.

## Stages

| Stage | What | Checks |
|---|---|---|
| S1 | `build.py`: section split, missing h2 ids, `search.json` | build fails on duplicate ids within a page; spot-check entries |
| S2 | `search.js`, `search.html`, sidebar form, CSS | manual: queries "tempo", "kāla", "S", "xyz"; keyboard only; VoiceOver on Safari (status announced, landmarks) |
| S3 | Guide: mention search in Getting started; AGENTS.md code map (`site/` row) | build, links |

## Decided

1. The sidebar box goes to the results page on Enter; no live dropdown under
   it (the sidebar is narrow, and one results page is simpler and easier to
   make accessible).
