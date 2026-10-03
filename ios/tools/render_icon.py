#!/usr/bin/env python3
"""Generate the app's code-native document/clock icon as an opaque PNG."""
from pathlib import Path
import math
import struct
import zlib

SIZE = 1024
TEAL, WHITE, NAVY = (8, 127, 140), (255, 255, 255), (23, 43, 58)


def distance(x, y, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0, min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(x - ax - t * dx, y - ay - t * dy)


def pixel(x, y):
    color = TEAL
    if 296 <= x <= 704 and 240 <= y <= 784 and not (x > 576 and y < x - 336):
        color = WHITE
    if 576 <= x <= 704 and 240 <= y <= 368 and y >= x - 336:
        color = (181, 233, 229)
    if any(distance(x, y, 390, line, end, line) <= 14 for line, end in ((448, 600), (526, 570), (604, 494))):
        color = TEAL
    if math.hypot(x - 652, y - 694) <= 130:
        color = NAVY
        if min(distance(x, y, 652, 624, 652, 694), distance(x, y, 652, 694, 709, 728)) <= 14:
            color = WHITE
    return color


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)


def main():
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)
        for x in range(SIZE):
            raw.extend(pixel(x, y))
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    target = Path(__file__).resolve().parents[1] / "GdanskCaseMonitor/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
    target.write_bytes(data)
    print("Generated opaque 1024×1024 app icon:", target)


if __name__ == "__main__":
    main()
