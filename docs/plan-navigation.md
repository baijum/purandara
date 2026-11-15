# Plan: a navigation menu (drawer, tabs, sidebar)

Status: **approved** (2026-11-15), with the recommended defaults for the open
questions. N1 done.

Today everything starts from one long landing page (`docs/plan-landing.md`):
Continue, the Sa card (tanpura, Practise Sa, Find my Sa, Recordings), then all
ten sets, Plain scale to Varnam, in one list. As the catalog has grown
(alankarams, gitams, swarajatis, varnams, recordings) the page has become long,
and Shruti practice and Recordings are buttons hidden inside a card.

The model is the user's other app, ukulele-companion: a Material navigation
drawer on Android (`ui/navigation/DrawerContent.kt`: a header with the logo,
grouped items under headings that fold open and shut, a divider, Help and a
copyright line), and on iOS tabs on compact width with a `NavigationSplitView`
sidebar on regular width (`ContentView.swift`).

## Decisions (chosen by the user)

1. **iOS follows ukulele-companion.** A `TabView` on compact width (iPhone,
   iPad portrait or split screen); a `NavigationSplitView` sidebar on regular
   width (iPad landscape). There's no slide-in drawer on iOS: it isn't a native
   pattern there.
2. **Each set is its own item.** The menu lists every set, grouped; each opens
   a page with that set's description, run row and exercises. Home keeps
   Continue and the Sa card.
3. **Android: modal on phones, permanent on wide screens.** A
   `ModalNavigationDrawer` (hamburger + edge swipe) on phones; a
   `PermanentNavigationDrawer` at expanded width (≥ 840 dp, e.g. tablets in
   landscape), measured with `LocalConfiguration.screenWidthDp`, so no new
   dependency is needed.

## Destinations

```
Purandara                       (logo + wordmark header)

  Home                          Continue / next up, Your Sa (tanpura, Sa picker)

VARISAIS                        ▾  (folds open and shut, like ukulele-companion)
  Plain scale
  Sarali Varisai
  Madhya Sthayi Varisai
  Janta Varisai
  Vakra Janta Varisai
  Dhattu Varisai
ALANKARAM & SONGS               ▾
  Alankaram
  Gitam
  Swarajati
  Varnam
───────────────
SA & VOICE
  Shruti practice
  Find my Sa
  Recordings
───────────────
  About                         version, licence, website link (opened only if
                                the user taps it; nothing is fetched)
```

- The group headings and their order come from a small shared model,
  `shared/.../navigation/Navigation.kt` (`NavDestination`: each destination,
  its stable id and where Back goes; `NavGroup`: the groups and what is in
  each), so both apps show the same menu and a test keeps every `VarisaiSet`
  in exactly one group. Set pages are titled by `VarisaiSet.title`; the other
  items' labels stay in each app (Android string resources, iOS text).
- A **set page** is today's set block from the landing page: heading, "n of m
  practised", description, run row (where `practisesAsRun`), exercise rows with
  contours and progress. Its title uses `VarisaiSet.shortTitle` where space is
  tight.
- **Home** keeps Continue and the Sa card (tanpura and Sa picker). Practise Sa,
  Find my Sa and Recordings move into the menu. On Home, below the cards, a
  short list of the sets (title + "n of m practised") links to the set pages,
  so Home still works as a start page on its own, and on iPhone, where the
  sets aren't in a tab.
- **The player, a practice run, the recorder and a recording's detail stay
  pushed screens** above the menu, with Back as today. The menu doesn't
  appear on them: no hamburger, and on Android the drawer's edge swipe is off
  there, so it can't clash with notation scrolling or the seek bar.

### iOS tabs (compact width)

| Tab | Holds |
|---|---|
| Practise (`music.note.list`) | Home → set pages → player / run |
| Sa (`tuningfork`) | A small hub: Your Sa (picker, tanpura), Shruti practice, Find my Sa |
| Recordings (`waveform`) | The recordings list → record / detail |

About sits in the Practise tab's toolbar menu. Each tab keeps its own
`NavigationStack` path. The sidebar (regular width) lists the full menu above,
with the detail column holding the chosen destination's `NavigationStack`.

## Behaviour

- **Back on Android:** from a pushed screen, back as today. From a top-level
  destination other than Home, back goes to Home; from Home it leaves the app.
  If the modal drawer is open, back closes it first.
- **Audio:** leaving a screen that plays (player, Shruti practice, Find my Sa,
  recorder, playback) stops it, as Back does today. The Home tanpura keeps
  playing while the student moves between Home and set pages, and stops when
  a screen that uses audio opens (as today, when an exercise opens from the
  landing page).
- **Remembering:** the app opens on Home. The last destination isn't restored
  (a practice app should start from Continue).
- **Run banner, Continue, deep links:** unchanged; they push the player on the
  current stack. On iOS, Continue on Home pushes within the Practise tab.
- **Debug launch arguments:** `-open <varisai-id>`, `-open shruti`,
  `-open find-sa`, `-open recordings`, `-open record` keep working (they select
  the right tab or destination first). New: `-open set-<SET_NAME>` opens a set
  page, for screenshots and UI tests.

## Accessibility

- Android: the drawer has a content description; the header and group headings
  use `heading()`; the fold rows are buttons with an expanded or collapsed state
  description; items are `NavigationDrawerItem`s (selected state announced);
  the hamburger has a label ("Open menu"). Folding honours `LocalReduceMotion`.
  48 dp targets.
- iOS: tab items and sidebar rows have labels; section headers use `.isHeader`;
  the sidebar avoids `Label` clipping (an `HStack` of image and text, per the
  audit lessons); at accessibility text sizes the Sa hub's rows stay a few tall
  rows.
- TalkBack/VoiceOver order: menu button, title, content. When the destination
  changes, focus moves to the new screen's heading.

## Steps

- **N1 Shared model.** Done. `NavDestination` and `NavGroup` in
  `shared/.../navigation/` (groups, order, titles, ids, Back) + tests (every
  set in exactly one group, the order follows `VarisaiSet`, ids round-trip).
- **N2 Android.** Drawer (modal / permanent), `NavDestination` state in
  `PracticeViewModel` (a `StateFlow`), set pages split out of `LibraryScreen`,
  Home trimmed, Sa & voice items wired to the existing routes, back handling,
  About. Update `AccessibilityTest` (reach screens through the drawer; audit the
  drawer, a set page and the permanent layout).
- **N3 iOS.** `RootView`: `TabView` on compact width, `NavigationSplitView` on
  regular width; the Sa hub; set pages split out of `LibraryView`; launch
  arguments. Update `PurandaraUITests` (audit each tab, the sidebar on an iPad
  simulator, a set page). `xcodegen generate`.
- **N4 Docs and site.** Guide (`site/pages/guide/index.html`, `settings.html`)
  and its screenshots, store screenshots, README feature summary, the AGENTS.md
  code map and status line; mark `docs/plan-landing.md` as superseded in part.

Each step lands with its tests passing (`:shared:jvmTest`, `testDebugUnitTest`,
`assembleDebug lintDebug detekt`, the iOS test scheme) and keeps both apps in
step: N2 and N3 ship together, never one app without the other.

## Settled questions (the recommended defaults, approved)

1. **About in the menu?** Default: yes (version, licence, attribution summary,
   a link to the website). Nothing is fetched; the link opens the browser only
   when tapped.
2. **Settings** (strictness, tuning, etc. now live in the player's settings
   sheet and the Sa controls). Default: no Settings item for now; it can come
   later if the settings outgrow the sheet.
3. **Group names.** Default: "Varisais", "Alankaram & songs", "Sa & voice".
4. **Do the groups start open?** Default: all open (as in ukulele-companion);
   the open/shut state is remembered for the session only.
