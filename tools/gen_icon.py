"""Логотип мода (icon.png): пиксельный плакат — вождь в маршальской фуражке на фоне красных лучей,
под ним лента «ДИКТАТУРА». Рисуется в сетке 64x64 и увеличивается до 128x128 (и 512 для превью).

Запуск: python tools/gen_icon.py
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/diktatura/icon.png')
N = 64


def hexc(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


RED = hexc('#B3202A')
RED_D = hexc('#86141C')
RED_DD = hexc('#5E0C12')
GOLD = hexc('#F2C23C')
GOLD_D = hexc('#B8861E')
GOLD_L = hexc('#FFE27A')
OLIVE = hexc('#4E5E2E')
OLIVE_D = hexc('#36421F')
OLIVE_L = hexc('#66783C')
SKIN = hexc('#E3AC84')
SKIN_D = hexc('#C38E68')
BLACK = hexc('#16131A')
WHITE = hexc('#F2EEE4')
EYE = hexc('#4A2E1A')

img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = img.load()


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


# Фон: лучи из-за головы, как на плакате.
cx, cy = 32, 30
for y in range(N):
    for x in range(N):
        a = math.degrees(math.atan2(y - cy, x - cx)) % 360
        ray = int(a // 15) % 2 == 0
        d = math.hypot(x - cx, y - cy)
        c = RED if ray else RED_D
        if d > 34:
            c = RED_D if ray else RED_DD
        put(x, y, c)

# Ореол за головой.
for y in range(N):
    for x in range(N):
        d = math.hypot(x - cx, y - (cy - 2))
        if 17 <= d < 18.2:
            put(x, y, GOLD_D)

# Плечи и китель с золотыми эполетами.
rect(10, 44, 54, 56, OLIVE)
rect(10, 44, 54, 45, OLIVE_L)
rect(30, 44, 34, 56, OLIVE_D)
for y in (47, 50, 53):
    put(32, y, GOLD)
    put(31, y, GOLD_D)
rect(25, 44, 30, 47, RED)
rect(34, 44, 39, 47, RED)
for x0 in (8, 46):
    rect(x0, 42, x0 + 10, 45, GOLD)
    rect(x0, 42, x0 + 10, 43, GOLD_L)
    for x in range(x0, x0 + 10, 2):
        rect(x, 45, x + 1, 48, GOLD_D)
# Медали на груди.
for x, c in ((14, RED), (16, GOLD), (18, hexc('#2F6FBF')), (20, RED)):
    rect(x, 49, x + 2, 51, c)
    put(x, 51, GOLD)
    put(x + 1, 51, GOLD_D)

# Голова: лицо 24x24 (блок 8x8 в масштабе 3).
HX, HY, S = 20, 20, 3
FACE = [
    "HHHHHHHH",
    "HSSSSSSH",
    "SBBSSBBS",
    "SWESSWES",
    "SSSNNSSS",
    "SMMMMMMS",
    "MMSssSMM",
    "SSSSSSSS",
]
COL = {'H': BLACK, 'S': SKIN, 's': SKIN_D, 'B': BLACK, 'W': WHITE, 'E': EYE, 'N': SKIN_D, 'M': BLACK}
for j, row in enumerate(FACE):
    for i, ch in enumerate(row):
        rect(HX + i * S, HY + j * S, HX + i * S + S, HY + j * S + S, COL[ch])
# Тень под подбородком и щёки.
rect(HX, HY + 24, HX + 24, HY + 25, SKIN_D)
put(HX + 1, HY + 13, SKIN_D)
put(HX + 22, HY + 13, SKIN_D)
# Блики в глазах.
put(HX + 7, HY + 9, WHITE)
put(HX + 19, HY + 9, WHITE)

# Маршальская фуражка: высокая тулья, красный околыш, козырёк, герб.
rect(14, 8, 50, 17, OLIVE)
rect(14, 8, 50, 9, OLIVE_L)
rect(15, 16, 49, 17, OLIVE_D)
rect(13, 9, 14, 16, OLIVE_D)
rect(50, 9, 51, 16, OLIVE_D)
rect(18, 17, 46, 21, RED)
rect(18, 17, 46, 18, hexc('#D33A42'))
rect(17, 21, 47, 23, BLACK)
rect(19, 23, 45, 24, hexc('#2A2630'))
rect(18, 20, 46, 21, GOLD_D)   # шнур над козырьком
# Герб: звезда в золотом венке.
STAR = [
    "...#...",
    "...#...",
    "#######",
    ".#####.",
    "..###..",
    ".##.##.",
    "#.....#",
]
for j, row in enumerate(STAR):
    for i, ch in enumerate(row):
        if ch == '#':
            put(29 + i, 10 + j, GOLD)
for (x, y) in ((27, 13), (27, 14), (28, 16), (37, 13), (37, 14), (36, 16)):
    put(x, y, GOLD_D)

# Лента с надписью «ДИКТАТУРА».
rect(4, 54, 60, 63, RED_DD)
rect(5, 55, 59, 62, RED)
rect(5, 55, 59, 56, hexc('#D33A42'))
for x0, d in ((0, 1), (60, -1)):
    rect(x0, 56, x0 + 4, 63, RED_D)
    put(x0 + (0 if d > 0 else 3), 56, (0, 0, 0, 0))
    put(x0 + (0 if d > 0 else 3), 62, (0, 0, 0, 0))
    put(x0 + (1 if d > 0 else 2), 59, RED_DD)

FONT = {
    'Д': [".###.", ".#.#.", ".#.#.", "#####", "#...#"],
    'И': ["#..#", "#..#", "#.##", "##.#", "#..#"],
    'К': ["#..#", "#.#.", "##..", "#.#.", "#..#"],
    'Т': ["####", ".##.", ".##.", ".##.", ".##."],
    'А': [".##.", "#..#", "####", "#..#", "#..#"],
    'У': ["#..#", "#..#", ".###", "...#", "###."],
    'Р': ["###.", "#..#", "###.", "#...", "#..."],
}
text = "ДИКТАТУРА"
w = sum(len(FONT[ch][0]) + 1 for ch in text) - 1
x = (N - w) // 2
for ch in text:
    for j, row in enumerate(FONT[ch]):
        for i, c in enumerate(row):
            if c == '#':
                put(x + i, 57 + j + 1, RED_DD)
                put(x + i, 57 + j, GOLD)
    x += len(FONT[ch][0]) + 1

# Рамка.
for i in range(N):
    for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
        if px[x, y][3] and not (y >= 54 and (x < 4 or x >= 60)):
            put(x, y, GOLD_D)

img.resize((128, 128), Image.NEAREST).save(OUT)
prev = os.path.join(ROOT, 'docs')
os.makedirs(prev, exist_ok=True)
img.resize((512, 512), Image.NEAREST).save(os.path.join(prev, 'logo.png'))
print('ok')
