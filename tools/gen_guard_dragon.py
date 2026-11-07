"""Текстуры надстройки дракона госохраны (64x64): guard_dragon.png и guard_dragon_glow.png.
Раскладка UV совпадает с GuardDragonModel. Запуск: python tools/gen_guard_dragon.py"""
import random
from PIL import Image

random.seed(1945)
W = H = 64
base = Image.new('RGBA', (W, H), (0, 0, 0, 0))
glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
B, G = base.load(), glow.load()


def jitter(c, n=10):
    return tuple(max(0, min(255, v + random.randint(-n, n))) for v in c[:3]) + (255,)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def box(u, v, w, h, d):
    """Клетки развёртки куба: (x, y, грань, t по высоте 0..1)."""
    for y in range(v, v + d):
        for x in range(u + d, u + d + 2 * w):
            yield x, y, 'top' if x < u + d + w else 'bottom', 0.0
    for y in range(v + d, v + d + h):
        for x in range(u, u + 2 * (w + d)):
            if x < u + d:
                f = 'right'
            elif x < u + d + w:
                f = 'front'
            elif x < u + 2 * d + w:
                f = 'left'
            else:
                f = 'back'
            yield x, y, f, (y - v - d) / max(1, h - 1)


def horn(u, v, w, h, d, c_tip, c_base, tip_is_top=True):
    for x, y, f, t in box(u, v, w, h, d):
        # t=0 — верх развёртки (конец рога), t=1 — основание
        c = lerp(c_tip, c_base, t if tip_is_top else 1 - t)
        if f in ('right', 'left'):
            c = lerp(c, (0, 0, 0), 0.18)
        if (y - v) % 3 == 0 and f != 'top':
            c = lerp(c, (0, 0, 0), 0.12)  # кольца на роге
        B[x, y] = jitter(c, 6)


# Большой рог 3x13x3 (0,0): от тёмного основания к фиолетово-серому верху.
horn(0, 0, 3, 13, 3, (78, 64, 96), (24, 22, 30))
# Золотой конец рога 2x9x2 (12,0).
horn(12, 0, 2, 9, 2, (255, 228, 120), (196, 140, 32))
# Боковой рог 2x2x8 (20,0): тёмный, вдоль длины одинаковый.
for x, y, f, t in box(20, 0, 2, 2, 8):
    B[x, y] = jitter(lerp((30, 26, 36), (70, 58, 86), random.random() * 0.5), 5)

# Кокарда 5x5x1 (40,0): золотой ободок, красное поле, жёлтая звезда на лицевой грани.
STAR = ["..#..", ".###.", "#####", ".###.", "#.#.#"]
for x, y, f, t in box(40, 0, 5, 5, 1):
    B[x, y] = jitter((210, 160, 40), 8)
for j in range(5):
    for i in range(5):
        x, y = 41 + i, 1 + j
        c = (255, 222, 70) if STAR[j][i] == '#' else (176, 22, 22)
        B[x, y] = jitter(c, 6)
        G[x, y] = (0, 0, 0, 255)


def crystal(u, v, w, h, d):
    for x, y, f, t in box(u, v, w, h, d):
        fx = (x - u) % (w + d)
        facet = 1.0 if fx in (0, w + d - 1) else 0.0
        bright = 1 - t * 0.55  # верх кристалла ярче
        c = lerp((70, 36, 96), (186, 150, 220), bright * 0.7)
        if facet:
            c = lerp(c, (20, 10, 30), 0.45)
        if f == 'top':
            c = (225, 205, 245)
        B[x, y] = jitter(c, 8)
        gl = int(255 * (0.45 + 0.55 * bright) * (0.55 if facet else 1.0))
        G[x, y] = (gl, gl, gl, 255)


crystal(0, 16, 4, 11, 4)   # большой кристалл спины
crystal(16, 16, 3, 6, 3)   # малый кристалл спины
crystal(28, 16, 2, 4, 2)   # кристалл гребня шеи и хвоста

# Повязка госохраны 8x4x8 (0,32): красная, золотые кромки, золотая звезда на каждой боковой грани.
for x, y, f, t in box(0, 32, 8, 4, 8):
    c = (168, 20, 20)
    if f not in ('top', 'bottom') and (y in (40, 43)):
        c = (212, 166, 42)
    B[x, y] = jitter(c, 7)
MINI = [".#.", "#.#"]
for side_u in (0, 8, 16, 24):  # четыре боковые грани по 8 px
    for j in range(2):
        for i in range(3):
            if MINI[j][i] == '#':
                B[side_u + 3 + i, 41 + j] = jitter((255, 220, 70), 5)

base.save('src/main/resources/assets/diktatura/textures/entity/guard_dragon.png')
glow.save('src/main/resources/assets/diktatura/textures/entity/guard_dragon_glow.png')
print('ok')
