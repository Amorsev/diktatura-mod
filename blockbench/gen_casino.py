"""Казино на GeckoLib: модели столов для Blockbench и атлас игральных карт.

  poker_table — овальный стол на 6 мест (сукно, бортик, мягкий поручень, тумба, табуреты, колода и стойка фишек).
                Модель в половинном масштабе: в игре рисуется ×2 (PokerTableRenderer), чтобы развёртка
                большого стола уместилась в текстуру.

Координаты Blockbench: Y вверх, пол — 0, перёд (-Z) — сторона крупье, +X — справа от крупье, если смотреть
со стороны игроков... проще: в игре стол стоит крупье на север, и +X модели — восток.
Места игроков (центры табуретов, пиксели модели) — SEATS; их же использует Java (PokerTableEntity.SEATS).

Атлас карт textures/entity/poker/cards.png (512x128): карта 20x28, столбцы — 2..A и рубашка, строки — масти
♠ ♥ ♦ ♣ (как Card.suit), внизу — фишки (верх 8x8 и бок 8x2) и кнопка дилера.

Запуск: python blockbench/gen_casino.py → blockbench/out/poker_table.png/.js, out/cards.png
"""
import math
import os

from PIL import Image

from gen_npcs import BLACK, GOLD, GOLD_D, RED, WHITE, Model, hexc, shade, solid, OUT

FELT = hexc('#1E6B3A')
FELT_D = hexc('#17552E')
WOOD = hexc('#6B3A1E')
WOOD_D = hexc('#4A2612')
LEATHER = hexc('#2A1A14')
BRASS = hexc('#C9A23A')

# Места игроков вокруг стола (по часовой стрелке от крупье, если смотреть сверху): x, z — пиксели модели.
SEATS = [(13, -17), (25, 0), (13, 17), (-13, 17), (-25, 0), (-13, -17)]
DEALER = (0, -17)


def ellipse_mask(ax, az):
    """Клетки 1x1 внутри эллипса с полуосями ax, az (центр в 0, клетка [x, x+1))."""
    cells = set()
    for x in range(-ax, ax):
        for z in range(-az, az):
            cx, cz = x + 0.5, z + 0.5
            if (cx / ax) ** 2 + (cz / az) ** 2 <= 1.0:
                cells.add((x, z))
    return cells


def rects(cells):
    """Жадное покрытие клеток прямоугольниками: строки по z, одинаковые соседние строки сливаются."""
    rows = {}
    for x, z in cells:
        rows.setdefault(z, set()).add(x)
    spans = {}
    for z, xs in rows.items():
        xs = sorted(xs)
        cur = []
        start = prev = xs[0]
        for x in xs[1:]:
            if x != prev + 1:
                cur.append((start, prev + 1))
                start = x
            prev = x
        cur.append((start, prev + 1))
        spans[z] = cur
    out = []
    open_ = {}
    for z in sorted(spans):
        now = {}
        for sp in spans[z]:
            if sp in open_:
                now[sp] = open_.pop(sp)
            else:
                now[sp] = z
        for sp, z0 in open_.items():
            out.append((sp[0], z0, sp[1], z))
        open_ = now
    last = max(spans) + 1
    for sp, z0 in open_.items():
        out.append((sp[0], z0, sp[1], last))
    return out


def felt(f):
    for n in f:
        f[n].fill(FELT, 4)
    up = f['up']
    for y in range(up.h):
        for x in range(up.w):
            if (x * 3 + y * 5) % 13 == 0:
                up.set(x, y, FELT_D, 2)


def wood(f):
    for n in f:
        for y in range(f[n].h):
            for x in range(f[n].w):
                f[n].set(x, y, WOOD_D if (x + y * 3) % 7 == 0 else WOOD, 5)


def leather(f):
    for n in f:
        f[n].fill(LEATHER, 3)
        if f[n].h >= 2:
            f[n].row(0, shade(LEATHER, 1.3), 2)


def poker_table():
    m = Model('poker_table', 777, height=256)
    m.group('table', None, (0, 0, 0))
    outer = ellipse_mask(20, 12)
    inner = ellipse_mask(17, 9)
    # Бортик (дерево) — весь овал; сукно — внутренний овал; поручень — кольцо между ними.
    for i, (x0, z0, x1, z1) in enumerate(rects(outer)):
        m.cube('table', f'rim{i}', (x0, 5, z0), (x1, 7, z1), wood)
    for i, (x0, z0, x1, z1) in enumerate(rects(inner)):
        m.cube('table', f'felt{i}', (x0, 7, z0), (x1, 8, z1), felt)
    for i, (x0, z0, x1, z1) in enumerate(rects(outer - inner)):
        m.cube('table', f'rail{i}', (x0, 7, z0), (x1, 9, z1), leather)
    # Тумба и опора.
    m.cube('table', 'pedestal', (-6, 1, -3), (6, 5, 3), wood)
    m.cube('table', 'base', (-9, 0, -5), (9, 1, 5), solid(WOOD_D, 4))
    # Латунная линия по сукну — «дуга» для карт игроков.
    # Табуреты.
    for i, (sx, sz) in enumerate(SEATS):
        g = m.group(f'stool{i}', 'table', (sx, 0, sz))
        m.cube(g, f'stool{i}_seat', (sx - 3, 4, sz - 3), (sx + 3, 5, sz + 3), solid(hexc('#8A1E1E'), 4, top=hexc('#A82828')))
        m.cube(g, f'stool{i}_leg', (sx - 0.5, 0, sz - 0.5), (sx + 0.5, 4, sz + 0.5), solid(BRASS, 3))
    # Колода и стойка фишек у крупье.
    deck = m.group('deck', 'table', (0, 8, -6))

    def deck_paint(f):
        for n in f:
            f[n].fill(WHITE, 2)
        f['up'].fill(hexc('#8A1E2A'), 3)
        f['up'].set(0, 1, GOLD, 1)
    m.cube(deck, 'deck_cards', (-1, 8, -7), (1, 9, -4), deck_paint)

    def rack(f):
        cols = [WHITE, RED, hexc('#2F5FBF'), BLACK, GOLD]
        for n in f:
            f[n].fill(shade(WOOD, 0.8), 3)
        for x in range(f['up'].w):
            for y in range(f['up'].h):
                f['up'].set(x, y, cols[(x // 2) % len(cols)], 3)
    m.cube('table', 'chip_rack', (-8, 8, -8), (-3, 9, -6), rack)
    m.cube('table', 'chip_rack2', (3, 8, -8), (8, 9, -6), rack)
    return m


# ---------------------------------------------------------------- атлас карт

CW, CH = 20, 28
GLYPHS = {
    'A': ['.#.', '#.#', '###', '#.#', '#.#'],
    '2': ['##.', '..#', '.#.', '#..', '###'],
    '3': ['##.', '..#', '.#.', '..#', '##.'],
    '4': ['#.#', '#.#', '###', '..#', '..#'],
    '5': ['###', '#..', '##.', '..#', '##.'],
    '6': ['.##', '#..', '###', '#.#', '###'],
    '7': ['###', '..#', '.#.', '.#.', '.#.'],
    '8': ['###', '#.#', '###', '#.#', '###'],
    '9': ['###', '#.#', '###', '..#', '##.'],
    'T': ['#.###', '#.#.#', '#.#.#', '#.#.#', '#.###'],
    'J': ['..#', '..#', '..#', '#.#', '.#.'],
    'Q': ['.#.', '#.#', '#.#', '##.', '.##'],
    'K': ['#.#', '##.', '#..', '##.', '#.#'],
}
SUITS = {
    0: ['...#...', '..###..', '.#####.', '#######', '#######', '.#.#.#.', '...#...'],   # пики
    1: ['.##.##.', '#######', '#######', '#######', '.#####.', '..###..', '...#...'],   # червы
    2: ['...#...', '..###..', '.#####.', '#######', '.#####.', '..###..', '...#...'],   # бубны
    3: ['..###..', '..###..', '##.#.##', '#######', '##.#.##', '...#...', '..###..'],   # трефы
}
SUIT_SMALL = {
    0: ['..#..', '.###.', '#####', '#####', '.#.#.'],
    1: ['##.##', '#####', '#####', '.###.', '..#..'],
    2: ['..#..', '.###.', '#####', '.###.', '..#..'],
    3: ['.###.', '.###.', '#####', '#####', '..#..'],
}
RANKS = '23456789TJQKA'
INK_RED = (200, 30, 40, 255)
INK_BLACK = (25, 25, 30, 255)
PAPER = (246, 242, 230, 255)
EDGE = (180, 172, 150, 255)


def blit(px, x0, y0, rows, color, scale=1):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '#':
                for dy in range(scale):
                    for dx in range(scale):
                        px[x0 + x * scale + dx, y0 + y * scale + dy] = color


def card_face(px, x0, y0, rank, suit):
    for y in range(CH):
        for x in range(CW):
            edge = x in (0, CW - 1) or y in (0, CH - 1)
            corner = (x in (0, CW - 1)) and (y in (0, CH - 1))
            px[x0 + x, y0 + y] = (0, 0, 0, 0) if corner else (EDGE if edge else PAPER)
    ink = INK_RED if suit in (1, 2) else INK_BLACK
    g = GLYPHS[RANKS[rank - 2]]
    blit(px, x0 + 2, y0 + 2, g, ink)
    blit(px, x0 + 2, y0 + 8, SUIT_SMALL[suit], ink)
    # Крупная масть в центре (×2), у картинок — с золотой рамкой.
    if 11 <= rank <= 13:
        for x in range(8, CW - 2):
            px[x0 + x, y0 + 4] = GOLD + (255,)
            px[x0 + x, y0 + CH - 5] = GOLD + (255,)
        for y in range(4, CH - 4):
            px[x0 + 8, y0 + y] = GOLD + (255,)
            px[x0 + CW - 3, y0 + y] = GOLD + (255,)
        blit(px, x0 + 10, y0 + 7, g, ink, 2)
        blit(px, x0 + 10, y0 + 18, SUIT_SMALL[suit], ink)
    else:
        blit(px, x0 + 9, y0 + 10, SUITS[suit], ink)
        blit(px, x0 + CW - 2 - len(g[0]), y0 + CH - 7, g, ink)


def card_back(px, x0, y0):
    base = (138, 30, 42, 255)
    dark = (100, 18, 28, 255)
    for y in range(CH):
        for x in range(CW):
            edge = x in (0, CW - 1) or y in (0, CH - 1)
            corner = (x in (0, CW - 1)) and (y in (0, CH - 1))
            if corner:
                c = (0, 0, 0, 0)
            elif edge or x in (2, CW - 3) and 2 <= y <= CH - 3 or y in (2, CH - 3) and 2 <= x <= CW - 3:
                c = (240, 236, 224, 255)
            else:
                c = dark if (x + y) % 4 == 0 or (x - y) % 4 == 0 else base
            px[x0 + x, y0 + y] = c
    star = ['..#..', '..#..', '#####', '.###.', '##.##']
    blit(px, x0 + 8, y0 + 12, star, GOLD + (255,))


def chips(px, y0):
    cols = [(236, 236, 230), (200, 40, 40), (47, 95, 191), (30, 30, 34), (227, 178, 60)]
    for i, c in enumerate(cols):
        x0 = i * 8
        for y in range(8):
            for x in range(8):
                d = math.hypot(x - 3.5, y - 3.5)
                if d <= 4.0:
                    stripe = d > 2.6 and (int(math.degrees(math.atan2(y - 3.5, x - 3.5)) + 360) // 45) % 2 == 0
                    v = (245, 245, 240) if stripe and i != 0 else (190, 190, 190) if stripe else c
                    px[x0 + x, y0 + y] = v + (255,)
        # Бок: 8x2 полоски.
        for x in range(8):
            for y in range(2):
                px[x0 + x, y0 + 8 + y] = ((245, 245, 240) if x % 4 == 1 and i != 0 else shade(c, 0.85)) + (255,)
    # Кнопка дилера.
    x0 = 5 * 8
    for y in range(8):
        for x in range(8):
            if math.hypot(x - 3.5, y - 3.5) <= 4.0:
                px[x0 + x, y0 + y] = (250, 250, 250, 255)
    blit(px, x0 + 2, y0 + 1, ['##.', '#.#', '#.#', '#.#', '##.'], (25, 25, 30, 255))


def cards_atlas():
    img = Image.new('RGBA', (512, 128), (0, 0, 0, 0))
    px = img.load()
    for suit in range(4):
        for r in range(2, 15):
            card_face(px, (r - 2) * CW, suit * CH, r, suit)
        card_back(px, 13 * CW, suit * CH)
    chips(px, 4 * CH)
    return img


if __name__ == '__main__':
    m = poker_table()
    m.save()
    print(m.id, len(m.cubes), 'кубов, UV до y =', m.shelf[1] + m.shelf[2])
    os.makedirs(OUT, exist_ok=True)
    cards_atlas().save(os.path.join(OUT, 'cards.png'))
    print('cards.png')
