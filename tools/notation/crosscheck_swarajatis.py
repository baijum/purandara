"""Compare the app's swarajati notation with shivkumar.org, avarta by avarta.

Not part of the build: it downloads the page each time and keeps no copy.
Standard library only.

    python3 tools/notation/crosscheck_swarajatis.py

shivkumar.org writes length by letter case: a capital swara lasts two cells,
a lowercase one cell, `,` holds one cell and `;` two; a stray `-` is a word
separator, not a cell. Its octave marks don't
survive in the HTML, so swaras are compared by letter and length only. Each
source avarta (its page's cells per avarta: 32 in Adi at four to a beat, 16 at
two) should match one of the app's; differences belong in the header of
Swarajati.kt.

Sambasiva's page repeats its song text before the notation, dashes stuck to
swaras or between them ("m-g", "dp -") are printing noise, and its khanda-gati
charanam (after the page's "khaNDa gati" heading) does not expand to whole
40-cell avartas — so for it the script additionally compares the whole letter
stream (the pitch sequence) of the piece, which is what two sources must
agree on; the few known differences are listed in Swarajati.kt's header.
"""

import html
import re
import urllib.request
from pathlib import Path

SWARAJATI_KT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/kotlin/com/baijum/purandara/domain/varisai/catalog/Swarajati.kt"

# title -> (page, cells per avarta, heading that starts a khanda-gati section or None)
SOURCES = {
    "Rara Venu Gopa Bala": ("https://www.shivkumar.org/music/varnams/raravenu-swarajati.htm", 32, None),
    "Sambasiva Yenave": ("https://www.shivkumar.org/music/varnams/sambasiva-swarajati.htm", 16, "khaNDa gati"),
}


def shivkumar_lines(url: str) -> list[str]:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(request, timeout=30).read().decode("utf-8", errors="replace")
    raw = re.sub(r"(?is)<(script|style|head).*?</\1>", "", raw)
    text = html.unescape(re.sub(r"<[^>]+>", " ", re.sub(r"(?i)<br\s*/?>|</p>", "\n", raw)))
    return re.sub(r"[\u00a0\ufffd]", " ", text).split("\n")


def shivkumar_cells(lines: list[str], khanda_marker: str | None) -> tuple[list[str], list[str]]:
    """(cells of the non-khanda sections, cells of the khanda-gati part alone)."""
    cells: list[str] = []
    khanda: list[str] | None = None
    for line in lines:
        # The page repeats the song text before the notation, so its first
        # khanda heading is in the lyrics; only switch once notation has begun.
        if khanda_marker and "khanda gati" in line.lower() and cells:
            khanda = []
        tokens = line.replace("|", " ").split()
        if not tokens:
            continue
        cleaned = []
        for token in tokens:
            # A pure dash (hyphen or en dash) is a word separator, not a cell;
            # a dash stuck to a swara or between swaras ("-N", "m-g", "dp-") is
            # printing noise around it.
            if all(c in "-–−—" for c in token):
                continue
            token = re.sub(r"[-–−—]", "", token)
            if not re.fullmatch(r"[srgmpdnSRGMPDN]+|,|;", token or "x"):
                cleaned = []
                break  # not a notation line
            cleaned.append(token)
        if not cleaned:
            continue
        for token in cleaned:
            if token == ",":
                cells.append(",")
                if khanda is not None:
                    khanda.append(",")
            elif token == ";":
                cells += [",", ","]
                if khanda is not None:
                    khanda += [",", ","]
            else:
                for c in token:
                    if khanda is None:
                        cells.append(c.upper())
                    else:
                        khanda.append(c.upper())
                    if c.isupper():
                        if khanda is None:
                            cells.append(",")
                        else:
                            khanda.append(",")
    return cells, [c for c in khanda or [] if c != ","]


def app_avartas() -> dict[str, tuple[set[str], list[str]]]:
    """title -> (the app's avartas as cell strings, the same avartas kept separately)."""
    source = SWARAJATI_KT.read_text()
    body = source[source.index('"""') + 3:source.rindex('"""')]
    pieces, title = {}, None
    for line in body.split("\n"):
        line = line.strip()
        if line.startswith("title:"):
            title = line.split(":", 1)[1].strip()
            pieces[title] = (set(), [])
        elif title and line and line[0] in "SRGMPDN":
            cells = [t[0] if t != "," else "," for t in line.replace("|", " ").split()]
            pieces[title][0].add(" ".join(cells))
            pieces[title][1].append(" ".join(cells))
    return pieces


def main() -> None:
    ours = app_avartas()
    for title, (url, cells_per_avarta, khanda_marker) in SOURCES.items():
        app_avarta_set, app_avartas_list = ours[title]
        lines = shivkumar_lines(url)
        cells, khanda_cells = shivkumar_cells(lines, khanda_marker)
        theirs = [" ".join(cells[i:i + cells_per_avarta]) for i in range(0, len(cells) - cells_per_avarta + 1, cells_per_avarta)]
        print(f"## {title}: {len(cells)} cells on shivkumar.org ({len(cells) / cells_per_avarta:.1f} avartas)")
        matched = sum(1 for a in theirs if a in app_avarta_set)
        print(f"  {matched} of {len(theirs)} source avartas match one of the app's, by letter and length")
        for a in theirs:
            if a not in app_avarta_set:
                print(f"  only on shivkumar.org: {a}")
        if khanda_marker:
            # The page's dashes are ambiguous (sometimes a hold, sometimes
            # printing noise), so the sections with them are compared by
            # letter alone: the pitch sequence is what two sources must agree on.
            ours_letters = "".join(
                c for a in app_avartas_list for c in a.split() if c != ","
            )
            theirs_letters = "".join(c for c in cells + khanda_cells if c != ",")
            print(f"  letter streams ({len(theirs_letters)} vs {len(ours_letters)}): "
                  f"{'match' if theirs_letters == ours_letters else 'DIFFER'}")
            if theirs_letters != ours_letters:
                for i, (a, b) in enumerate(zip(theirs_letters, ours_letters)):
                    if a != b:
                        print(f"    first difference at letter {i}: source {a}, app {b}")
                        print(f"    source around it: ...{theirs_letters[max(0, i - 8):i + 8]}...")
                        print(f"    app around it:    ...{ours_letters[max(0, i - 8):i + 8]}...")
                        break


if __name__ == "__main__":
    main()
