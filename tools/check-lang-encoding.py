#!/usr/bin/env python3
"""Guard the localisation files against double-encoding corruption.

What went wrong once already (commit 23e99cd, 2026-10-02): UTF-8 text was read
as CP1252 and written back out as UTF-8. The arrow of "LEVEL I -> LEVEL II"
(E2 86 92) came back as the three characters U+00E2 U+2020 U+2019, and players
saw "a-? RESOURCE" instead of "PREVIOUS RESOURCE" on the mine panel.

That class of damage is invisible to JSON parsing -- the file is perfectly valid
JSON, just full of nonsense -- so no build step caught it. This check catches it
on every push.

Usage:
    python tools/check-lang-encoding.py          # verify, exit 1 on problems
    python tools/check-lang-encoding.py --fix    # rewrite decodable values in place
"""

from __future__ import annotations

import json
import sys
import unicodedata
from pathlib import Path

LANG_DIR = Path("src/main/resources/assets/createcityexpansioncore/lang")

# CP1252's high range: bytes 0x80-0x9F that CP1252 maps to real punctuation.
CP1252_HIGH = {
    0x20AC, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021, 0x02C6,
    0x2030, 0x0160, 0x2039, 0x0152, 0x017D, 0x2018, 0x2019, 0x201C,
    0x201D, 0x2022, 0x2013, 0x2014, 0x02DC, 0x2122, 0x0161, 0x203A,
    0x0153, 0x017E, 0x0178,
}

# The three Latin-1 characters that are really a stray lead byte.
LEAD_ARTIFACT = {0x00E2, 0x00C2, 0x00C3}

# Reverse map for the CP1252 range, used by --fix.
CP1252_BYTE = {
    0x20AC: 0x80, 0x201A: 0x82, 0x0192: 0x83, 0x201E: 0x84, 0x2026: 0x85,
    0x2020: 0x86, 0x2021: 0x87, 0x02C6: 0x88, 0x2030: 0x89, 0x0160: 0x8A,
    0x2039: 0x8B, 0x0152: 0x8C, 0x017D: 0x8E, 0x2018: 0x91, 0x2019: 0x92,
    0x201C: 0x93, 0x201D: 0x94, 0x2022: 0x95, 0x2013: 0x96, 0x2014: 0x97,
    0x02DC: 0x98, 0x2122: 0x99, 0x0161: 0x9A, 0x203A: 0x9B, 0x0153: 0x9C,
    0x017E: 0x9E, 0x0178: 0x9F,
}


def decode_mojibake(text: str) -> str | None:
    """Undo the CP1252 misread. Returns None when the text is not decodable."""
    out = bytearray()
    for ch in text:
        cp = ord(ch)
        if cp <= 0xFF:
            out.append(cp)
        elif cp in CP1252_BYTE:
            out.append(CP1252_BYTE[cp])
        else:
            return None
    try:
        return out.decode("utf-8")
    except UnicodeDecodeError:
        return None


def find_artifacts(text: str) -> tuple[list[tuple[int, str]], list[tuple[int, str]]]:
    """Return (fatal fingerprint hits, suspicious lone leads)."""
    fatal: list[tuple[int, str]] = []
    lone: list[tuple[int, str]] = []
    for index, ch in enumerate(text):
        cp = ord(ch)
        if cp not in LEAD_ARTIFACT:
            continue
        nxt = text[index + 1] if index + 1 < len(text) else ""
        if nxt and ord(nxt) in CP1252_HIGH:
            fatal.append((index, f"U+{cp:04X} U+{ord(nxt):04X}"))
        else:
            lone.append((index, f"U+{cp:04X}"))
    return fatal, lone


def check_file(path: Path, fix: bool) -> list[str]:
    problems: list[str] = []
    raw = path.read_bytes()

    if raw[:3] == b"\xef\xbb\xbf":
        problems.append(f"{path.name}: UTF-8 BOM present; Minecraft cannot read it")

    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        problems.append(f"{path.name}: not valid UTF-8 -- {exc}")
        return problems

    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        problems.append(f"{path.name}: invalid JSON -- {exc}")
        return problems

    if not isinstance(data, dict):
        problems.append(f"{path.name}: expected a flat object of keys")
        return problems

    # For the English and Russian locales the three stray lead bytes can never
    # be legitimate: English has no Latin-1 diacritics and Russian is Cyrillic.
    # Treat any occurrence as corruption. Other locales keep the narrower
    # two-character fingerprint so a legitimate "a-circumflex" cannot block them.
    strict = path.stem in {"en_us", "ru_ru"}

    fixed: list[tuple[str, str, str]] = []
    for key, value in data.items():
        if not isinstance(value, str):
            continue
        fatal, lone = find_artifacts(value)
        if strict and lone:
            fatal = fatal + lone
        if fatal:
            recovered = decode_mojibake(value)
            if fix and recovered is not None:
                fixed.append((key, value, recovered))
            else:
                hint = recovered if recovered is not None else "not auto-decodable"
                problems.append(
                    f"{path.name}: {key} carries a double-encoding artefact "
                    f"({fatal[0][1]}) -> {hint!r}"
                )
        elif lone:
            print(
                f"  warning: {path.name}: {key} contains a bare Latin-1 lead "
                f"({lone[0][1]}); check it is intentional",
                file=sys.stderr,
            )

    if fixed:
        # Rewrite only the damaged values, in place, so the rest of the file --
        # key order, indentation, line endings -- survives untouched.
        for key, broken, recovered in fixed:
            old_entry = json.dumps(key, ensure_ascii=False) + ": " + json.dumps(
                broken, ensure_ascii=False
            )
            new_entry = json.dumps(key, ensure_ascii=False) + ": " + json.dumps(
                recovered, ensure_ascii=False
            )
            if old_entry in text:
                text = text.replace(old_entry, new_entry)
        if fix:
            path.write_bytes(text.encode("utf-8"))
            print(f"  fixed {len(fixed)} value(s) in {path.name}")

    return problems


def main() -> int:
    fix = "--fix" in sys.argv[1:]
    if not LANG_DIR.is_dir():
        print(f"error: {LANG_DIR} not found; run from the repository root", file=sys.stderr)
        return 2

    files = sorted(LANG_DIR.glob("*.json"))
    if not files:
        print(f"error: no lang files under {LANG_DIR}", file=sys.stderr)
        return 2

    problems: list[str] = []
    keys: dict[str, set[str]] = {}
    for path in files:
        problems += check_file(path, fix)
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            keys[path.name] = set(data)
        except (json.JSONDecodeError, UnicodeDecodeError):
            pass

    # Every locale must expose the same key set, otherwise a player switching
    # language sees raw translation keys on screen.
    if len(keys) >= 2:
        names = sorted(keys)
        reference, *others = names
        for name in others:
            missing = sorted(keys[reference] - keys[name])
            extra = sorted(keys[name] - keys[reference])
            for key in missing:
                problems.append(f"{name}: missing key present in {reference}: {key}")
            for key in extra:
                problems.append(f"{reference}: missing key present in {name}: {key}")

    if problems:
        print("\n".join(f"error: {p}" for p in problems), file=sys.stderr)
        print(f"\n{len(problems)} localisation problem(s)", file=sys.stderr)
        return 1

    total = sum(len(v) for v in keys.values())
    print(f"ok: {len(files)} lang file(s), {total} keys, encoding and parity clean")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
