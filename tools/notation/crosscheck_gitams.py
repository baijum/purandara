"""Compare the app's gitam notation with its two web sources, avarta by avarta.

Not part of the build: it downloads the pages each time and keeps no copies.
Standard library only.

    python3 tools/notation/crosscheck_gitams.py

For each gitam it prints the distinct avartas (swaras only) found only in the
app or only in a source. shivkumar.org doesn't mark the upper octave, so upper
marks are ignored throughout; holds are compared as karvais (shivkumar.org
sometimes repeats the swara under a held syllable instead). Every difference
it reports should be listed in the header of
shared/.../domain/varisai/catalog/Gitam.kt.
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


def main() -> None:
    for title, found in app_gitams().items():
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
