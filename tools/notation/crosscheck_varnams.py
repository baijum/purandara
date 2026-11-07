"""Compare the app's varnam notation with shivkumar.org's pages, by letter stream.

Not part of the build: it downloads the pages each time and keeps no copies.
Standard library only.

    python3 tools/notation/crosscheck_varnams.py

The four varnams' primary source is shivkumar.org's notation PDF (read by
hand; its hooked-comma rests and hyphenated swaras survive nowhere else).
Each page's HTML keeps the same letters, so comparing letter streams —
octave-, hold- and variant-agnostic — validates the transcription against an
independent extraction path. The comparison runs over the written order (each
section once); the app's sung order adds the `(section @2)` repeats.

The second sources for issue #7 (rosemuralikrishnan.com's Ninnukori: personal-
use-only, checked by hand against the pallavi opening; patantara.com's Evvari
Bodhana: images; carnaticpedia.com's Sami Ninne: JavaScript-rendered; the NIOS
book's Jalajaksha) are not machine-readable here. Every difference this
script reports should be listed in the header of
shared/.../domain/varisai/catalog/Varnam.kt.
"""

import html
import re
import urllib.request
from pathlib import Path

VARNAM_KT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/kotlin/com/baijum/purandara/domain/varisai/catalog/Varnam.kt"
SOURCES = {
    "Ninnukori": "https://www.shivkumar.org/music/varnams/ninnukori.htm",
    "Evvari Bodhana": "https://www.shivkumar.org/music/varnams/evvaribodhana.htm",
    "Sami Ninne Kori": "https://www.shivkumar.org/music/varnams/samininne.htm",
    "Jalajaksha": "https://www.shivkumar.org/music/varnams/jalajakshi.htm",
}


def app_letter_stream(title: str) -> str:
    """The app's letters for one varnam, in written order."""
    source = VARNAM_KT.read_text()
    body = source[source.index('"""') + 3:source.rindex('"""')]
    letters, mine = [], False
    for line in body.split("\n"):
        line = line.strip()
        if line.startswith("title:"):
            mine = line.split(":", 1)[1].strip() == title
        elif mine and line and line[0] in "SRGMPDN_":
            letters += re.findall(r"[SRGMPDN]", line)
    return "".join(letters)


def shivkumar_letter_stream(url: str) -> str:
    """The page's letters in order. Collection starts after the page's last
    section heading ("Pallavi"/"Anupallavi"/"Meanings"): the sahityam and
    meanings above repeat letters in prose. Only lines of pure swara tokens
    count (the word rows and the "(San)" return markers have other letters)."""
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(request, timeout=30).read().decode("utf-8", errors="replace")
    raw = re.sub(r"(?is)<(script|style|head).*?</\1>", "", raw)
    text = html.unescape(re.sub(r"<[^>]+>", " ", re.sub(r"(?i)<br\s*/?>|</p>", "\n", raw)))
    lines = re.sub(r"[\u00a0\ufffd]", " ", text).split("\n")
    headings = [i for i, line in enumerate(lines) if re.search(r"meanings", line, re.I)]
    start = headings[-1] + 1 if headings else 0
    letters = []
    for line in lines[start:]:
        tokens = line.replace("|", " ").split()
        if not tokens:
            continue
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
            token = re.sub(r"[-–−—˘]", "", token)
            if not re.fullmatch(r"[srgmpdnSRGMPDN][0-9]?|,", token or "x"):
                cleaned = []
                break  # not a notation line
            cleaned.append(token)
        for token in cleaned:
            if token != ",":
                letters.append(token[0].upper())
    return "".join(letters)


def main() -> None:
    for title, url in SOURCES.items():
        theirs = shivkumar_letter_stream(url)
        ours = app_letter_stream(title)
        print(f"## {title}")
        print(f"  letter streams ({len(theirs)} vs {len(ours)}): {'match' if theirs == ours else 'DIFFER'}")
        if theirs != ours:
            for i, (a, b) in enumerate(zip(theirs, ours)):
                if a != b:
                    print(f"    first difference at letter {i}: source {a}, app {b}")
                    print(f"    source around it: ...{theirs[max(0, i - 10):i + 10]}...")
                    print(f"    app around it:    ...{ours[max(0, i - 10):i + 10]}...")
                    break
            if len(theirs) != len(ours):
                print(f"    lengths differ by {len(theirs) - len(ours)}")


if __name__ == "__main__":
    main()
