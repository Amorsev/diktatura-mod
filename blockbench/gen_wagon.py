"""Повозка госперевозки для Blockbench (формат «Модовая сущность»), текстура 128x256.

Деревянная телега-рикша: кузов из досок с красной полосой и звездой, два колеса (крутятся в игре),
оглобли вперёд — в них впрягается заключённый (модель prisoner из gen_npcs.py), флажок на корме.
Координаты Blockbench: Y вверх, пол — 0, перёд — -Z.

Запуск: python blockbench/gen_wagon.py → blockbench/out/wagon.png, wagon.js
"""
import math

from gen_npcs import Model, SIDES, GOLD, RED, hexc, shade, solid

WOOD = hexc('#8A5E34')
WOOD_D = hexc('#5E3E22')
WOOD_L = hexc('#A87A48')
IRON = hexc('#4A4A52')


STAR5 = ['..#..', '#####', '.###.', '.#.#.']


def draw_star(face, x0, y0):
    for j, row in enumerate(STAR5):
        for i, ch in enumerate(row):
            if ch == '#':
                face.set(x0 + i, y0 + j, GOLD, 2)


def planks(horizontal=True, stripe=False, star=False):
    """Доски: волокна, тёмные стыки; по желанию — красная полоса и жёлтая звезда по бортам."""
    def paint(f):
        for n, face in f.items():
            for y in range(face.h):
                for x in range(face.w):
                    k = (y if horizontal else x)
                    c = WOOD_D if k % 3 == 2 else (WOOD_L if (x * 7 + y * 3) % 11 == 0 else WOOD)
                    face.set(x, y, c, 6)
            if stripe and n in ('east', 'west') and face.h >= 4:
                face.row(1, RED, 3)
                face.row(2, RED, 3)
                if star and face.w >= 9:
                    draw_star(face, face.w // 2 - 2, 0)
    return paint


def wheel_face(ou=0, ov=0):
    """Колесо: обод, восемь спиц и ступица — на боковых гранях (east/west), торцы — железная шина.
    Колесо 12x12 собрано из четырёх брусков; (ou, ov) — где этот брусок лежит в круге колеса."""
    return lambda f: _wheel(f, ou, ov)


def _wheel(f, ou, ov):
    r = 6.0
    for n, face in f.items():
        if n in ('east', 'west'):
            for y in range(face.h):
                for x in range(face.w):
                    dx, dy = x + ou + 0.5 - r, y + ov + 0.5 - r
                    d = math.hypot(dx, dy)
                    ang = (math.degrees(math.atan2(dy, dx)) + 360) % 45
                    if d > r - 1.2:
                        c = IRON
                    elif d > r - 2.4:
                        c = WOOD_D
                    elif d < 1.6:
                        c = IRON
                    elif ang < 9 or ang > 36:
                        c = WOOD_L
                    else:
                        c = shade(WOOD_D, 0.7)
                    face.set(x, y, c, 4)
        else:
            face.fill(IRON, 5)


def wagon():
    m = Model('wagon', 1937, height=256)
    m.group('body', None, (0, 0, 0))
    # Кузов.
    m.cube('body', 'floor', (-10, 8, -8), (10, 10, 20), planks(horizontal=False))
    m.cube('body', 'side_right', (9, 10, -8), (10, 16, 20), planks(stripe=True, star=True))
    m.cube('body', 'side_left', (-10, 10, -8), (-9, 16, 20), planks(stripe=True, star=True))
    m.cube('body', 'back', (-9, 10, 19), (9, 16, 20), planks(stripe=True))
    m.cube('body', 'front', (-9, 10, -8), (9, 13, -7), planks())
    m.cube('body', 'seat', (-8, 12, 9), (8, 14, 14), planks(horizontal=False))
    m.cube('body', 'seat_back', (-8, 14, 13), (8, 19, 14), planks())
    m.cube('body', 'axle', (-12, 5, 5), (12, 7, 7), solid(IRON, 4))
    # Оглобли: прямо в руки заключённому, с поперечиной впереди.
    m.cube('body', 'shaft_right', (6, 11, -30), (7, 12, -8), solid(WOOD, 5, top=WOOD_L))
    m.cube('body', 'shaft_left', (-7, 11, -30), (-6, 12, -8), solid(WOOD, 5, top=WOOD_L))
    m.cube('body', 'crossbar', (-7, 11, -31), (7, 12, -30), solid(WOOD_D, 5))
    # Флажок госперевозки на корме.
    m.cube('body', 'pole', (-9.5, 16, 18), (-8.5, 32, 19), solid(WOOD_D, 4, top=GOLD))
    m.cube('body', 'flag', (-8.5, 26, 18), (-1.5, 31, 19), solid(RED, 4,
           front=lambda fr: draw_star(fr, 1, 0)))
    # Колёса: по два квадратных обода (второй повёрнут на 45°) — восьмиугольник со спицами.
    # Колёса: восьмиугольник из четырёх брусков (крест + крест, повёрнутый на 45°).
    for side, x in (('right', 11), ('left', -12)):
        g = m.group(f'wheel_{side}', None, (x + 0.5, 6, 6))
        m.cube(g, f'spoke_v_{side}', (x, 0, 3.5), (x + 1, 12, 8.5), wheel_face(4, 0))
        m.cube(g, f'spoke_h_{side}', (x, 3.5, 0), (x + 1, 8.5, 12), wheel_face(0, 4))
        g2 = m.group(f'wheel_{side}_r', g, (x + 0.5, 6, 6), (45, 0, 0))
        m.cube(g2, f'spoke_v_{side}_r', (x, 0, 3.5), (x + 1, 12, 8.5), wheel_face(4, 0))
        m.cube(g2, f'spoke_h_{side}_r', (x, 3.5, 0), (x + 1, 8.5, 12), wheel_face(0, 4))
    return m


if __name__ == '__main__':
    w = wagon()
    w.save()
    print(w.id, len(w.cubes), 'кубов, UV до y =', w.shelf[1] + w.shelf[2])
