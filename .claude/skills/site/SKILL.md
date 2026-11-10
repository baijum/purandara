---
name: site
description: Edit, build, preview and publish the Purandara website and guide (site/). Covers writing guide pages for musicians, adding pages and app screenshots, the build and its search index, previewing locally with headless Chrome screenshots (and its narrow-width false alarm), checking search and accessibility, and pushing to publish. Use when the user asks to update the website, the guide or the docs on the site, add a guide page or section, add a screenshot to the site, check or preview the site, or when an app change needs the guide kept in step.
---

# Website and guide

The site is https://baijum.github.io/purandara/: a landing page and a guide
for musicians. Sources are in `site/`; `python3 site/build.py` (standard
library only) writes `site/_build`. Pushing to `main` publishes it:
`.github/workflows/site.yml` runs the same build and deploys to GitHub Pages.
Site commits go straight to `main` (no PR) when the user asks to commit.

## Layout

| Where | What |
|---|---|
| `site/pages/index.html` | Landing page (own full-width layout; not searched) |
| `site/pages/guide/*.html` | Guide pages: HTML fragments with front matter |
| `site/pages/learn/*.html` | Learn: the music for beginners (plan `docs/plan-learn.md`) |
| `site/pages/search.html` | The search page at the site root (`guide/search.html` redirects to it) |
| `site/templates/base.html`, `guide.html` | Masthead, footer; guide sidebar (search box + page list) |
| `site/static/` | `style.css`, `varisai.js` (landing hero), `search.js`, fonts, `images/` |
| `site/build.py` | `GUIDE` list (sidebar order and labels), heading ids, `search.json` |
| `docs/plan-site-search.md` | How search works |

## Writing a guide page

- Front matter first, then one `<h1>`:

  ```html
  <!--
  title: Choose your Sa
  description: One line for link previews.
  -->
  <h1>Choose your Sa</h1>
  ```

- A **new page** needs an entry in `GUIDE` in `site/build.py` (file name and
  sidebar label, in reading order); the sidebar and previous/next links follow
  from it.
- Links and images inside the site use `{{root}}` (`{{root}}guide/settings.html`,
  `{{root}}images/app-play.jpg`); links between guide pages may also be
  relative (`settings.html#tempo-speed`). The build fails on any unfilled
  `{{…}}`.
- Sections are `<h2>`. The build gives an `<h2>` without attributes an id from
  its text (`Building up speed` → `building-speed`) and fails on duplicate ids
  in a page. **If you link to a heading, write its `id` by hand**, so renaming
  the heading doesn't break the link.
- Each `<h2>` section is one search result, so keep sections focused and
  their titles descriptive.
- Write for musicians, not developers: plain words, Carnatic terms (kala,
  avarta, Sa, kattai), the app's own control names in `<em>` (*Tempo*,
  *Speed*), no code names. Short paragraphs; tables for numbers. Say what to
  do, then why. Defer to the teacher where practice differs ("If your teacher
  … do it their way").
- Existing patterns to reuse: `<p class="intro">`, `<div class="note">`,
  `<figure>` with `<figcaption>`, `<dl>` for controls, `<table>` with
  `<th scope="col">`.

**Keep the guide in step with the app.** When a feature or setting changes,
update the guide in the same piece of work: the page for the feature, the
settings page (`settings.html`, including *Defaults at a glance*), and
`accessibility.html` if what screen readers say changed. Facts (ranges,
defaults) come from the code, not memory: e.g. tempo range is
`Schedule.TEMPO_RANGE`. `site/pages/guide/tuning.html` mirrors
`docs/tuning.md`; update both if `Swara.ratio` changes.

## App screenshots

Guide screenshots are iPhone simulator captures, 600 px wide JPEG:

1. Launch the app on the iPhone 17 Pro simulator with the debug launch
   arguments from AGENTS.md: `-resetSettings YES -open <varisai-id>
   -autoplay YES` (add `-sa <midi>`, or `-open shruti` / `-open find-sa`).
2. `xcrun simctl io booted screenshot /tmp/shot.png` at the moment you want.
3. `sips -s format jpeg -s formatOptions 80 --resampleWidth 600 /tmp/shot.png --out site/static/images/app-<name>.jpg`
   (gives 600×1304, like the others).
4. **Terminate the app afterwards** (`xcrun simctl terminate booted
   com.baijum.purandara.ios`): autoplay keeps sounding.
5. In the page: `<img src="{{root}}images/app-<name>.jpg" width="600"
   height="1304" loading="lazy" alt="…">`. The alt text describes what the
   screen shows (it is also searched); drop `loading="lazy"` for the first
   image on a page.

## Build and preview

```bash
python3 site/build.py                       # "built N pages and M search entries"
cd site/_build && python3 -m http.server 8765   # run in the background
```

Serve over HTTP: the search page fetches `search.json`, which fails from
`file://`. Screenshots with headless Chrome:

```bash
C="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$C" --headless=new --disable-gpu --hide-scrollbars --window-size=1280,1100 \
  --virtual-time-budget=4000 --screenshot=/tmp/desk.png "http://localhost:8765/guide/settings.html"
"$C" --headless=new --disable-gpu --hide-scrollbars --window-size=600,1100 \
  --virtual-time-budget=4000 --screenshot=/tmp/narrow.png "http://localhost:8765/guide/settings.html"
```

Then look at the PNGs with Read.

- **Narrow-width false alarm:** headless Chrome won't lay out narrower than
  about 500 px, so a `--window-size=390,…` screenshot looks clipped on the
  right even for unchanged pages. Use 600 for the narrow layout (the
  single-column breakpoint is 52rem); don't "fix" the clipping.
- `--virtual-time-budget` lets scripts (search, the hero) finish before the
  capture. `--dump-dom` instead of `--screenshot` gives the rendered HTML.
- Stop the server when done: `pkill -f "http.server 8765"`.

## Check search

After editing guide text, check a few queries hit the right section first:

```bash
for q in tempo "echo%20canc" S xyzzy; do
  "$C" --headless=new --disable-gpu --virtual-time-budget=4000 --dump-dom \
    "http://localhost:8765/search.html?q=$q" 2>/dev/null \
  | grep -o 'role="status">[^<]*' ; done
```

The status line gives the count ("13 results for “tempo”"); dump the
`#search-results` list to see the order. Matching and ranking are described
in `docs/plan-site-search.md`; `search.js` keeps no lookbehind (older Safari).

## Accessibility

Treat it as seriously as the app:
- One `<h1>`, then `<h2>`/`<h3>` in order; tables have `<th scope>`.
- Every image has alt text (`alt=""` only for decoration); links say where
  they go.
- Form controls have visible `<label>`s; changing text (the search count)
  goes in a `role="status"` region; nothing relies on colour alone.
- Touch targets at least 44 px (2.75rem); focus outlines stay (gold).
- Honour `prefers-reduced-motion` for any animation.
- Headless Chrome can't run a screen reader: for new interactive parts, ask
  the user to try VoiceOver on Safari, and say it wasn't checked.

## Publish

1. `python3 site/build.py` passes.
2. Commit only the sources (`site/_build` is not committed), when the user
   asks; push to `main`.
3. The **site** workflow deploys in a minute or two:
   `gh run list --workflow=site.yml --limit 1` to confirm.
