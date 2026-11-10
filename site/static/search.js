/* Search for the guide: matches the words typed against search.json (written
   by build.py, one entry per section) and lists the sections that contain
   them all. Plan: docs/plan-site-search.md. No libraries. */
(function () {
  "use strict";

  var script = document.currentScript;
  var form = document.querySelector("form.search-page");
  var input = document.getElementById("q");
  var status = document.getElementById("search-status");
  var list = document.getElementById("search-results");
  if (!script || !form || !input || !status || !list) return;

  var MAX_RESULTS = 30;
  var SNIPPET_BEFORE = 80;   // characters of context before the first match
  var SNIPPET_LENGTH = 220;
  var WORD = /[\p{L}\p{N}]/u;

  var entries = null;        // the index, folded once loaded
  var loading = null;
  var timer = 0;

  /* Folds case, accents and typographic punctuation. Returns the folded
     string and, for each of its characters, the index in the original. */
  function fold(text) {
    var out = "";
    var map = [];
    var i = 0;
    for (var ch of text) {
      var f = ch.normalize("NFKD").replace(/[\u0300-\u036F]/g, "").toLowerCase()
        .replace(/[\u2018\u2019]/g, "'").replace(/[\u201C\u201D]/g, '"').replace(/[\u2010-\u2015]/g, "-");
      for (var j = 0; j < f.length; j++) map.push(i);
      out += f;
      i += ch.length;
    }
    map.push(text.length);
    return { text: out, map: map };
  }

  function escapeRegExp(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  /* One pattern per query word: it must start a word in the text. A query of
     a single one-letter word ("S") must be the whole word. Each pattern also
     matches the character before the word (no lookbehind: older Safari). */
  function patterns(query) {
    var words = fold(query).text.split(/[^\p{L}\p{N}']+/u).filter(Boolean);
    if (words.length > 1) words = words.filter(function (w) { return w.length > 1; });
    var whole = words.length === 1 && words[0].length === 1;
    return words.map(function (w) {
      return {
        re: new RegExp("(?:^|[^\\p{L}\\p{N}'])" + escapeRegExp(w) + (whole ? "(?![\\p{L}\\p{N}])" : ""), "gu"),
        length: w.length
      };
    });
  }

  function count(p, text, cap) {
    p.re.lastIndex = 0;
    var n = 0;
    while (n < cap && p.re.exec(text)) n++;
    return n;
  }

  function search(query) {
    var res = patterns(query);
    if (!res.length) return null;
    var hits = [];
    entries.forEach(function (e, order) {
      var score = 0;
      for (var k = 0; k < res.length; k++) {
        var s = 10 * count(res[k], e.foldedSection, 1) +
                5 * count(res[k], e.foldedPage, 1) +
                count(res[k], e.folded.text, 5);
        if (!s) return;
        score += s;
      }
      hits.push({ entry: e, score: score, order: order });
    });
    hits.sort(function (a, b) { return b.score - a.score || a.order - b.order; });
    return { hits: hits, patterns: res };
  }

  /* The matches in the folded text, each widened to the end of its word. */
  function matches(res, folded) {
    var found = [];
    res.forEach(function (p) {
      p.re.lastIndex = 0;
      var m;
      while ((m = p.re.exec(folded))) {
        var end = m.index + m[0].length;
        var start = end - p.length;
        while (end < folded.length && WORD.test(folded[end])) end++;
        found.push([start, end]);
      }
    });
    return found.sort(function (a, b) { return a[0] - b[0]; });
  }

  /* A few lines of text around the first match, with every match marked. */
  function snippet(entry, res) {
    var text = entry.text;
    var folded = entry.folded;
    var found = matches(res, folded.text).map(function (r) {
      return [folded.map[r[0]], folded.map[r[1]]];
    });
    var start = found.length ? Math.max(0, found[0][0] - SNIPPET_BEFORE) : 0;
    if (start > 0) start = text.indexOf(" ", start) + 1;
    var end = Math.min(text.length, start + SNIPPET_LENGTH);
    if (end < text.length) {
      var space = text.lastIndexOf(" ", end);
      if (space > start) end = space;
    }
    var p = document.createElement("p");
    var at = start;
    if (start > 0) p.append("… ");
    found.forEach(function (r) {
      var a = Math.max(r[0], at);
      var b = Math.min(r[1], end);
      if (a >= b) return;
      p.append(text.slice(at, a));
      var mark = document.createElement("mark");
      mark.textContent = text.slice(a, b);
      p.append(mark);
      at = b;
    });
    p.append(text.slice(at, end));
    if (end < text.length) p.append(" …");
    return p;
  }

  function show(query) {
    list.replaceChildren();
    list.hidden = true;
    var q = query.trim();
    if (!q) {
      status.textContent = "Type a word or two to search every page of the guide.";
      return;
    }
    var result = search(q);
    var hits = result ? result.hits : [];
    if (!hits.length) {
      status.textContent = "No results for \u201C" + q + "\u201D. Try fewer or shorter words.";
      return;
    }
    var shown = hits.slice(0, MAX_RESULTS);
    status.textContent = (hits.length > shown.length
      ? "The first " + shown.length + " of " + hits.length + " results"
      : hits.length === 1 ? "1 result" : hits.length + " results") + " for \u201C" + q + "\u201D";
    shown.forEach(function (hit) {
      var e = hit.entry;
      var li = document.createElement("li");
      var a = document.createElement("a");
      a.href = "../" + e.page + (e.anchor ? "#" + e.anchor : "");
      if (e.section && e.section !== e.pageTitle) {
        var page = document.createElement("span");
        page.className = "result-page";
        page.textContent = e.pageTitle;
        a.append(page, " › " + e.section);
      } else {
        a.textContent = e.pageTitle;
      }
      li.append(a, snippet(e, result.patterns));
      list.append(li);
    });
    list.hidden = false;
  }

  function load() {
    if (!loading) {
      status.textContent = "Loading the search index…";
      loading = fetch(script.dataset.index)
        .then(function (r) {
          if (!r.ok) throw new Error(r.status);
          return r.json();
        })
        .then(function (data) {
          entries = data.map(function (e) {
            e.folded = fold(e.text);
            e.foldedSection = fold(e.section).text;
            e.foldedPage = fold(e.pageTitle).text;
            return e;
          });
        })
        .catch(function () {
          loading = null;
          status.textContent = "The search index could not be loaded. Check your connection and try again, or use the guide menu.";
          throw new Error("search index");
        });
    }
    return loading;
  }

  function update() {
    var q = input.value;
    var url = new URL(location.href);
    if (q.trim()) url.searchParams.set("q", q); else url.searchParams.delete("q");
    history.replaceState(null, "", url);
    if (entries) show(q);
    else if (q.trim()) load().then(function () { show(input.value); }, function () {});
    else show(q);
  }

  input.addEventListener("input", function () {
    clearTimeout(timer);
    timer = setTimeout(update, 150);
  });
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    clearTimeout(timer);
    update();
  });

  input.value = new URLSearchParams(location.search).get("q") || "";
  update();
})();
