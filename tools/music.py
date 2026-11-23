"""Composes and synthesises the capture minigame music: a looping capture theme and a short victory jingle.

Everything here is original: the melodies are written out below and the instruments are synthesised from scratch
(pulse waves, additive saws, FM bells, noise drums), so no samples or existing music are involved.

    python3 tools/music.py                 # writes the chosen tracks into the mod's assets as .ogg
    python3 tools/music.py previews DIR    # renders every option (loops played twice) as .ogg/.mp3 into DIR

Needs numpy and ffmpeg (with libvorbis). Run from the repository root.
"""
import os
import subprocess
import sys

import numpy as np

SR = 44100
SOUNDS = "src/main/resources/assets/cobblemonranger/sounds/music"

NOTE_INDEX = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(name):
    """'C#5' -> 73, 'Bb4' -> 70."""
    n = NOTE_INDEX[name[0]]
    rest = name[1:]
    while rest and rest[0] in "#b":
        n += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return n + 12 * (int(rest) + 1)


def freq(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def parse(line):
    """'A4:0.5 D5:1 r:0.5' -> [(midi or None, beats)]. Bars are separated by '|' and each must add up to 4 beats."""
    out = []
    for bar in line.split("|"):
        bar = bar.strip()
        if not bar:
            continue
        total = 0
        for tok in bar.split():
            name, beats = tok.split(":")
            beats = float(beats)
            total += beats
            out.append((None if name == "r" else midi(name), beats))
        if abs(total - 4) > 1e-6:
            raise ValueError(f"bar adds up to {total} beats: {bar}")
    return out


# ---------------------------------------------------------------- instruments

def env_adsr(n, a, d, s, r_len, sr=SR):
    """Attack/decay/sustain over n samples, then a release tail of r_len seconds appended."""
    a_n, d_n, r_n = int(a * sr), int(d * sr), int(r_len * sr)
    e = np.full(n + r_n, s, dtype=np.float64)
    a_n = max(1, min(a_n, n))
    e[:a_n] = np.linspace(0, 1, a_n, endpoint=False)
    d_end = min(n, a_n + d_n)
    if d_end > a_n:
        e[a_n:d_end] = np.linspace(1, s, d_end - a_n, endpoint=False)
    level = e[n - 1] if n > 0 else s
    e[n:] = level * np.linspace(1, 0, r_n) ** 2 if r_n else e[n:]
    return e


def vibrato(t, f, depth=0.006, rate=5.5, delay=0.18):
    ramp = np.clip((t - delay) / 0.25, 0, 1)
    return f * (1 + depth * ramp * np.sin(2 * np.pi * rate * t))


def phase_of(fr):
    return np.cumsum(fr) / SR


def pulse(f, dur, duty=0.25, vib=True, a=0.005, d=0.08, s=0.7, r=0.06):
    n = int(dur * SR)
    e = env_adsr(n, a, d, s, r)
    t = np.arange(len(e)) / SR
    fr = vibrato(t, f) if vib else np.full(len(e), f)
    ph = phase_of(fr) % 1.0
    dt = fr / SR
    sq = np.where(ph < duty, 1.0, -1.0) + poly_blep(ph, dt) - poly_blep((ph - duty) % 1.0, dt)
    return sq * e * 0.5


def poly_blep(t, dt):
    """Smooths the jumps of a naive pulse wave so high notes don't alias into a harsh fizz."""
    out = np.zeros_like(t)
    lo = t < dt
    x = t[lo] / dt[lo]
    out[lo] = x + x - x * x - 1
    hi = t > 1 - dt
    x = (t[hi] - 1) / dt[hi]
    out[hi] = x * x + x + x + 1
    return out


def triangle(f, dur, a=0.003, d=0.05, s=0.85, r=0.03):
    n = int(dur * SR)
    e = env_adsr(n, a, d, s, r)
    t = np.arange(len(e)) / SR
    ph = (f * t) % 1.0
    # NES-like stepped triangle: 16 levels
    tri = 4 * np.abs(ph - 0.5) - 1
    tri = np.round(tri * 7.5) / 7.5
    return tri * e * 0.7


def additive_saw(f, t, bright, detune=0.0):
    """Band-limited saw; bright (array or float) scales how many harmonics come through."""
    out = np.zeros_like(t)
    kmax = int(min(40, 11000 / f))
    fr = vibrato(t, f * (1 + detune), depth=0.004, rate=5.0, delay=0.3)
    ph = 2 * np.pi * phase_of(fr)
    for k in range(1, kmax + 1):
        amp = (1.0 / k) * np.exp(-(k - 1) / np.maximum(bright, 0.3))
        out += amp * np.sin(k * ph)
    return out


def brass(f, dur, r=0.12):
    n = int(dur * SR)
    e = env_adsr(n, 0.03, 0.15, 0.8, r)
    t = np.arange(len(e)) / SR
    bright = 2 + 9 * np.clip(t / 0.06, 0, 1) * (0.75 + 0.25 * np.exp(-t * 3))
    return additive_saw(f, t, bright) * e * 0.45


def strings(f, dur, r=0.35, a=0.12):
    n = int(dur * SR)
    e = env_adsr(n, a, 0.2, 0.85, r)
    t = np.arange(len(e)) / SR
    sig = sum(additive_saw(f, t, 5, det) for det in (-0.004, 0.0, 0.005)) / 3
    return sig * e * 0.4


def bell(f, dur, ratio=3.5, index=2.2, decay=2.6):
    n = int((dur + 1.2) * SR)
    t = np.arange(n) / SR
    env = np.exp(-t * decay) * np.clip(t / 0.002, 0, 1)
    mod = index * np.exp(-t * 4) * np.sin(2 * np.pi * f * ratio * t)
    return np.sin(2 * np.pi * f * t + mod) * env * 0.35


def pluck(f, dur):
    """Short square-ish pluck for arpeggios."""
    return pulse(f, min(dur, 0.12), duty=0.125, vib=False, a=0.002, d=0.06, s=0.25, r=0.04) * 0.6


RNG = np.random.default_rng(7)
NOISE = RNG.uniform(-1, 1, SR * 2)


def drum(kind):
    if kind == "k":  # kick: pitch-dropping sine
        n = int(0.22 * SR)
        t = np.arange(n) / SR
        f = 45 + 120 * np.exp(-t * 35)
        return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 14) * 0.9
    if kind == "s":  # snare: noise + body
        n = int(0.18 * SR)
        t = np.arange(n) / SR
        body = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30) * 0.4
        return (NOISE[:n] * np.exp(-t * 20) * 0.5 + body) * 0.8
    if kind == "h":  # closed hat: bright noise
        n = int(0.05 * SR)
        t = np.arange(n) / SR
        hp = np.diff(NOISE[: n + 1])
        return hp * np.exp(-t * 80) * 0.18
    if kind == "o":  # open hat
        n = int(0.22 * SR)
        t = np.arange(n) / SR
        hp = np.diff(NOISE[: n + 1])
        return hp * np.exp(-t * 14) * 0.13
    if kind == "t":  # low tom / timpani-ish
        n = int(0.5 * SR)
        t = np.arange(n) / SR
        f = 70 + 40 * np.exp(-t * 12)
        return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6) * 0.7
    if kind == "c":  # crash
        n = int(1.4 * SR)
        t = np.arange(n) / SR
        hp = np.diff(np.resize(NOISE, n + 1))
        return hp * np.exp(-t * 2.8) * 0.16
    raise ValueError(kind)


# ---------------------------------------------------------------- effects

def lowpass(x, cutoff):
    """One-pole low-pass, applied as an FFT convolution with its (truncated) impulse response."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    ir = (1 - a) * a ** np.arange(int(min(SR * 0.05, 5 / (1 - a) + 1)))
    return fft_convolve(x, ir)[: len(x)]


def fft_convolve(x, h):
    n = len(x) + len(h) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(h, size), size)[:n]


def comb(x, delay, g):
    y = x.copy()
    for start in range(delay, len(y), delay):
        end = min(start + delay, len(y))
        y[start:end] += g * y[start - delay:end - delay]
    return y


def allpass(x, delay, g):
    # y[n] = -g x[n] + x[n-D] + g y[n-D]
    y = -g * x
    y[delay:] += x[:-delay]
    for start in range(delay, len(y), delay):
        end = min(start + delay, len(y))
        y[start:end] += g * y[start - delay:end - delay]
    return y


def reverb(x, wet=0.18, size=1.0):
    combs = [1557, 1617, 1491, 1422]
    out = sum(comb(x, int(d * size), 0.78) for d in combs) / len(combs)
    for d in (225, 556, 441):
        out = allpass(out, d, 0.5)
    out = lowpass(out, 5000)
    return x * (1 - wet) + out * wet * 1.6


# ---------------------------------------------------------------- arrangement

class Song:
    def __init__(self, bpm, bars, beats_per_bar=4):
        self.bpm = bpm
        self.spb = 60.0 / bpm
        self.length = int(round(bars * beats_per_bar * self.spb * SR))
        tail = int(3 * SR)
        self.buses = {}
        self.size = self.length + tail

    def bus(self, name):
        if name not in self.buses:
            self.buses[name] = np.zeros(self.size)
        return self.buses[name]

    def place(self, bus, sig, beat, gain=1.0):
        i = int(round(beat * self.spb * SR))
        b = self.bus(bus)
        if i >= len(b):
            return
        end = min(len(b), i + len(sig))
        b[i:end] += sig[: end - i] * gain

    def melody(self, bus, notes, inst, gain=1.0, start=0.0, legato=0.92, transpose=0):
        beat = start
        for m, beats in notes:
            if m is not None:
                self.place(bus, inst(freq(m + transpose), beats * self.spb * legato), beat, gain)
            beat += beats

    def drums(self, bus, pattern, bars, gain=1.0, start_bar=0, step=0.5):
        """pattern: per-bar strings of hits, e.g. 'k.h.s.h.' one char per step (default eighth notes).
        Several voices can be stacked with '+': 'k...k...+..s...s.'"""
        for bar in range(bars):
            for voice in pattern.split("+"):
                for i, ch in enumerate(voice):
                    if ch != ".":
                        self.place(bus, drum(ch), (start_bar + bar) * 4 + i * step, gain)

    def mix(self, levels, loop=True, verb=0.16, pans=None):
        pans = pans or {}
        left = np.zeros(self.size)
        right = np.zeros(self.size)
        for name, sig in self.buses.items():
            g = levels.get(name, 1.0)
            p = pans.get(name, 0.0)
            wet = reverb(sig, verb) if name not in ("drums", "bass") else sig
            left += wet * g * (1 - max(0, p))
            right += wet * g * (1 + min(0, p))
        out = np.stack([left, right])
        if loop:
            tail = out[:, self.length:]
            out = out[:, : self.length].copy()
            out[:, : tail.shape[1]] += tail
        else:  # drop the silent end of the reverb tail
            loud = np.nonzero(np.max(np.abs(out), axis=0) > 1e-3 * np.max(np.abs(out)))[0]
            out = out[:, : loud[-1] + 1]
            out[:, -2000:] *= np.linspace(1, 0, 2000)
        out = np.tanh(out * 1.1) / 1.1  # soft clip
        # level by RMS (with a peak ceiling) so every option plays at about the same loudness
        out *= 0.16 / (np.sqrt(np.mean(out ** 2)) + 1e-9)
        return out * min(1.0, 0.89 / np.max(np.abs(out)))


def chord_tones(root, quality):
    r = midi(root)
    return {"maj": [r, r + 4, r + 7], "min": [r, r + 3, r + 7], "sus": [r, r + 5, r + 7]}[quality]


def chord_list(spec):
    """'D:maj G:maj ...' one chord per bar, or 'Em/A' style split half bars written as 'E:min,A:maj'."""
    bars = []
    for tok in spec.split():
        bars.append([tuple(c.split(":")) for c in tok.split(",")])
    return bars


def arpeggio(song, bus, chords, inst, octave_shift=12, pattern=(0, 1, 2, 1), step=0.25, gain=1.0):
    for bi, bar in enumerate(chords):
        per = 4 / len(bar)
        for ci, (root, q) in enumerate(bar):
            tones = [m + octave_shift for m in chord_tones(root + "4", q)]
            steps = int(per / step)
            for s in range(steps):
                m = tones[pattern[s % len(pattern)]]
                song.place(bus, inst(freq(m), step * song.spb), bi * 4 + ci * per + s * step, gain)


def bassline(song, bus, chords, inst, style="octave", gain=1.0, octave=2):
    for bi, bar in enumerate(chords):
        per = 4 / len(bar)
        for ci, (root, q) in enumerate(bar):
            r = midi(root + str(octave))
            beat0 = bi * 4 + ci * per
            if style == "octave":  # chip bounce
                for s in range(int(per * 2)):
                    m = r + (12 if s % 2 else 0)
                    song.place(bus, inst(freq(m), 0.5 * song.spb * 0.85), beat0 + s * 0.5, gain)
            elif style == "drive":  # straight eighths, accent on the beat
                for s in range(int(per * 2)):
                    song.place(bus, inst(freq(r), 0.5 * song.spb * 0.7), beat0 + s * 0.5, gain * (1 if s % 2 == 0 else 0.7))
            elif style == "march":  # root - fifth on beats
                for s in range(int(per)):
                    m = r + (7 if s % 2 else 0)
                    song.place(bus, inst(freq(m), song.spb * 0.9), beat0 + s, gain)


def pad(song, bus, chords, inst, gain=1.0, octave=4):
    for bi, bar in enumerate(chords):
        per = 4 / len(bar)
        for ci, (root, q) in enumerate(bar):
            for m in chord_tones(root + str(octave), q):
                song.place(bus, inst(freq(m), per * song.spb), bi * 4 + ci * per, gain / 3)


# ---------------------------------------------------------------- the compositions

def capture_a():
    """'Trailblazer': bright chiptune, D major, 152 bpm. Pulse lead, bouncing triangle bass, plucked arpeggios."""
    s = Song(152, 16)
    lead = parse(
        "A4:0.5 D5:0.5 F#5:1 E5:0.5 D5:0.5 A5:1 | G5:1.5 F#5:0.5 E5:1 D5:1 |"
        "F#5:0.5 F#5:0.5 B5:1 A5:0.5 F#5:0.5 D5:1 | E5:2.5 C#5:0.5 D5:0.5 E5:0.5 |"
        "F#5:0.5 A5:0.5 D6:1 C#6:0.5 B5:0.5 A5:1 | B5:1.5 A5:0.5 G5:1 B5:1 |"
        "A5:1 G5:0.5 F#5:0.5 E5:1 C#5:1 | D5:3 r:1 |"
        "r:0.5 B4:0.5 D5:0.5 G5:0.5 F#5:1 G5:1 | A5:1.5 E5:0.5 E5:2 |"
        "r:0.5 C#5:0.5 F#5:0.5 A5:0.5 C#6:1 B5:1 | A5:1 F#5:1 D5:1 F#5:1 |"
        "G5:0.5 A5:0.5 B5:1 D6:1 B5:1 | C#6:1.5 B5:0.5 A5:2 |"
        "G5:0.5 E5:0.5 C5:0.5 E5:0.5 G5:1 C6:1 | A5:1 B5:0.5 C#6:0.5 E6:2")
    chords = chord_list("D:maj G:maj B:min A:maj D:maj G:maj E:min,A:maj D:maj "
                        "G:maj A:maj F#:min B:min G:maj A:maj C:maj A:maj")
    s.melody("lead", lead, lambda f, d: pulse(f, d, duty=0.25))
    # echo of the lead, an octave down and an eighth late, quieter: the classic handheld "fake reverb"
    s.melody("echo", lead, lambda f, d: pulse(f, d, duty=0.5, vib=False), start=0.5, transpose=-12)
    arpeggio(s, "arp", chords, pluck, octave_shift=0, pattern=(0, 1, 2, 1, 2, 1, 0, 1))
    bassline(s, "bass", chords, triangle, "octave")
    s.drums("drums", "k..k..k.+..s...s.+hhhhhhhh", 16, step=0.5)
    s.place("drums", drum("c"), 0, 0.8)
    s.place("drums", drum("c"), 32, 0.8)
    return s.mix({"lead": 0.55, "echo": 0.16, "arp": 0.22, "bass": 0.55, "drums": 0.55},
                 verb=0.12, pans={"echo": 0.4, "arp": -0.3})


def capture_b():
    """'Wild Encounter': tense and driving, E minor, 168 bpm. Synth brass lead, string stabs, galloping bass."""
    s = Song(168, 16)
    lead = parse(
        "E5:0.75 E5:0.75 G5:0.5 B5:1 A5:0.5 G5:0.5 | F#5:0.5 G5:0.5 F#5:0.5 E5:0.5 B4:2 |"
        "C5:0.75 E5:0.75 G5:0.5 C6:1 B5:0.5 A5:0.5 | B5:1 A5:0.5 F#5:0.5 D5:2 |"
        "E5:0.75 E5:0.75 G5:0.5 B5:1 D6:0.5 E6:0.5 | D6:0.5 B5:0.5 A5:0.5 G5:0.5 F#5:1 G5:1 |"
        "E5:0.75 G5:0.75 C6:0.5 E6:1 D6:0.5 C6:0.5 | B5:1.5 A5:0.5 F#5:0.5 D#5:0.5 B4:1 |"
        "A5:2 C6:1 B5:0.5 A5:0.5 | G5:1.5 B5:0.5 E5:2 |"
        "E5:0.5 F#5:0.5 G5:0.5 A5:0.5 B5:1 C6:1 | D6:1.5 C6:0.5 B5:0.5 A5:0.5 F#5:1 |"
        "A5:0.5 C6:0.5 E6:1 D6:0.5 C6:0.5 B5:1 | D#6:1.5 B5:0.5 F#5:2 |"
        "E6:0.5 D6:0.5 C6:0.5 B5:0.5 A5:0.5 G5:0.5 F#5:0.5 E5:0.5 | D#5:0.5 F#5:0.5 B5:0.5 D#6:0.5 F#6:2")
    chords = chord_list("E:min E:min C:maj D:maj E:min E:min C:maj B:maj "
                        "A:min E:min C:maj D:maj A:min B:maj C:maj B:maj")
    s.melody("lead", lead, brass, legato=0.95)
    s.melody("double", lead, lambda f, d: pulse(f, d, duty=0.125), transpose=-12)
    # off-beat string stabs
    for bi, bar in enumerate(chords):
        root, q = bar[0]
        for off in (0.5, 1.5, 2.5, 3.5):
            for m in chord_tones(root + "4", q):
                s.place("stabs", strings(freq(m), 0.18 * s.spb, r=0.08, a=0.01), bi * 4 + off, 1 / 3)
    for bi, bar in enumerate(chords):  # gallop: 8th + two 16ths
        r = midi(bar[0][0] + "2")
        for beat in range(4):
            for off, ln in ((0, 0.5), (0.5, 0.25), (0.75, 0.25)):
                s.place("bass", pulse(freq(r), ln * s.spb * 0.8, duty=0.5, vib=False, s=0.9, r=0.02),
                        bi * 4 + beat + off, 0.8 if off == 0 else 0.55)
    s.drums("drums", "k.k.k.kk+..s...s.+h.h.h.ho", 16, step=0.5)
    for b in (0, 32):
        s.place("drums", drum("c"), b, 0.7)
    for beat in (30, 30.5, 31, 31.5, 62, 62.5, 63, 63.5):
        s.place("drums", drum("t"), beat, 0.6)
    return s.mix({"lead": 0.5, "double": 0.12, "stabs": 0.3, "bass": 0.32, "drums": 0.55},
                 verb=0.14, pans={"double": 0.35, "stabs": -0.25})


def capture_c():
    """'Sky Ridge': heroic and sweeping, F major, 132 bpm. Brass lead over strings, bell counter-arpeggios, toms."""
    s = Song(132, 16)
    lead = parse(
        "C5:1.5 F5:0.5 A5:2 | G5:1 F5:0.5 E5:0.5 G5:2 |"
        "F5:1.5 A5:0.5 D6:1 C6:1 | Bb5:2 A5:1 G5:1 |"
        "A5:1.5 C6:0.5 F6:2 | E6:1 D6:0.5 C6:0.5 G5:2 |"
        "Bb5:1 A5:0.5 G5:0.5 F5:1 D5:1 | E5:1 F5:0.5 G5:0.5 C5:2 |"
        "D5:0.5 F5:0.5 A5:1 D6:1.5 C6:0.5 | Bb5:1 D6:1 F5:2 |"
        "A5:0.5 Bb5:0.5 C6:1 F5:1 A5:1 | G5:3 E5:1 |"
        "F5:0.5 A5:0.5 D6:1 E6:1 F6:1 | D6:1.5 C6:0.5 Bb5:2 |"
        "Bb5:1 A5:1 G5:1 Bb5:1 | C6:1.5 Bb5:0.5 G5:1 E5:1")
    chords = chord_list("F:maj C:maj D:min Bb:maj F:maj C:maj Bb:maj C:maj "
                        "D:min Bb:maj F:maj C:maj D:min Bb:maj G:min C:maj")
    s.melody("lead", lead, brass, legato=0.97)
    pad(s, "strings", chords, strings, octave=4)
    arpeggio(s, "bells", chords, lambda f, d: bell(f, d, decay=5), octave_shift=12,
             pattern=(0, 1, 2, 2, 1, 2), step=0.5)
    bassline(s, "bass", chords, lambda f, d: strings(f, d, r=0.15, a=0.02), "march")
    s.drums("drums", "t.......t...t...+....s.......s...+h.h.h.h.h.h.h.h.", 16, step=0.25)
    for b in (0, 32):
        s.place("drums", drum("c"), b, 0.7)
    return s.mix({"lead": 0.55, "strings": 0.4, "bells": 0.16, "bass": 0.5, "drums": 0.5},
                 verb=0.22, pans={"bells": 0.35, "strings": -0.15})


def victory_a():
    """Chip fanfare in D: quick run up, then a I-IV-V-I flourish."""
    s = Song(150, 3)
    lead = parse("A4:0.25 D5:0.25 F#5:0.25 A5:0.25 D6:0.75 B5:0.25 C#6:0.5 A5:0.5 D6:1 |"
                 "r:4 | r:4")
    s.melody("lead", lead, lambda f, d: pulse(f, d, duty=0.25), legato=0.95)
    s.place("lead", pulse(freq(midi("D6")), 2.2 * s.spb, duty=0.25), 4, 1.0)
    s.melody("harm", parse("r:1 F#5:0.75 G5:0.25 A5:0.5 E5:0.5 F#5:1 | r:4 | r:4"),
             lambda f, d: pulse(f, d, duty=0.5, vib=False))
    s.place("harm", pulse(freq(midi("A5")), 2.2 * s.spb, duty=0.5), 4, 1.0)
    for beat, note in ((0, "D3"), (1, "G2"), (2, "A2"), (3, "D3"), (4, "D2")):
        s.place("bass", triangle(freq(midi(note)), (0.9 if beat < 4 else 2.2) * s.spb), beat)
    for beat in (0, 1, 2, 3):
        s.place("drums", drum("s"), beat + 0.5, 0.4)
    s.place("drums", drum("k"), 4)
    s.place("drums", drum("c"), 4, 0.9)
    return s.mix({"lead": 0.6, "harm": 0.3, "bass": 0.6, "drums": 0.5}, loop=False, verb=0.15)


def victory_b():
    """Brass fanfare: an E minor climb that lands on a bright E major chord."""
    s = Song(126, 3)
    lead = parse("B4:0.333 E5:0.333 G5:0.334 B5:1 C6:0.5 D6:0.5 r:1 | r:4 | r:4")
    s.melody("lead", lead, brass, legato=0.95)
    s.place("lead", brass(freq(midi("E6")), 3.0 * s.spb, r=0.5), 3, 1.0)
    for beat, root, q, ln in ((0, "E", "min", 1), (1, "C", "maj", 1), (2, "D", "maj", 1), (3, "E", "maj", 3.2)):
        for m in chord_tones(root + "4", q):
            s.place("strings", strings(freq(m), ln * s.spb, a=0.03), beat, 1 / 3)
        s.place("bass", strings(freq(midi(root + "2")), ln * s.spb, a=0.02, r=0.3), beat)
    for beat in (2, 2.25, 2.5, 2.75):
        s.place("drums", drum("t"), beat, 0.6)
    s.place("drums", drum("c"), 3, 1.0)
    s.place("drums", drum("t"), 3, 0.9)
    return s.mix({"lead": 0.55, "strings": 0.45, "bass": 0.45, "drums": 0.5}, loop=False, verb=0.25)


def victory_c():
    """Sparkling bells: an F major shimmer up, then IV-V-I with strings underneath."""
    s = Song(132, 3)
    run = parse("F5:0.25 A5:0.25 C6:0.25 F6:0.25 A5:0.25 C6:0.25 F6:0.25 A6:0.25 C6:1 D6:0.5 E6:0.5 | r:4 | r:4")
    s.melody("bells", run, lambda f, d: bell(f, d, decay=3.5))
    s.place("bells", bell(freq(midi("F6")), 2.5 * s.spb, decay=1.6), 4, 1.2)
    s.place("bells", bell(freq(midi("C6")), 2.5 * s.spb, decay=1.6), 4, 0.7)
    s.melody("lead", parse("r:2 C6:1 D6:0.5 E6:0.5 | r:4 | r:4"), brass)
    s.place("lead", brass(freq(midi("F6")), 2.6 * s.spb, r=0.5), 4, 1.0)
    for beat, root, ln in ((0, "F", 2), (2, "Bb", 1), (3, "C", 1), (4, "F", 3)):
        for m in chord_tones(root + "4", "maj"):
            s.place("strings", strings(freq(m), ln * s.spb, a=0.05), beat, 1 / 3)
        s.place("bass", strings(freq(midi(root + "2")), ln * s.spb, a=0.02, r=0.3), beat)
    s.place("drums", drum("c"), 4, 0.6)
    return s.mix({"bells": 0.35, "lead": 0.4, "strings": 0.4, "bass": 0.4, "drums": 0.4}, loop=False, verb=0.3)


CAPTURE = {"A": capture_a, "B": capture_b, "C": capture_c}
VICTORY = {"A": victory_a, "B": victory_b, "C": victory_c}
# The options Dylan picked; these are what the mod ships.
CHOSEN_CAPTURE = "A"
CHOSEN_VICTORY = "A"


# ---------------------------------------------------------------- output

def write(path, audio, quality=5, fmt="ogg"):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    pcm = (np.clip(audio.T, -1, 1) * 32767).astype("<i2").tobytes()
    codec = ["-c:a", "libvorbis", "-q:a", str(quality)] if fmt == "ogg" else ["-c:a", "libmp3lame", "-q:a", "2"]
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    *codec, path], input=pcm, check=True)


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "previews":
        out = sys.argv[2]
        for key, fn in CAPTURE.items():
            loop = fn()
            twice = np.concatenate([loop, loop], axis=1)  # play the loop twice so the seam can be heard
            write(f"{out}/capture-{key}.mp3", twice, fmt="mp3")
        for key, fn in VICTORY.items():
            write(f"{out}/victory-{key}.mp3", fn(), fmt="mp3")
        return
    write(f"{SOUNDS}/capture.ogg", CAPTURE[CHOSEN_CAPTURE](), quality=4)
    write(f"{SOUNDS}/victory.ogg", VICTORY[CHOSEN_VICTORY](), quality=5)


if __name__ == "__main__":
    main()
