# Plan: Shruti practice, strictness, and choosing Sa

Status: **done** (P1–P3, 2026-11-06): the user chose all four ideas taken from a
review of a shruti web app. These are Purandara's own designs, not copies.
Verified with the debug synthetic singer on the Android emulator and iOS
simulator; not yet tried with a real voice.

1. **Hold practice**: hold Sa, Pa or upper Sa in tune for 10, 20 or 30 seconds.
2. **Free singing with a pitch trace**: the tanpura plays, you sing, and you see
   your pitch against the swara lines (last ~10 s).
3. **Strictness levels** for what counts as "in tune" and "close".
4. **Help choosing Sa**: voice-range hints in the Sa list, a **fine-tune** of
   ±50 cents, and **Find my Sa** (sing your lowest and highest comfortable notes,
   get a suggested kattai).

Everything lands on both apps.

## Where it lives

The landing page's **Your Sa (Shruthi)** card gets two buttons:

- **Practise Sa** opens the **Shruti practice** screen: the tanpura starts, the
  pitch trace and live reading show what you sing, and below it the **Hold**
  panel (target Sa / Pa / Sa′, goal 10 / 20 / 30 s, a progress bar for the
  current hold, your best).
- **Find my Sa** opens a two-step guide. Each step says: sing and hold a
  comfortable note for a couple of seconds. It ends with "Suggested Sa: D3 ·
  2 kattai" and **Use this**.

The current **Play tanpura** button stays.

On iOS the hold choices (note and goal) open in a sheet from a "Hold Sa for 10
seconds · Change" row, so the screen fits without scrolling (accessibility
audit) and leaving it doesn't stop the tanpura; Android shows them inline.

## Decisions (defaults)

| Topic | Default |
|---|---|
| Strictness levels | **Gentle** in tune ±20¢ / close ±40¢; **Steady** ±15 / ±30; **Precise** ±10 / ±25 (today's zones). Default **Precise**, keeping the earlier decision. A practice setting in both apps that applies to sing-along and Shruti practice (iOS: the last section of the settings sheet). |
| Hold rule | On target = the target swara in the right octave, within the *close* zone. Breaks of up to 0.3 s (a breath, a wobble) don't end a hold. Goal reached when one hold lasts the goal. |
| Room check | Shruti practice listens for 2 s at the start ("stay quiet") to measure room noise and tanpura leakage (the same leakage model as sing-along). |
| Fine-tune | −50…+50 cents in 1¢ steps; shown as "C3 · 1 kattai +12¢". Applies everywhere (harmonium, tanpura, scoring). Android: under Sa in practice settings. iOS: at the top of the Sa list (the settings sheet's first screen must stay short for the accessibility audit). |
| Voice-range hints | Sa list grouped: **Lower voices** G2–B2, **Most men** C3–E3, **Most women** F3–A♯3, **Higher voices** B3–C4. |
| Find my Sa | A held note = 1.5 s of steady pitch (within ±50¢ of its median). Suggested Sa: lower Pa (Sa − 5 semitones) at or above your lowest note, and upper Ri (Sa + 14) at or below your highest, choosing the middle of the possible Sas, within G2–C4. If your range is too narrow for both, choose the closest compromise and say so. |

## Milestones

| | Scope | Tests |
|---|---|---|
| P1 ✅ | Shared: `Strictness` threaded through zones and scoring; `HoldTracker`; `ShrutiSession` (room check, readings, hold); `NoteCatcher` + `SaFinder`; Sa fine-tune; voice ranges | commonTest |
| P2 ✅ | Android: Shruti practice screen, Find my Sa, strictness and fine-tune settings, grouped Sa list | unit + `AccessibilityTest` |
| P3 ✅ | iOS: the same (mic windowing shared with the sing-along engine) | XCTest + UI audits |
