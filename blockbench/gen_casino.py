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


# ---------------------------------------------------------------- рулетка, автомат, кости

ROUL_RED = hexc('#B3202A')
ROUL_BLACK = hexc('#1E1E22')
ROUL_GREEN = hexc('#2E8B3A')
SEGMENTS = 16


def roulette_wheel():
    """Колесо рулетки (полный масштаб, лежит на столе): чаша-обод, 16 лунок (зеро, красные нечётные, чёрные чётные),
    золотая ступица и шарик. Кости: wheel (крутится, в ней лунки seg_k), ball_orbit → ball (бежит по кругу)."""
    m = Model('roulette_wheel', 3636)
    m.group('base', None, (0, 0, 0))
    m.cube('base', 'plate', (-11, 0, -11), (11, 1, 11), wood)
    for k in range(SEGMENTS):
        g = m.group(f'rim{k}', 'base', (0, 0, 0), (0, k * 360 / SEGMENTS, 0))
        m.cube(g, f'rim{k}_c', (-2.5, 1, -12), (2.5, 4, -10), wood)
    m.group('wheel', None, (0, 0, 0))
    m.cube('wheel', 'disc', (-6, 0.5, -6), (6, 1.5, 6), solid(WOOD_D, 3))
    for k in range(SEGMENTS):
        col = ROUL_GREEN if k == 0 else ROUL_RED if k % 2 == 1 else ROUL_BLACK
        g = m.group(f'seg{k}', 'wheel', (0, 0, 0), (0, k * 360 / SEGMENTS, 0))
        y0 = 1.0 + (0.02 if k % 2 else 0.0)

        def pocket(f, col=col):
            for n in f:
                f[n].fill(col, 4)
            f['up'].row(0, GOLD, 1)
        m.cube(g, f'seg{k}_c', (-1.5, y0, -10), (1.5, y0 + 1, -6), pocket)
    m.cube('wheel', 'hub', (-2, 1, -2), (2, 4, 2), solid(GOLD, 3, top=shade(GOLD, 1.15)))
    m.cube('wheel', 'spindle', (-0.5, 4, -0.5), (0.5, 6, 0.5), solid(GOLD_D, 2))
    m.group('ball_orbit', None, (0, 0, 0))
    m.group('ball', 'ball_orbit', (0, 2, -8.5))
    m.cube('ball', 'ball_c', (-0.5, 2, -9), (0.5, 3, -8), solid((245, 245, 240), 2))
    return m


# Символы барабанов (10x8, как в Casino.SYMBOL_ITEMS): звезда, корона, колос, трактор, печать, усы.
SYMBOL_ART = [
    (['....##....', '....##....', '##########', '.########.', '..######..', '.###..###.', '.##....##.', '..........'],
     {'#': GOLD}),
    (['.#..##..#.', '.#.####.#.', '.########.', '.#r##r##r.', '.########.', '.########.', '..........', '..........'],
     {'#': GOLD, 'r': ROUL_RED}),
    (['....#.....', '...###....', '..#.#.#...', '...###....', '..#.#.#...', '...###....', '....#.....', '....#.....'],
     {'#': hexc('#D8B54A')}),
    (['..####....', '..#..#....', '.######.r.', '.#######r.', '.########.', '.oo..oooo.', 'oooo.oooo.', '.oo...oo..'],
     {'#': ROUL_RED, 'r': (60, 60, 60), 'o': (30, 30, 34)}),
    (['...####...', '..#....#..', '.#.####.#.', '.#.#..#.#.', '.#.####.#.', '..#....#..', '...####...', '..........'],
     {'#': ROUL_RED}),
    (['..........', '..........', '.##....##.', '####..####', '##########', '.###..###.', '..........', '..........'],
     {'#': (30, 26, 24)}),
]
REEL_X = (-13, 0, 13)
REEL_Y = 29
REEL_Z = 2
PLATE_W = 10
PLATE_H = 8


def plate_painter(sym):
    art, pal = SYMBOL_ART[sym]

    def paint(f):
        for n in f:
            f[n].fill((240, 236, 222), 2)
        fr = f['north']
        for y, row in enumerate(art):
            for x, ch in enumerate(row):
                if ch in pal:
                    fr.set(x, y, pal[ch], 3)
    return paint


def slot_machine():
    """Автомат «Пятилетка» (полный масштаб): тумба, рама с тремя барабанами по 6 символов, табло со звездой,
    лампа (lamp_on/lamp_off) и рычаг (lever) слева. Барабан reel_i: пластина plate_i_k повёрнута на -k*60° по X,
    поэтому поворот барабана на +k*60° (Blockbench) выводит символ k вперёд."""
    m = Model('slot_machine', 1955, height=256)
    cab = hexc('#7A1020')
    m.group('body', None, (0, 0, 0))

    def cabinet(f):
        for n in f:
            f[n].fill(cab, 5)
            f[n].row(0, GOLD, 2)
        fr = f['north']
        for x in range(fr.w):
            fr.set(x, fr.h - 2, GOLD_D, 2)
        for y in range(4, 12):
            for x in range(18, 30):
                fr.set(x, y, shade(cab, 0.7), 3)
    m.cube('body', 'base', (-24, 0, -8), (24, 18, 8), cabinet)
    frame = solid(shade(cab, 0.85), 4, top=GOLD)
    m.cube('body', 'back', (-24, 18, 9), (24, 40, 11), solid((20, 18, 22), 3))
    m.cube('body', 'pillar_l', (-24, 18, -6), (-21, 40, 9), frame)
    m.cube('body', 'pillar_r', (21, 18, -6), (24, 40, 9), frame)
    m.cube('body', 'beam_bottom', (-21, 18, -6), (21, 21, 9), solid(GOLD_D, 3))
    m.cube('body', 'beam_top', (-21, 37, -6), (21, 40, 9), solid(GOLD_D, 3))
    for i in (0, 1):
        m.cube('body', f'divider{i}', (-7 + 13 * i, 21, -5), (-6 + 13 * i, 37, 8), solid((40, 36, 40), 3))

    def sign(f):
        for n in f:
            f[n].fill(ROUL_RED, 4)
        fr = f['north']
        fr.border(GOLD)
        star = ['...#...', '..###..', '#######', '.#####.', '.##.##.']
        for y, row in enumerate(star):
            for x, ch in enumerate(row):
                if ch == '#':
                    fr.set(fr.w // 2 - 3 + x, 2 + y, GOLD, 2)
        for x in range(3, fr.w - 3):
            if x % 3 != 0:
                fr.set(x, fr.h - 2, GOLD, 2)
    m.cube('body', 'sign', (-20, 40, -2), (20, 50, 6), sign)
    m.group('lamp_off', 'body', (0, 50, 2))
    m.cube('lamp_off', 'lamp_off_c', (-2, 50, 0), (2, 54, 4), solid((90, 70, 30), 3))
    m.group('lamp_on', 'body', (0, 50, 2))
    m.cube('lamp_on', 'lamp_on_c', (-2, 50, 0), (2, 54, 4), solid((255, 220, 90), 2), inflate=0.1)
    # Рычаг слева (если смотреть на автомат спереди — справа от игрока).
    m.group('lever', 'body', (-25, 24, 2))
    m.cube('lever', 'lever_arm', (-26, 24, 1.5), (-25, 36, 2.5), solid((170, 170, 176), 3))
    m.cube('lever', 'lever_knob', (-27, 36, 0.5), (-24, 39, 3.5), solid(ROUL_RED, 3, top=shade(ROUL_RED, 1.2)))
    m.cube('body', 'lever_base', (-26, 22, 0), (-24, 26, 4), solid(GOLD_D, 2))
    for i, rx in enumerate(REEL_X):
        reel = m.group(f'reel{i}', None, (rx, REEL_Y, REEL_Z))
        apothem = 6.93
        for k in range(6):
            g = m.group(f'plate{i}_{k}', reel, (rx, REEL_Y, REEL_Z), (-k * 60, 0, 0))
            z0 = REEL_Z - apothem - 1
            m.cube(g, f'plate{i}_{k}_c', (rx - PLATE_W / 2, REEL_Y - PLATE_H / 2, z0),
                   (rx + PLATE_W / 2, REEL_Y + PLATE_H / 2, z0 + 1), plate_painter(k))
    return m


PIPS = {
    1: [(2, 2)], 2: [(0, 0), (4, 4)], 3: [(0, 0), (2, 2), (4, 4)], 4: [(0, 0), (4, 0), (0, 4), (4, 4)],
    5: [(0, 0), (4, 0), (2, 2), (0, 4), (4, 4)], 6: [(0, 0), (4, 0), (0, 2), (4, 2), (0, 4), (4, 4)],
}
DIE_SPOTS = [(-17.6, -2.4), (-8, 2.4), (9.6, -2.4), (19.2, 2.4)]


def die_painter(value, body, pip):
    sides = [v for v in (2, 3, 4, 5, 1, 6) if v not in (value, 7 - value)]

    def face(fc, v):
        fc.fill(body, 2)
        for x, y in PIPS[v]:
            fc.set(x, y, pip, 1)

    def paint(f):
        face(f['up'], value)
        face(f['down'], 7 - value)
        for n, v in zip(('north', 'east', 'south', 'west'), sides):
            face(f[n], v)
    return paint


def dice_set():
    """Четыре кости на столе (белые — игрока, красные — дома). У каждой кости die_i шесть вариантов die_i_v
    (сверху — очко v); в игре виден один — выпавшее очко. Бросок — анимация animation.dice.throw (из руки крупье)."""
    m = Model('dice_set', 1066, height=256)
    m.group('dice', None, (0, 0, 0))
    for i, (x, z) in enumerate(DIE_SPOTS):
        body = (240, 238, 230) if i < 2 else ROUL_RED
        pip = (20, 20, 24) if i < 2 else (245, 245, 240)
        g = m.group(f'die{i}', 'dice', (x, 2.5, z))
        for v in range(1, 7):
            gv = m.group(f'die{i}_{v}', g, (x, 2.5, z))
            m.cube(gv, f'die{i}_{v}_c', (x - 2.5, 0, z - 2.5), (x + 2.5, 5, z + 2.5), die_painter(v, body, pip))
    return m


if __name__ == '__main__':
    for fn in (poker_table, roulette_wheel, slot_machine, dice_set):
        m = fn()
        m.save()
        print(m.id, len(m.cubes), 'кубов, UV до y =', m.shelf[1] + m.shelf[2])
    os.makedirs(OUT, exist_ok=True)
    cards_atlas().save(os.path.join(OUT, 'cards.png'))
    print('cards.png')
