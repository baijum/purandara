# Tuning: Just (tanpura) vs Equal (keyboard)

Status: reference for the **Tuning** setting. The numbers below are generated
from the app's own ratio table, `Swara.ratio`; the implementation section maps
each step to code. If the ratios change, regenerate the tables.

The short version:

| | Just (tanpura) | Equal (keyboard) |
|---|---|---|
| Basis | Small whole-number ratios, 5-limit | 12 equal semitones in the octave |
| Semitone | `16/15` = 111.731 cents | 100 cents |
| Major third (G3) | `5/4` = 386.314 cents | 400 cents |
| Perfect fifth (P) | `3/2` = 701.955 cents | 700 cents |
| Octave | `2/1` = 1200 cents | 1200 cents |
| Harmonium playback | 12-TET samples shifted a few cents | 12-TET samples at normal rate (fine tune aside) |
| Tanpura | The recording's natural intervals, not retuned | The same recording; not retuned |
| Best with | Voice and tanpura | Keyboard or other fixed-pitch instrument |
| App default | Yes | No |

Fine tune shifts Sa in both modes; the "normal rate" for Equal assumes Sa is
exactly on a keyboard note. "12-TET" for the samples is the preparation target;
the bundled files measure within about half a cent of it (section 7.2), and the
tanpura loops within about a cent of their target Sa (section 7.3).

Carnatic swaras are relative to **Sa**. Everything below is a ratio or a
cent value, so it is independent of which Sa you choose; frequency columns use
the app's default Sa, C3.

## 1. The two systems

### Just intonation

Just intonation tunes intervals to small whole-number frequency ratios:
`2/1` for the octave, `3/2` for the fifth, `4/3` for the fourth, `5/4` for the
major third, and so on. The ratios come from the harmonic series, so the
partials of the two notes line up and the interval has no beating. The result
is an acoustically pure interval, but a fixed twelve-note instrument cannot
have every key and every interval pure at the same time; tuning one third pure
puts a comma somewhere else. See [Just intonation](https://en.wikipedia.org/wiki/Just_intonation)
and [Five-limit tuning](https://en.wikipedia.org/wiki/Five-limit_tuning).

The app's Just mode is **5-limit**: every ratio is built from powers of 2, 3
and 5 only. That is the smallest limit that gives a pure major third (`5/4`)
alongside pure fifths (`3/2`) and octaves (`2/1`).

### Equal temperament

12-tone equal temperament (12-TET) divides the octave into twelve identical
semitones. Each semitone is the twelfth root of 2:

`2^(1/12) = 1.0594630943592953…`, or exactly 100 cents.

Every fifth is 700 cents, every fourth 500 cents, every major third 400 cents.
The fifths and fourths are close to just; the thirds and sixths are not. Equal
temperament is the usual choice for keyboards because every key has the same
intervals and modulation works everywhere. See
[Equal temperament](https://en.wikipedia.org/wiki/Equal_temperament).

### Cents and percentage

A cent is a logarithmic unit: one hundredth of an equal-tempered semitone.

```
cents(f2/f1) = 1200 × log2(f2 / f1)
f2           = f1 × 2^(cents / 1200)
```

One cent is a frequency factor of `2^(1/1200) = 1.0005777895…`; one equal
semitone is `2^(1/12) = 1.0594630943…`.

The percentage used in this document is the **linear frequency difference**:

```
percent = (f_just / f_equal − 1) × 100
```

It is not a percentage of an interval and it does not add the way cents add.
For small differences:

| Difference | Frequency difference |
|---|---|
| 1 cent | 0.0578% |
| 5 cents | 0.2892% |
| 10 cents | 0.5793% |
| 11.731 cents | 0.6799% |
| 13.686 cents | 0.7937% |
| 17.596 cents | 1.0216% |
| 20 cents | 1.1619% |
| 100 cents (one semitone) | 5.9463% |

Conversely, a 1% frequency change is about 17.23 cents. A cent is a
hundredth of a semitone; a 14-cent difference is about a seventh of a semitone
and is clearly audible, as the [Cent (music)](https://en.wikipedia.org/wiki/Cent_(music))
article notes for the just major third.

## 2. The app's twelve positions and sixteen names

The app uses **16 Carnatic swara names over 12 semitone positions**. Four
pairs share a position and ratio; the raga decides which name is used:

| Position | Names | Just ratio |
|---:|---|---|
| 0 | S | `1/1` |
| 1 | R1 | `16/15` |
| 2 | R2, G1 | `9/8` |
| 3 | R3, G2 | `6/5` |
| 4 | G3 | `5/4` |
| 5 | M1 | `4/3` |
| 6 | M2 | `45/32` |
| 7 | P | `3/2` |
| 8 | D1 | `8/5` |
| 9 | D2, N1 | `5/3` |
| 10 | D3, N2 | `9/5` |
| 11 | N3 | `15/8` |

In the app's data model the position is called `semitone`; the ratio is called
`ratio`. The `semitone` field is used for Equal mode and the nearest equal
position; the `ratio` field is used for Just mode. In both modes the sample
bank chooses the nearest 12-TET sample from the resulting target frequency.

This is a **12-position** model, not the 22-shruti grid. Carnatic theory
discusses 22 shrutis, but the 12 swarasthanas are the practical pitch classes,
and the number and exact ratios of the finer shrutis are debated. The app
deliberately implements one practical 5-limit realisation of the 12 positions;
it does not claim to encode every shruti or every regional interpretation. See
[Shruti (music)](https://en.wikipedia.org/wiki/Shruti_(music)) and
[Svara](https://en.wikipedia.org/wiki/Svara).

## 3. Exact comparison at Sa C3

The app's default Sa is C3. With A4 = 440 Hz,

`C3 = 440 × 2^((48 − 69)/12) = 130.8127826502993 Hz`.

At another Sa, multiply both frequency columns by `Sa / C3`; the ratios,
cents and percentages do not change.

### 3.1 Ratios, cents and frequency difference

Positive Δ means the just note is **higher (sharper)** than 12-TET.
Percentages are linear frequency differences, not cents.

| Pos. | Swara names | Just ratio | Just (cents) | 12-TET (cents) | Δ (cents) | Δ (frequency) | Western interval |
|---:|---|---:|---:|---:|---:|---:|---|
| 0 | S | `1/1` | 0.000 | 0 | +0.000 | +0.0000% | unison |
| 1 | R1 | `16/15` | 111.731 | 100 | +11.731 | +0.6799% | minor 2nd |
| 2 | R2 / G1 | `9/8` | 203.910 | 200 | +3.910 | +0.2261% | major 2nd |
| 3 | R3 / G2 | `6/5` | 315.641 | 300 | +15.641 | +0.9076% | minor 3rd |
| 4 | G3 | `5/4` | 386.314 | 400 | −13.686 | −0.7874% | major 3rd |
| 5 | M1 | `4/3` | 498.045 | 500 | −1.955 | −0.1129% | perfect 4th |
| 6 | M2 | `45/32` | 590.224 | 600 | −9.776 | −0.5631% | augmented 4th (tritone) |
| 7 | P | `3/2` | 701.955 | 700 | +1.955 | +0.1130% | perfect 5th |
| 8 | D1 | `8/5` | 813.686 | 800 | +13.686 | +0.7937% | minor 6th |
| 9 | D2 / N1 | `5/3` | 884.359 | 900 | −15.641 | −0.8994% | major 6th |
| 10 | D3 / N2 | `9/5` | 1017.596 | 1000 | +17.596 | +1.0216% | minor 7th |
| 11 | N3 | `15/8` | 1088.269 | 1100 | −11.731 | −0.6753% | major 7th |

Cents are rounded to 0.001; percentages to 0.0001%. The ratios are exact.
The largest absolute differences in the app's full 12-position set are
**+17.596 cents** (D3/N2, `9/5`) and **−15.641 cents** (R3/G2, `6/5`, and
D2/N1, `5/3`). The major third and minor sixth are the familiar
"about 14 cents" cases: G3 is −13.686 and D1 is +13.686.

The "Western interval" column is only an orientation. In particular, `M2` is
Carnatic prati madhyama (the augmented fourth at position 6), not a Western
major second.

### 3.2 Frequencies at Sa C3

| Pos. | Swara names | Just (Hz) | 12-TET (Hz) | Δ (Hz) | Keyboard note at Sa C3 |
|---:|---|---:|---:|---:|---|
| 0 | S | 130.813 | 130.813 | +0.000 | C3 |
| 1 | R1 | 139.534 | 138.591 | +0.942 | C♯3 |
| 2 | R2 / G1 | 147.164 | 146.832 | +0.332 | D3 |
| 3 | R3 / G2 | 156.975 | 155.563 | +1.412 | D♯3 |
| 4 | G3 | 163.516 | 164.814 | −1.298 | E3 |
| 5 | M1 | 174.417 | 174.614 | −0.197 | F3 |
| 6 | M2 | 183.955 | 184.997 | −1.042 | F♯3 |
| 7 | P | 196.219 | 195.998 | +0.221 | G3 |
| 8 | D1 | 209.300 | 207.652 | +1.648 | G♯3 |
| 9 | D2 / N1 | 218.021 | 220.000 | −1.979 | A3 |
| 10 | D3 / N2 | 235.463 | 233.082 | +2.381 | A♯3 |
| 11 | N3 | 245.274 | 246.942 | −1.668 | B3 |

The "keyboard note" is what a piano-keyboard name would call the sounding
pitch at Sa C3. It is **not** the Carnatic name: for example, Carnatic G3
sounds E3 above Sa C3. In sing-along, the app shows this Western name
separately from the swara name. The frequencies above are the theoretical
targets; the bundled samples add a sub-cent residual, measured in section 7.2.

### 3.3 The app's Mayamalavagowla subset

Mayamalavagowla, the raga of the varisais and alankarams, uses
S R1 G3 M1 P D1 N3. Its deviations are:

| Swara | Just ratio | Just (cents) | Δ vs 12-TET |
|---|---:|---:|---:|
| S | `1/1` | 0.000 | +0.000 |
| R1 | `16/15` | 111.731 | +11.731 |
| G3 | `5/4` | 386.314 | −13.686 |
| M1 | `4/3` | 498.045 | −1.955 |
| P | `3/2` | 701.955 | +1.955 |
| D1 | `8/5` | 813.686 | +13.686 |
| N3 | `15/8` | 1088.269 | −11.731 |

So "up to about 14 cents" is accurate **for Mayamalavagowla**: its worst
deviation is G3/D1 at 13.686 cents. The other positions in the table (R3/G2,
D2/N1 and D3/N2) have larger deviations (15.641 and 17.596 cents), so it is
not the maximum of the full table.

## 4. Why the steps are unequal

The app's just set is not twelve equal steps. Between adjacent positions the
intervals are:

| Step | Ratio | Size (cents) |
|---|---:|---:|
| S → R1 | `16/15` | 111.731 |
| R1 → R2 / G1 | `135/128` | 92.179 |
| R2 / G1 → R3 / G2 | `16/15` | 111.731 |
| R3 / G2 → G3 | `25/24` | 70.672 |
| G3 → M1 | `16/15` | 111.731 |
| M1 → M2 | `135/128` | 92.179 |
| M2 → P | `16/15` | 111.731 |
| P → D1 | `16/15` | 111.731 |
| D1 → D2 / N1 | `25/24` | 70.672 |
| D2 / N1 → D3 / N2 | `27/25` | 133.238 |
| D3 / N2 → N3 | `25/24` | 70.672 |
| N3 → S′ | `16/15` | 111.731 |

The smallest step is 70.672 cents and the largest 133.238 cents. This is the
trade-off: the pure intervals are pure, but a fixed 12-note layout cannot also
have equal steps and pure intervals in every key. Equal temperament spreads
the unavoidable comma across all twelve intervals, which is why its thirds
and sixths are the most noticeably different from just.

The perfect fifth (`3/2`, 701.955) and fourth (`4/3`, 498.045) are within about
2 cents of 12-TET; the octave is exact. That is why Just and Equal sound very
close on Sa–Pa and Sa–S′, but differ clearly on the third-based and sixth-based
swaras.

## 5. Worked examples

All at Sa C3. The formulas are in section 1; the sample rates are what
`HarmoniumBank` uses to reach the target from the nearest 12-TET sample.

### G3, the major third

- Just: `C3 × 5/4 = 130.8128 × 1.25 = 163.5160 Hz`.
- 12-TET: `C3 × 2^(4/12) = 164.8138 Hz`.
- Cents: `1200 × log2(163.5160 / 164.8138) = −13.686`.
- Frequency: `(163.5160 / 164.8138 − 1) × 100 = −0.7874%`.
- Nearest sample: E3, 164.8138 Hz; rate `163.5160 / 164.8138 = 0.992126`.
  The sample is played slightly slower, lowering it by 13.69 cents.

### P, the perfect fifth

- Just: `C3 × 3/2 = 196.2192 Hz`.
- 12-TET: `195.9977 Hz`.
- Cents: `+1.955`; frequency `+0.1130%`.
- Sample: G3, 195.9977 Hz; rate `1.001130`. The difference is small but not
  zero.

### D3 / N2, the largest deviation

- Just: `C3 × 9/5 = 235.4630 Hz`.
- 12-TET: `233.0819 Hz`.
- Cents: `+17.596`; frequency `+1.0216%`.
- Sample: A♯3, 233.0819 Hz; rate `1.010216`.

### M2, the tritone

- Just: `C3 × 45/32 = 183.9555 Hz`.
- 12-TET: `184.9972 Hz`.
- Cents: `−9.776`; frequency `−0.5631%`.
- Sample: F♯3; rate `0.994369`.

## 6. Research context: shrutis, tanpura and keyboards

Carnatic music is melodic and drone-anchored: the tanpura sustains Sa and Pa
(the app's sample is the traditional pa–SA–SA–sa), and the singer tunes
intervals against that reference. The seven svaras, the twelve swarasthanas
and the sixteen names are the working pitch framework; the 22 shrutis are a
finer theoretical grid, not a single agreed 22-note keyboard. The number and
exact values of the shrutis are debated, and gamakas mean that many performed
pitches are approached or ornamented rather than fixed. See
[Shruti (music)](https://en.wikipedia.org/wiki/Shruti_(music)),
[Svara](https://en.wikipedia.org/wiki/Svara) and
[Tanpura](https://en.wikipedia.org/wiki/Tanpura). Dr Vidyadhar Oke's
[22shruti.com](https://22shruti.com/) is a well-known practical exploration of
the 22-shruti approach.

The app takes a deliberately narrower position:

- It uses one 5-limit ratio per semitone position. The ratios are the common
  just major/minor-scale ratios, not a claim about all 22 shrutis.
- It does not model gamakas or microtonal shruti distinctions. A plain
  harmonium note is the reference; sing-along can mark a gamaka as high or low
  even when it was sung correctly, which is why the guide recommends Gentle
  strictness for gitams and varnams.
- It keeps a real tanpura recording for the drone. The recording is not
  retuned to equal temperament; only its root is shifted to the chosen Sa.
- It offers Equal mode for the practical case where the student is practising
  with a keyboard, a tuner or another fixed-pitch instrument. Equal mode is a
  compatibility choice, not a claim that 12-TET is the correct Carnatic tuning.

A 5-limit set has choices. The app uses `45/32` for M2 and `9/5` for D3/N2;
other 5-limit tables may use, for example, `64/45` or `16/9` for some positions.
This table is the app's set, not "the" just intonation.

## 7. How it is implemented here

### 7.1 Ratios and frequencies

`Swara` stores, for each name, a `semitone` position and an exact
`numerator`/`denominator` ratio:

```kotlin
enum class Swara(val symbol: String, val semitone: Int, val numerator: Int, val denominator: Int) {
    S("S", 0, 1, 1),
    R1("R1", 1, 16, 15),
    // ...
    N3("N3", 11, 15, 8);

    val ratio: Double get() = numerator.toDouble() / denominator
}
```

`Note.frequency(saHz)` applies the ratio and the octave factor (0.5 for mandra,
1 for madhya, 2 for tara):

```kotlin
fun frequency(saHz: Double): Double = saHz * swara.ratio * octaveFactor
```

This is the Just path. `Tuning.EQUAL` bypasses `ratio` and uses the semitone:

```kotlin
enum class Tuning {
    JUST, EQUAL;

    fun frequency(note: Note, saHz: Double): Double = when (this) {
        JUST -> note.frequency(saHz)
        EQUAL -> saHz * 2.0.pow(note.swara.semitone / 12.0 + note.octave.offset)
    }
}
```

`PerformanceSettings` carries Sa, tempo, kala and `Tuning`. `Schedule` calls
`settings.tuning.frequency` once per note when it builds the performance, so
the choice changes the scheduled pitch, not the timing. See
[`Swara.kt`](../shared/src/commonMain/kotlin/com/baijum/purandara/domain/Swara.kt),
[`Tuning.kt`](../shared/src/commonMain/kotlin/com/baijum/purandara/domain/Tuning.kt)
and
[`Schedule.kt`](../shared/src/commonMain/kotlin/com/baijum/purandara/domain/schedule/Schedule.kt).

### 7.2 Harmonium samples

The bundled harmonium samples are prepared for exact 12-TET at A4 = 440 Hz,
one per semitone from C2 to D5. `HarmoniumBank.playbackFor(frequencyHz)` picks
the nearest sample and a playback rate:

```
rate = targetHz / sampleHz
```

The rate stays within half a semitone of 1.0, so the timbre does not change
noticeably. In Equal mode, with Sa exactly on a keyboard note (fine tune 0),
every note lands on a sample, so `rate == 1.0`; the test
`equalTuningLandsExactlyOnSamples` asserts this. The sample's own residual
error remains (see the measurement below). A fine-tuned Sa shifts every target
frequency by the same amount; the nearest sample is chosen for the new target,
and the rate is the offset from that sample (still within ±50 cents). In Just
mode the nearest sample is played a few cents fast or slow: G3 at C3 is the E3
sample at rate 0.992126, which is −13.686 cents.

The samples themselves are prepared by `tools/samples/prepare_harmonium.py`,
which pitch-corrects the source pack to exact 12-TET, checks each file is
within 1 cent of its target and normalises it. The app does not store
separate just and equal sample sets; it shifts one 12-TET bank. See
[`HarmoniumBank.kt`](../shared/src/commonMain/kotlin/com/baijum/purandara/domain/HarmoniumBank.kt)
and
[`prepare_harmonium.py`](../tools/samples/prepare_harmonium.py).

**Measured on the bundled files:** all 39 are 44.1 kHz mono, 2.500 s. Using
parselmouth's median F0 over 0.20–2.20 s, the largest error from the nominal
12-TET pitch is **+0.507 cents** (MIDI 39, 77.78 Hz); the mean is −0.025 cent
and every file is within 0.51 cent. The preparation script's 1-cent tolerance
is therefore not being used in practice. "Exact 12-TET" is the design target;
the audio has sub-cent residuals, and Equal mode inherits them because it
plays the samples at rate 1.0.

**Model vs delivered at Sa C3, fine tune 0, Mayamalavagowla.** "Target" is the
mathematically correct value from the selected tuning; "delivered" combines
the measured sample pitch with the nominal playback rate chosen by
`HarmoniumBank`. This is illustrative: another Sa or fine tune selects
different samples.

| Swara | Just target (Hz) | Just delivered (Hz) | Just Δ (¢) | Equal target (Hz) | Equal delivered (Hz) | Equal Δ (¢) |
|---|---:|---:|---:|---:|---:|---:|
| S | 130.813 | 130.807 | −0.08 | 130.813 | 130.807 | −0.08 |
| R1 | 139.534 | 139.528 | −0.07 | 138.591 | 138.586 | −0.07 |
| G3 | 163.516 | 163.501 | −0.16 | 164.814 | 164.799 | −0.16 |
| M1 | 174.417 | 174.413 | −0.04 | 174.614 | 174.610 | −0.04 |
| P | 196.219 | 196.207 | −0.11 | 195.998 | 195.986 | −0.11 |
| D1 | 209.300 | 209.299 | −0.01 | 207.652 | 207.651 | −0.01 |
| N3 | 245.274 | 245.270 | −0.03 | 246.942 | 246.937 | −0.03 |

The residual is below 0.2 cent for these seven notes; the full bank's worst
case is 0.507 cent. The tuning model itself is exact in cents; the residual is
the sample realisation.

### 7.3 Tanpura

The tanpura is a recording of pa SA SA sa, not a synthesised equal-tempered
drone. `TanpuraBank` stores four loops (A♯2, D3, F♯3, A♯3), each pitch-shifted
to target an exact 12-TET root by `prepare_tanpura.py`; the app picks the
nearest loop and a rate within ±3 semitones to reach the student's Sa. The
preparation script checks each loop's measured Sa is within 3 cents of its
target.

**Measured on the bundled files:** all four loops are 44.1 kHz mono, 18.150 s.
A long-term-spectrum peak within a semitone of the target gives:

| File | Target | Measured root | Error |
|---|---:|---:|---:|
| `46.flac` (A♯2) | 116.541 Hz | 116.569 Hz | +0.415 c |
| `50.flac` (D3) | 146.832 Hz | 146.870 Hz | +0.442 c |
| `54.flac` (F♯3) | 184.997 Hz | 185.051 Hz | +0.504 c |
| `58.flac` (A♯3) | 233.082 Hz | 233.176 Hz | +0.699 c |

All four are within 0.7 cent of their nominal root. A spectral peak near 1.5×
the measured root, which is where the Pa string lies, measures within about
0.6 cent of a 3/2 fifth in these loops. That is a long-term spectral check, not
a per-string pitch measurement; the recording still keeps its own natural
intervals rather than being rebuilt as 12-TET.

`TanpuraBank.playbackFor(saHz)` has no `Tuning` parameter. Switching Just to
Equal therefore changes the harmonium and the pitch targets, **not the
tanpura**. The recording keeps its own natural intervals (its Pa is close to,
but not mathematically guaranteed to be, a 3/2 fifth), while its root follows
the chosen Sa. This is the intended hybrid: a just/natural drone with either a
just or a 12-TET harmonium. At Equal, the fifth of the drone is close enough to
the equal fifth (a just fifth is only 1.955 cents wider than 12-TET) that the
combination works; the app does not claim an exactly equal-tempered drone.

The tanpura also follows fine tune: both the Sa used by `TanpuraBank` and the
harmonium's target frequencies come from `Shruti.saHz(midi, cents)`. See
[`TanpuraBank.kt`](../shared/src/commonMain/kotlin/com/baijum/purandara/domain/TanpuraBank.kt)
and
[`prepare_tanpura.py`](../tools/samples/prepare_tanpura.py).

### 7.4 Sing-along and Shruti practice

`SwaraMapper` uses the selected `Tuning` for the target pitch:

```kotlin
fun targetHz(swara: Swara, octave: Int): Double {
    val madhya = when (tuning) {
        JUST -> saHz * swara.ratio
        EQUAL -> saHz * 2.0.pow(swara.semitone / 12.0)
    }
    return madhya * 2.0.pow(octave)
}
```

The sing-along and Shruti screens compare the detected frequency with that
target using `Pitch.cents(target, frequency)`. In Just mode `SwaraMapper` finds
the nearest of all twelve just targets (not the nearest equal semitone first),
which avoids naming a sharp R1 as R2. In Equal mode it uses the nearest
semitone. Strictness then sets the in-tune/close width around the target;
tuning does not change the width. See
[`SwaraMapper.kt`](../shared/src/commonMain/kotlin/com/baijum/purandara/singalong/SwaraMapper.kt),
[`Pitch.kt`](../shared/src/commonMain/kotlin/com/baijum/purandara/domain/Pitch.kt)
and the [Practice settings guide](../site/pages/guide/settings.html#strictness).

### 7.5 Settings and persistence

The setting is called **Tuning** in the app, with choices **Just (tanpura)** and
**Equal (keyboard)**. On Android it is the Tuning segmented control in the
player's settings sheet; on iPhone and iPad it is under **Sound → Tuning**. It
is stored in `PracticeSettings`:

- Android: `app/src/main/java/com/baijum/purandara/data/PracticePreferences.kt`
- iOS: `iosApp/Purandara/Models/PracticeSettings.swift`
- Android UI: `app/src/main/java/com/baijum/purandara/ui/player/PlayerScreen.kt`
- iOS UI: `iosApp/Purandara/Views/PlayerView.swift` (Sound → Tuning)

The default is `Tuning.JUST`. The app offers Sa G2–C4 (default C3) and a
±50-cent fine tune. The same setting is used by the player, sing-along and
Shruti practice. While playing, changing it restarts playback because the
scheduled pitches change. `Shruti.saHz(midi, cents)` applies the fine tune to
both tunings.

### 7.6 Tests that pin the behaviour

- `RagaTalaTuningTest.equalTemperamentMatchesKeyboardPitches` checks equal
  C3/MIDI frequencies.
- `RagaTalaTuningTest.justAndEqualAgreeOnSaAndDifferOnGa` checks the G3
  difference is −13.69 cents.
- `SwaraTest.mayamalavagowlaFrequencies` checks the exact ratios for
  Mayamalavagowla.
- `SwaraTest.justRatiosStayCloseToEqualTemperament` checks all ratios are
  within 20 cents of 12-TET.
- `HarmoniumBankTest.justIntonationNudgesNearestSample` checks the G3 sample
  and −13.69-cent rate.
- `ScheduleTest.equalTuningLandsExactlyOnSamples` checks Equal mode uses
  rate 1.0 for every note.
- `ScheduleTest.everyExercisePlayableForEverySaItAllowsAndBothTunings` checks
  every exercise with a playable Sa, both tunings and the ±50-cent fine tune
  stay within the sample bank.

## 8. Choosing between them

**Use Just (the default)** when practising with the tanpura and your voice.
It is the sound the app is modelled on: pure octaves, fifths and fourths, and
pure thirds and sixths. The trade-off is that those thirds and sixths are not
the keyboard's 400/900 cents.

**Use Equal** when you are practising with a keyboard, a digital piano, a
tuner or another fixed-pitch instrument. Every semitone is exactly 100 cents;
with Sa exactly on a keyboard note, every note lands on a 12-TET sample at
normal speed. The tanpura is still the recorded just/natural drone; its Sa is
exact, its Pa is a couple of cents from the equal fifth. That is normal for a
tanpura and is not a defect.

**If you are unsure**, use Just and fine-tune Sa to your teacher's shruti box
or recording. Fine tune moves Sa, not the ratios, so the comparison table
still applies. If your teacher's reference is a keyboard, use Equal and fine
tune if needed.

Neither choice changes the notation, tala, tempo, kala, count-in, repeat or
scoring thresholds. It changes the sounding pitch of the harmonium and the
pitch the app expects from you.

## 9. Reproducing the numbers

The table is generated from the ratio list; this standard-library Python
reproduces it. It uses Sa C3.

```python
from math import log2

SA = 440.0 * 2 ** ((48 - 69) / 12)          # C3, A4 = 440
ROWS = [
    ("S", 0, 1, 1), ("R1", 1, 16, 15), ("R2 / G1", 2, 9, 8),
    ("R3 / G2", 3, 6, 5), ("G3", 4, 5, 4), ("M1", 5, 4, 3),
    ("M2", 6, 45, 32), ("P", 7, 3, 2), ("D1", 8, 8, 5),
    ("D2 / N1", 9, 5, 3), ("D3 / N2", 10, 9, 5), ("N3", 11, 15, 8),
]

for name, semitone, n, d in ROWS:
    ratio = n / d
    just_cents = 1200 * log2(ratio)
    equal_cents = 100 * semitone
    equal_ratio = 2 ** (semitone / 12)
    print(
        f"{name:10s} {n:2d}/{d:<2d} "
        f"just {just_cents:9.3f} c  "
        f"et {equal_cents:6.1f} c  "
        f"delta {just_cents - equal_cents:+8.3f} c  "
        f"freq {(ratio / equal_ratio - 1) * 100:+8.4f}%  "
        f"{SA * ratio:9.3f} Hz"
    )
```

`./gradlew :shared:jvmTest` runs the tuning tests. A change to `Swara.kt` or
`Tuning.kt` should update this document and the expected numbers in those
tests.

## Sources

- [Just intonation](https://en.wikipedia.org/wiki/Just_intonation) — pure
  intervals, small whole-number ratios, 5-limit.
- [Five-limit tuning](https://en.wikipedia.org/wiki/Five-limit_tuning) — the
  ratio table and the 2/3/5 construction.
- [Equal temperament](https://en.wikipedia.org/wiki/Equal_temperament) —
  12 equal semitones, the twelfth root of 2, and why thirds/sixths deviate
  more than fourths/fifths.
- [Cent (music)](https://en.wikipedia.org/wiki/Cent_(music)) — the cent
  formula and the 5:4 / 400-cent comparison.
- [Shruti (music)](https://en.wikipedia.org/wiki/Shruti_(music)) — the
  22-shruti framework, its ambiguity, and the 12 universal pitch classes.
- [Svara](https://en.wikipedia.org/wiki/Svara) — seven svaras, the twelve
  swarasthanas, and Sa/Pa as anchors.
- [Tanpura](https://en.wikipedia.org/wiki/Tanpura) — the drone instrument and
  its Sa/Pa tuning.
- [22shruti.com](https://22shruti.com/) — Dr Vidyadhar Oke's practical
  exploration of the 22 shrutis.
- [A440](https://en.wikipedia.org/wiki/A440_(pitch_standard)) — the concert
  pitch used for the samples.

Further reading (from [`resources.md`](resources.md)): Helmholtz, *On the
Sensations of Tone*; Benade, *Fundamentals of Musical Acoustics*; Sethares,
*Tuning, Timbre, Spectrum, Scale*; Barbour, *Tuning and Temperament*; Mathieu,
*Harmonic Experience*; Doty, *The Just Intonation Primer*.
