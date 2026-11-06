"""Compare the app's gitam notation with its web sources, avarta by avarta.

Not part of the build: it downloads the pages each time and keeps no copies.
Standard library only.

    python3 tools/notation/crosscheck_gitams.py

For each gitam it prints the distinct avartas (swaras only) found only in the
app or only in a source. shivkumar.org doesn't mark the upper octave, so upper
marks are ignored throughout; holds are compared as karvais (shivkumar.org
sometimes repeats the swara under a held syllable instead). Every difference
it reports should be listed in the header of
shared/.../domain/varisai/catalog/Gitam.kt.

Gitams 6-8 (Kamalajadala, Re Re Sri Ramachandra, Sri Ramachandra) have a
single source each, accepted by the user's decision (issue #1): shivkumar.org,
whose pages differ in conventions (on Re Re's page lowercase means the mandra
octave; Kamalajadala's and Sri Ramachandra's carry variant digits). Their
octave and variant marks survive only in the pages' PDFs, which were read by
hand — so for these the script compares the letter stream alone (the pitch
sequence, octave-agnostic), which is what a single source must still fix.
"""

import html
import re
import urllib.request
from pathlib import Path

GITAM_KT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/kotlin/com/baijum/purandara/domain/varisai/catalog/Gitam.kt"
SOURCES = {
    "Sri Gananatha": ("https://karnatik.com/c1806.shtml", "https://www.shivkumar.org/music/varnams/lambodara-geetham.htm"),
    "Kunda Gaura": ("https://karnatik.com/c1807.shtml", "https://www.shivkumar.org/music/varnams/kundagoura-geetham.htm"),
    "Kereya Neeranu": ("https://karnatik.com/c1808.shtml", "https://www.shivkumar.org/music/varnams/kerayaneeranu-geetham.htm"),
    "Padumanabha": ("https://karnatik.com/c1809.shtml", "https://www.shivkumar.org/music/varnams/padumanabha-geetham.htm"),
    "Analekara": ("https://karnatik.com/c2415.shtml", "https://www.shivkumar.org/music/varnams/analekara-geetham.htm"),
}

# Single-source gitams: shivkumar.org page, compared by letter stream alone.
SINGLE_SOURCES = {
    "Kamalajadala": "https://www.shivkumar.org/music/varnams/kamalajathala-kalyani-geetham.htm",
    "Re Re Sri Ramachandra": "https://www.shivkumar.org/music/varnams/reresrirama-arabhi-geetham.htm",
    "Sri Ramachandra": "https://www.shivkumar.org/music/varnams/srirama-bhairavi-geetham.htm",
}


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(request, timeout=30).read().decode("latin-1")
    raw = re.sub(r"(?is)<(script|style|head).*?</\1>", "", raw)
    raw = re.sub(r"(?i)<br\s*/?>|</p>|</tr>", "\n", raw)
    return html.unescape(re.sub(r"<[^>]+>", " ", raw))


def avartas(line: str, token_to_swara) -> list[str]:
    out = []
    for segment in line.split("||"):
        tokens = segment.replace("|", " ").split()
        if tokens:
            out.append(" ".join(token_to_swara(t) for t in tokens))
    return out


def karnatik(text: str) -> set[str]:
    found = set()
    for line in text.split("\n"):
        tokens = line.replace("|", " ").split()
        if "||" in line and tokens and all(re.fullmatch(r"[srgmpdnSRGMPDN]\.?|,", t) for t in tokens):
            found.update(avartas(line, lambda t: "," if t == "," else t[0].upper() + ("_" if t.endswith(".") else "")))
    return found


def shivkumar(text: str) -> set[str]:
    found, buffer = set(), ""
    for line in text.split("\n"):
        candidate = (buffer + " " + line).strip()
        if candidate and re.fullmatch(r"[SRGMPDNsrgmpdn|\-,\s\xa0]*", candidate):
            buffer = candidate
            if candidate.endswith("||"):
                found.update(avartas(candidate, lambda t: "," if t in "-," else t.upper() + ("_" if t.islower() else "")))
                buffer = ""
        else:
            buffer = ""
    return found


def app_gitams() -> dict[str, set[str]]:
    source = GITAM_KT.read_text()
    body = source[source.index('"""') + 3:source.rindex('"""')]
    gitams, title = {}, None
    for line in body.split("\n"):
        line = line.strip()
        if line.startswith("title:"):
            title = line.split(":", 1)[1].strip()
            gitams[title] = set()
        elif title and line and line[0] in "SRGMPDN":
            gitams[title].update(avartas(line, lambda t: "," if t == "," else t[0] + ("_" if "ˌ" in t else "")))
    return gitams


def app_letter_stream(title: str) -> str:
    """The app's letters for one gitam, in order, octave- and variant-agnostic."""
    source = GITAM_KT.read_text()
    body = source[source.index('"""') + 3:source.rindex('"""')]
    letters, mine = [], False
    for line in body.split("\n"):
        line = line.strip()
        if line.startswith("title:"):
            mine = line.split(":", 1)[1].strip() == title
        elif mine and line and line[0] in "SRGMPDN":
            letters += re.findall(r"[SRGMPDN]", line)
    return "".join(letters)


def shivkumar_letter_stream(url: str) -> str:
    """The page's letters in order; its lines' conventions vary, so only the
    letters (never their case, digits or holds) are trusted here. Collection
    starts after the page's last "Geetham" heading: the song text and the
    raga scales above it repeat earlier in the page."""
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(request, timeout=30).read().decode("utf-8", errors="replace")
    raw = re.sub(r"(?is)<(script|style|head).*?</\1>", "", raw)
    text = html.unescape(re.sub(r"<[^>]+>", " ", re.sub(r"(?i)<br\s*/?>|</p>", "\n", raw)))
    lines = re.sub(r"[\u00a0\ufffd]", " ", text).split("\n")
    start = max(i for i, line in enumerate(lines) if "geetham" in line.lower()) + 1
    letters = []
    for line in lines[start:]:
        tokens = line.replace("|", " ").split()
        if not tokens:
            continue
        # This page's HTML separates a variant digit from its letter ("D 2");
        # merge such digits back before filtering.
        merged: list[str] = []
        for token in tokens:
            if re.fullmatch(r"[0-9]", token) and merged and re.fullmatch(r"[srgmpdnSRGMPDN][0-9]?", merged[-1]):
                merged[-1] += token
            else:
                merged.append(token)
        cleaned = []
        for token in merged:
            if all(c in "-–−—" for c in token):
                continue
            token = re.sub(r"[-–−—]", "", token)
            if not re.fullmatch(r"[srgmpdnSRGMPDN][0-9]?|,", token or "x"):
                cleaned = []
                break  # not a notation line
            cleaned.append(token)
        for token in cleaned:
            if token != ",":
                letters.append(token[0].upper())
    return "".join(letters)


def main() -> None:
    for title, found in app_gitams().items():
        if title not in SOURCES and title not in SINGLE_SOURCES:
            # Kamala Sulochana: its sources (the "Gurukulam & SF" collection
            # PDF and a staff-and-sargam rendering) were supplied by the user
            # and checked by hand; nothing to download.
            print(f"## {title}: no online source (checked by hand; see Gitam.kt)")
            continue
        if title in SINGLE_SOURCES:
            theirs = shivkumar_letter_stream(SINGLE_SOURCES[title])
            ours = app_letter_stream(title)
            print(f"## {title} (single source: shivkumar.org)")
            print(f"  letter streams ({len(theirs)} vs {len(ours)}): {'match' if theirs == ours else 'DIFFER'}")
            if theirs != ours:
                for i, (a, b) in enumerate(zip(theirs, ours)):
                    if a != b:
                        print(f"    first difference at letter {i}: source {a}, app {b}")
                        print(f"    source around it: ...{theirs[max(0, i - 8):i + 8]}...")
                        print(f"    app around it:    ...{ours[max(0, i - 8):i + 8]}...")
                        break
            continue
        print(f"## {title}")
        for name, url, parse in (("karnatik.com", SOURCES[title][0], karnatik), ("shivkumar.org", SOURCES[title][1], shivkumar)):
            theirs = parse(fetch(url))
            only_app, only_theirs = sorted(found - theirs), sorted(theirs - found)
            if not only_app and not only_theirs:
                print(f"  {name}: same avartas")
            for a in only_app:
                print(f"  only in the app (vs {name}): {a}")
            for a in only_theirs:
                print(f"  only in {name}: {a}")


if __name__ == "__main__":
    main()
