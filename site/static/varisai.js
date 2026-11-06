// The hero's varisai sheet: one beat at a time, the tala strip and the swara
// light up, the voice follows the contour, and a mark appears under each note
// once it has been sung, as the app does during sing-along. With reduced
// motion the finished state is shown and nothing moves.
(function () {
  const sheet = document.querySelector(".varisai-sheet");
  if (!sheet) return;
  const beats = Array.from(sheet.querySelectorAll(".tala li"));
  const swaras = Array.from(sheet.querySelectorAll(".avarta span"));
  const voice = sheet.querySelector(".contour-voice");
  // Scale steps above Sa for S R S R S R G M | S R G M P D N S′.
  const degrees = [0, 1, 0, 1, 0, 1, 2, 3, 0, 1, 2, 3, 4, 5, 6, 7];
  // The one note sung a little high, to show what feedback looks like.
  const HIGH = 10;
  const markFor = (i) => (i === HIGH ? "↑" : "✓");
  const BEAT_MS = 700;
  const REST_MS = 2600;

  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)");

  function showFinished() {
    swaras.forEach((s, i) => s.setAttribute("data-mark", markFor(i)));
    place(swaras.length - 1);
  }

  function place(i) {
    voice.setAttribute("cx", String(i * 20 + 10));
    voice.setAttribute("cy", String(56 - degrees[i] * 8));
  }

  function clear() {
    swaras.forEach((s) => { s.classList.remove("now"); s.removeAttribute("data-mark"); });
    beats.forEach((b) => b.classList.remove("now"));
  }

  let timer = null;
  let i = 0;

  function step() {
    if (i > 0) {
      swaras[i - 1].classList.remove("now");
      swaras[i - 1].setAttribute("data-mark", markFor(i - 1));
    }
    beats.forEach((b) => b.classList.remove("now"));
    if (i === swaras.length) {
      // A breath at the end of the line, then sing it again.
      timer = setTimeout(() => { clear(); i = 0; step(); }, REST_MS);
      return;
    }
    swaras[i].classList.add("now");
    beats[i % beats.length].classList.add("now");
    place(i);
    i += 1;
    timer = setTimeout(step, BEAT_MS);
  }

  function start() {
    clearTimeout(timer);
    clear();
    if (reduce.matches) { showFinished(); return; }
    i = 0;
    step();
  }

  // Pause when the sheet is off screen or the tab is hidden.
  const observer = new IntersectionObserver((entries) => {
    if (reduce.matches) return;
    if (entries[0].isIntersecting) start(); else clearTimeout(timer);
  });
  observer.observe(sheet);
  document.addEventListener("visibilitychange", () => { if (document.hidden) clearTimeout(timer); else if (!reduce.matches) start(); });
  reduce.addEventListener("change", start);
  showFinished();
})();
