"""Compare the app's swarajati notation with shivkumar.org, avarta by avarta.

Not part of the build: it downloads the page each time and keeps no copy.
Standard library only.

    python3 tools/notation/crosscheck_swarajatis.py

shivkumar.org writes length by letter case: a capital swara lasts two cells,
a lowercase one cell, `,` holds one cell and `;` two; a stray `-` is a word
separator, not a cell. Its octave marks don't
survive in the HTML, so swaras are compared by letter and length only. Each
source avarta (32 cells in Adi at four to a beat) should match one of the
app's; differences belong in the header of Swarajati.kt.
"""

import html
import re
import urllib.request
from pathlib import Path

SWARAJATI_KT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/kotlin/com/baijum/purandara/domain/varisai/catalog/Swarajati.kt"
SOURCES = {"Rara Venu Gopa Bala": "https://www.shivkumar.org/music/varnams/raravenu-swarajati.htm"}
CELLS = 32


def shivkumar_cells(url: str) -> list[str]:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(request, timeout=30).read().decode("latin-1")
    raw = re.sub(r"(?is)<(script|style|head).*?</\1>", "", raw)
    text = html.unescape(re.sub(r"<[^>]+>", " ", re.sub(r"(?i)<br\s*/?>|</p>", "\n", raw)))
    cells = []
    for line in text.split("\n"):
        tokens = line.replace("|", " ").split()
        if not tokens or not all(re.fullmatch(r"[srgmpdnSRGMPDN]+|,|;|-", t) for t in tokens):
            continue
        for token in tokens:
            if token == "-":
                continue  # a word separator in the swara line, not a cell (the cell counts prove it)
            if token == ",":
                cells.append(",")
            elif token == ";":
                cells += [",", ","]
            else:
                for c in token:
                    cells.append(c.upper())
                    if c.isupper():
                        cells.append(",")
    return cells


def app_avartas() -> dict[str, set[str]]:
    source = SWARAJATI_KT.read_text()
    body = source[source.index('"""') + 3:source.rindex('"""')]
    pieces, title = {}, None
    for line in body.split("\n"):
        line = line.strip()
        if line.startswith("title:"):
            title = line.split(":", 1)[1].strip()
            pieces[title] = set()
        elif title and line and line[0] in "SRGMPDN":
            cells = [t[0] if t != "," else "," for t in line.replace("|", " ").split()]
            pieces[title].add(" ".join(cells))
    return pieces


def main() -> None:
    for title, ours in app_avartas().items():
        cells = shivkumar_cells(SOURCES[title])
        theirs = [" ".join(cells[i:i + CELLS]) for i in range(0, len(cells) - CELLS + 1, CELLS)]
        print(f"## {title}: {len(cells)} cells on shivkumar.org ({len(cells) / CELLS:.1f} avartas)")
        matched = sum(1 for a in theirs if a in ours)
        print(f"  {matched} of {len(theirs)} source avartas match one of the app's")
        for a in theirs:
            if a not in ours:
                print(f"  only on shivkumar.org: {a}")


if __name__ == "__main__":
    main()
