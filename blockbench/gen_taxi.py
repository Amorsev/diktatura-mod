"""Кареты госТакси для Blockbench (формат «Модовая сущность»): три класса, лошади — часть модели.

  taxi_economy  — «Телега трудящегося»: открытая деревянная телега с сеном, одна кляча;
  taxi_comfort  — «Повозка служащего»: крытая зелёная карета, пара гнедых, фонари;
  taxi_business — «Карета номенклатуры»: чёрная лакированная карета с золотом, четвёрка вороных
                  с красными плюмажами, кучер на козлах.

У всех трёх — шашечки такси (чёрно-жёлтые клетки) на бортах.
Кости для анимации в игре: wheel_* (крутятся), <лошадь>_leg_* (шагают), <лошадь>_neck и <лошадь>_tail.
Координаты Blockbench: Y вверх, земля — 0, перёд — -Z. Пассажир сидит по координатам SEAT (в пикселях модели).

Запуск: python blockbench/gen_taxi.py → blockbench/out/taxi_*.png, taxi_*.js
"""
import math

from gen_npcs import (BLACK, GOLD, GOLD_D, RED, WHITE, Model, faces, head_painter, hexc, shade, solid)

WOOD = hexc('#8A5E34')
WOOD_D = hexc('#5E3E22')
WOOD_L = hexc('#A87A48')
IRON = hexc('#4A4A52')
HAY = hexc('#D8B54A')
HAY_D = hexc('#B08A2E')
CHECK_Y = hexc('#F2C230')
GLASS = hexc('#FFE38A')

# Место пассажира (центр сиденья, пиксели модели) — для рендера/посадки в игре.
SEAT = {
    'taxi_economy': (0, 17, 15),
    'taxi_comfort': (0, 16, 21),
    'taxi_business': (0, 17, 23),
}


class TaxiModel(Model):
    """Model с общей развёрткой: одинаковые детали (лошади одной упряжки) делят один участок текстуры."""

    def __init__(self, *a, **kw):
        super().__init__(*a, **kw)
        self._shared = {}

    def cube(self, group, name, frm, to, paint, inflate=0.0, key=None):
        if key is not None and key in self._shared:
            size, uv = self._shared[key]
            now = tuple(round(to[i] - frm[i]) for i in range(3))
            if now != size:
                raise ValueError(f'{self.id}/{name}: общая развёртка {key} другого размера')
            self.cubes.append({'group': group, 'name': name, 'from': list(frm), 'to': list(to),
                               'uv': list(uv), 'inflate': inflate})
            return
        super().cube(group, name, frm, to, paint, inflate)
        if key is not None:
            c = self.cubes[-1]
            self._shared[key] = (tuple(round(to[i] - frm[i]) for i in range(3)), tuple(c['uv']))


# ---------------------------------------------------------------- краски

def planks(base=WOOD, dark=WOOD_D, light=WOOD_L, horizontal=True, patch=False):
    def paint(f):
        for face in f.values():
            for y in range(face.h):
                for x in range(face.w):
                    k = (y if horizontal else x)
                    c = dark if k % 3 == 2 else (light if (x * 7 + y * 3) % 11 == 0 else base)
                    face.set(x, y, c, 6)
            if patch and face.w >= 8 and face.h >= 3:
                # Заплатка: доска другого оттенка с гвоздями — телега видала виды.
                for y in range(1, min(3, face.h)):
                    for x in range(face.w // 3, face.w // 3 + 4):
                        face.set(x, y, shade(base, 0.75), 4)
                face.set(face.w // 3, 1, IRON, 1)
                face.set(face.w // 3 + 3, 1, IRON, 1)
    return paint


def checker(face, y0, rows, c1=BLACK, c2=CHECK_Y, x0=0, x1=None):
    """Шашечки такси: два ряда клеток 2x1."""
    x1 = face.w if x1 is None else x1
    for r in range(rows):
        for x in range(x0, x1):
            face.set(x, y0 + r, c1 if ((x // 2) + r) % 2 == 0 else c2, 2)


def panel(base, trim, check_rows=0, check_y=None, door=False, star=False):
    """Борт кареты: краска, кант по краю, шашечки, дверь с ручкой."""
    def paint(f):
        for n, face in f.items():
            face.fill(base, 4)
            if n in ('east', 'west', 'north', 'south') and face.w >= 3 and face.h >= 3:
                face.border(trim)
                if check_rows:
                    checker(face, check_y if check_y is not None else face.h - 1 - check_rows, check_rows,
                            x0=1, x1=face.w - 1)
                if door and n in ('east', 'west') and face.w >= 12:
                    cx = face.w // 2
                    for y in range(1, face.h - 1):
                        face.set(cx - 4, y, trim, 2)
                        face.set(cx + 4, y, trim, 2)
                    face.set(cx + 2, face.h // 2, GOLD, 1)
                    if star:
                        for dx, dy in ((0, -1), (-1, 0), (0, 0), (1, 0), (-1, 1), (1, 1)):
                            face.set(cx + dx, 2 + dy + 1, GOLD, 1)
    return paint


def horse_body(coat, harness, buckle):
    def paint(f):
        for n, face in f.items():
            face.fill(coat, 6)
        f['down'].fill(shade(coat, 0.8), 4)
        # Хомут у шеи и подпруга посередине — по бокам и сверху.
        for n in ('east', 'west'):
            face = f[n]
            for col in (2, 3, 11, 12):
                c = col if n == 'east' else face.w - 1 - col
                face.col(c, harness, 2)
            c = 12 if n == 'east' else face.w - 13
            face.set(c, face.h // 2, buckle, 1)
        up = f['up']
        for r in (2, 3, 11, 12):
            up.row(r, harness, 2)
        # Яблоки на крупе — для живости.
        for n in ('east', 'west'):
            face = f[n]
            for (x, y) in ((17, 2), (19, 4), (16, 5)):
                xx = x if n == 'east' else face.w - 1 - x
                face.set(xx, y, shade(coat, 1.12), 2)
    return paint


def horse_leg(coat, hoof):
    def paint(f):
        for face in f.values():
            face.fill(coat, 5)
        for n in ('east', 'west', 'north', 'south'):
            face = f[n]
            face.fill(hoof, 3, face.h - 2, face.h)
            face.row(face.h - 3, shade(coat, 1.25), 3)  # «щётка» над копытом
        f['down'].fill(hoof, 2)
    return paint


def horse_head(coat, harness, blaze=None):
    def paint(f):
        for face in f.values():
            face.fill(coat, 5)
        nose = f['north']
        nose.fill(shade(coat, 0.7), 3, nose.h - 2, nose.h)
        nose.set(1, nose.h - 2, BLACK, 1)
        nose.set(nose.w - 2, nose.h - 2, BLACK, 1)
        if blaze:
            nose.col(nose.w // 2, blaze, 2, 0, nose.h - 2)
            f['up'].col(f['up'].w // 2, blaze, 2, 0, 3)
        # Глаза и уздечка по бокам.
        for n in ('east', 'west'):
            face = f[n]
            ex = 2 if n == 'east' else face.w - 3
            face.set(ex, 1, BLACK, 1)
            face.set(ex + (1 if n == 'east' else -1), 1, WHITE, 1)
            face.row(3, harness, 2)
            face.col(1 if n == 'east' else face.w - 2, harness, 2)
    return paint


def wheel(rim, spoke, hub, d):
    """Колесо диаметра d из двух брусков 1x5xd (и копии под 45°): обод, спицы, ступица на боковых гранях."""
    r = d / 2

    def make(ou, ov):
        def paint(f):
            for n, face in f.items():
                if n in ('east', 'west'):
                    for y in range(face.h):
                        for x in range(face.w):
                            dx, dy = x + ou + 0.5 - r, y + ov + 0.5 - r
                            dist = math.hypot(dx, dy)
                            ang = (math.degrees(math.atan2(dy, dx)) + 360) % 45
                            if dist > r - 1.2:
                                c = rim
                            elif dist > r - 2.2:
                                c = shade(rim, 0.75)
                            elif dist < 1.6:
                                c = hub
                            elif ang < 9 or ang > 36:
                                c = spoke
                            else:
                                c = shade(spoke, 0.45)
                            face.set(x, y, c, 3)
                else:
                    face.fill(rim, 4)
        return paint
    return make


# ---------------------------------------------------------------- детали

def horse(m, p, cx, cz, coat, mane, harness, buckle, hoof=(40, 36, 34), blaze=None, plume=None):
    """Лошадь, смотрит на -Z. Корпус в кости <p>, ноги/шея/хвост — свои кости для анимации."""
    m.group(p, 'body', (cx, 16, cz))
    m.cube(p, f'{p}_torso', (cx - 5, 11, cz - 11), (cx + 5, 21, cz + 11), horse_body(coat, harness, buckle),
           key='horse_body')
    for leg, lx, lz in (('fr', cx + 3, cz - 8), ('fl', cx - 3, cz - 8), ('br', cx + 3, cz + 8), ('bl', cx - 3, cz + 8)):
        g = m.group(f'{p}_leg_{leg}', 'body', (lx, 11, lz))
        m.cube(g, f'{p}_leg_{leg}_c', (lx - 2, 0, lz - 2), (lx + 2, 11, lz + 2), horse_leg(coat, hoof), key='horse_leg')
    neck = m.group(f'{p}_neck', 'body', (cx, 19, cz - 9), (-30, 0, 0))
    m.cube(neck, f'{p}_neck_c', (cx - 2, 17, cz - 13), (cx + 2, 29, cz - 6), solid(coat, 5), key='horse_neck')
    m.cube(neck, f'{p}_mane', (cx - 1, 18, cz - 6), (cx + 1, 31, cz - 4), solid(mane, 6), key='horse_mane')
    m.cube(neck, f'{p}_head', (cx - 2.5, 24, cz - 20), (cx + 2.5, 29, cz - 13), horse_head(coat, harness, blaze),
           key='horse_head')
    m.cube(neck, f'{p}_ear_r', (cx + 0.5, 29, cz - 14), (cx + 1.5, 31, cz - 13), solid(coat, 3), key='horse_ear')
    m.cube(neck, f'{p}_ear_l', (cx - 1.5, 29, cz - 14), (cx - 0.5, 31, cz - 13), solid(coat, 3), key='horse_ear')
    if plume:
        m.cube(neck, f'{p}_plume', (cx - 0.5, 29, cz - 17), (cx + 0.5, 34, cz - 15), solid(plume, 6, top=GOLD),
               key='horse_plume')
    tail = m.group(f'{p}_tail', 'body', (cx, 20, cz + 11), (-25, 0, 0))
    m.cube(tail, f'{p}_tail_c', (cx - 1.5, 9, cz + 11), (cx + 1.5, 20, cz + 14), solid(mane, 7), key='horse_tail')


def wheel_pair(m, x_right, x_left, cy, cz, d, rim, spoke, hub, tag):
    paint = wheel(rim, spoke, hub, d)
    off = (d - 5) / 2
    for side, x in (('right', x_right), ('left', x_left)):
        g = m.group(f'wheel_{tag}_{side}', 'body', (x + 0.5, cy, cz))
        m.cube(g, f'wheel_{tag}_{side}_v', (x, cy - d / 2, cz - 2.5), (x + 1, cy + d / 2, cz + 2.5), paint(off, 0),
               key=f'wheel_{tag}_v')
        m.cube(g, f'wheel_{tag}_{side}_h', (x, cy - 2.5, cz - d / 2), (x + 1, cy + 2.5, cz + d / 2), paint(0, off),
               key=f'wheel_{tag}_h')
        g2 = m.group(f'wheel_{tag}_{side}_r', g, (x + 0.5, cy, cz), (45, 0, 0))
        m.cube(g2, f'wheel_{tag}_{side}_vr', (x, cy - d / 2, cz - 2.5), (x + 1, cy + d / 2, cz + 2.5), paint(off, 0),
               key=f'wheel_{tag}_v')
        m.cube(g2, f'wheel_{tag}_{side}_hr', (x, cy - 2.5, cz - d / 2), (x + 1, cy + 2.5, cz + d / 2), paint(0, off),
               key=f'wheel_{tag}_h')
    m.cube('body', f'axle_{tag}', (x_left, cy - 1, cz - 1), (x_right + 1, cy + 1, cz + 1), solid(IRON, 4))


def lantern(m, name, x, y, z, metal, key):
    m.cube('body', name, (x - 1, y, z - 1), (x + 1, y + 4, z + 1),
           solid(GLASS, 6, top=metal, bottom=metal), key=key)
    m.cube('body', name + '_cap', (x - 1.5, y + 4, z - 1.5), (x + 1.5, y + 5, z + 1.5), solid(metal, 3), key=key + '_cap')


# ---------------------------------------------------------------- три класса

def taxi_economy():
    m = TaxiModel('taxi_economy', 4101, height=256)
    m.group('body', None, (0, 0, 0))
    coat, mane = hexc('#8C7A64'), hexc('#4A3F33')
    horse(m, 'horse', 0, -24, coat, mane, WOOD_D, IRON, hoof=(52, 46, 40))
    # Телега.
    m.cube('body', 'floor', (-10, 9, 0), (10, 11, 26), planks(horizontal=False))
    m.cube('body', 'side_right', (9, 11, 0), (10, 15, 26), planks(patch=True))
    m.cube('body', 'side_left', (-10, 11, 0), (-9, 15, 26), planks())
    m.cube('body', 'front', (-9, 11, 0), (9, 14, 1), planks())

    def back_board(f):
        planks()(f)
        checker(f['south'], 1, 2, x0=1, x1=f['south'].w - 1)  # шашечки, намалёванные от руки
        checker(f['north'], 1, 2, x0=1, x1=f['north'].w - 1)
    m.cube('body', 'back', (-9, 11, 25), (9, 15, 26), back_board)

    def hay(f):
        for face in f.values():
            for y in range(face.h):
                for x in range(face.w):
                    c = HAY_D if (x * 5 + y * 7) % 9 == 0 else (shade(HAY, 1.1) if (x + y * 3) % 7 == 0 else HAY)
                    face.set(x, y, c, 10)
    m.cube('body', 'hay', (-8, 11, 6), (8, 15, 24), hay)
    m.cube('body', 'hay_top', (-6, 15, 9), (6, 17, 21), hay)
    # Оглобли и верёвочная упряжь.
    m.cube('body', 'shaft_right', (6, 12, -34), (7, 13, 0), solid(WOOD, 5, top=WOOD_L))
    m.cube('body', 'shaft_left', (-7, 12, -34), (-6, 13, 0), solid(WOOD, 5, top=WOOD_L))
    m.cube('body', 'rope', (-6, 17, -33), (6, 18, -32), solid(hexc('#B79B6B'), 6))
    wheel_pair(m, 11, -12, 6, 15, 12, IRON, WOOD_L, IRON, 'rear')
    return m


def taxi_comfort():
    m = TaxiModel('taxi_comfort', 4202, height=512)
    m.group('body', None, (0, 0, 0))
    coat, mane = hexc('#7A4A2A'), hexc('#2E1E14')
    harness, brass = hexc('#231E1E'), hexc('#C9A24A')
    for side, cx in (('r', 6), ('l', -6)):
        horse(m, f'horse_{side}', cx, -27, coat, mane, harness, brass, blaze=WHITE if side == 'r' else None)
    green, trim = hexc('#2F5D3A'), brass
    m.cube('body', 'pole', (-1, 12, -38), (1, 13, -2), solid(WOOD_D, 4))
    m.cube('body', 'swingletree', (-11, 12, -14), (11, 13, -13), solid(WOOD_D, 4))
    # Кузов: нижние борта, стойки окон, крыша.
    m.cube('body', 'floor', (-11, 10, 0), (11, 12, 28), solid(WOOD_D, 4))
    m.cube('body', 'wall_right', (10, 12, 0), (11, 22, 28), panel(green, trim, 2, 6, door=True))
    m.cube('body', 'wall_left', (-11, 12, 0), (-10, 22, 28), panel(green, trim, 2, 6, door=True))
    m.cube('body', 'wall_front', (-10, 12, 0), (10, 22, 1), panel(green, trim, 2, 6))
    m.cube('body', 'wall_back', (-10, 12, 27), (10, 22, 28), panel(green, trim, 2, 6))
    for name, frm, to in (('post_fr', (10, 22, 0), (11, 30, 1)), ('post_fl', (-11, 22, 0), (-10, 30, 1)),
                          ('post_br', (10, 22, 27), (11, 30, 28)), ('post_bl', (-11, 22, 27), (-10, 30, 28)),
                          ('post_mr', (10, 22, 13), (11, 30, 15)), ('post_ml', (-11, 22, 13), (-10, 30, 15))):
        m.cube('body', name, frm, to, solid(green, 4), key='comfort_post' + str(round(to[2] - frm[2])))
    m.cube('body', 'roof', (-12, 30, -1), (12, 32, 29), solid(green, 4, top=shade(green, 0.85)))
    m.cube('body', 'roof_top', (-10, 32, 1), (10, 33, 27), solid(shade(green, 0.8), 4))
    m.cube('body', 'rack', (-8, 33, 4), (8, 35, 24), solid(hexc('#4A2E1C'), 6, top=hexc('#5C3A24')))
    # Сиденье внутри — его видно в окна.
    m.cube('body', 'seat', (-9, 12, 18), (9, 15, 26), solid(hexc('#7A1F25'), 5))
    m.cube('body', 'seat_back', (-9, 15, 25), (9, 21, 27), solid(hexc('#7A1F25'), 5))
    # Козлы кучера и фонари.
    m.cube('body', 'driver_box', (-9, 16, -6), (9, 22, 0), panel(green, trim))
    m.cube('body', 'driver_seat', (-9, 22, -5), (9, 23, 0), solid(BLACK, 3))
    lantern(m, 'lantern_r', 12, 23, 0, brass, 'comfort_lantern')
    lantern(m, 'lantern_l', -12, 23, 0, brass, 'comfort_lantern')
    wheel_pair(m, 11, -12, 5, 6, 10, IRON, hexc('#B8932F'), brass, 'front')
    wheel_pair(m, 11, -12, 6, 22, 12, IRON, hexc('#B8932F'), brass, 'rear')
    return m


def taxi_business():
    m = TaxiModel('taxi_business', 4303, height=512)
    m.group('body', None, (0, 0, 0))
    coat, mane = hexc('#1E1A1C'), hexc('#0C0A0B')
    harness, gold = RED, GOLD
    for pair, cz in (('a', -27), ('b', -53)):
        for side, cx in (('r', 6), ('l', -6)):
            horse(m, f'horse_{pair}{side}', cx, cz, coat, mane, harness, gold, hoof=(20, 18, 18), plume=RED)
    lacquer = hexc('#151318')
    m.cube('body', 'pole', (-1, 12, -64), (1, 13, -2), solid(lacquer, 3, top=GOLD_D))
    m.cube('body', 'swingletree', (-11, 12, -14), (11, 13, -13), solid(lacquer, 3))
    m.cube('body', 'swingletree_b', (-11, 12, -40), (11, 13, -39), solid(lacquer, 3))
    m.cube('body', 'floor', (-12, 11, 0), (12, 13, 30), solid(lacquer, 3, bottom=GOLD_D))
    m.cube('body', 'wall_right', (11, 13, 0), (12, 24, 30), panel(lacquer, gold, 1, 8, door=True, star=True))
    m.cube('body', 'wall_left', (-12, 13, 0), (-11, 24, 30), panel(lacquer, gold, 1, 8, door=True, star=True))
    m.cube('body', 'wall_front', (-11, 13, 0), (11, 24, 1), panel(lacquer, gold, 1, 8))
    m.cube('body', 'wall_back', (-11, 13, 29), (11, 24, 30), panel(lacquer, gold, 1, 8))
    for name, frm, to in (('post_fr', (11, 24, 0), (12, 32, 1)), ('post_fl', (-12, 24, 0), (-11, 32, 1)),
                          ('post_br', (11, 24, 29), (12, 32, 30)), ('post_bl', (-12, 24, 29), (-11, 32, 30)),
                          ('post_mr', (11, 24, 14), (12, 32, 16)), ('post_ml', (-12, 24, 14), (-11, 32, 16))):
        m.cube('body', name, frm, to, solid(gold, 3), key='business_post' + str(round(to[2] - frm[2])))
    # Красные шторки, подвязанные к стойкам.
    for name, frm, to in (('curtain_r', (11.5, 25, 1), (11.5 + 1, 31, 3)), ('curtain_l', (-12.5, 25, 1), (-12.5 + 1, 31, 3))):
        m.cube('body', name, frm, to, solid(hexc('#9E1B24'), 5), key='business_curtain', inflate=-0.25)
    m.cube('body', 'roof', (-13, 32, -1), (13, 34, 31), solid(lacquer, 3, top=shade(lacquer, 1.3)))
    m.cube('body', 'roof_trim', (-12, 34, 0), (12, 35, 30), solid(gold, 3))
    m.cube('body', 'roof_top', (-10, 35, 2), (10, 36, 28), solid(lacquer, 3))
    for name, x, z in (('knob_fr', 11, 0), ('knob_fl', -12, 0), ('knob_br', 11, 29), ('knob_bl', -12, 29)):
        m.cube('body', name, (x, 35, z), (x + 1, 37, z + 1), solid(gold, 2), key='business_knob')

    def crest(f):
        solid(gold, 3)(f)
        for n in ('north', 'south'):
            face = f[n]
            for (x, y) in ((1, 1), (2, 0), (2, 1), (2, 2), (3, 1), (1, 3), (3, 3)):
                face.set(x, y, RED, 1)
    m.cube('body', 'crest', (-2.5, 36, 13), (2.5, 40, 17), crest)
    m.cube('body', 'seat', (-10, 13, 20), (10, 16, 28), solid(hexc('#8E1620'), 5, top=hexc('#A51E28')))
    m.cube('body', 'seat_back', (-10, 16, 27), (10, 23, 29), solid(hexc('#8E1620'), 5))
    m.cube('body', 'step', (-6, 9, 30), (6, 10, 34), solid(lacquer, 3, top=GOLD_D))
    # Козлы и кучер в ливрее.
    m.cube('body', 'driver_box', (-9, 17, -8), (9, 24, 0), panel(lacquer, gold))
    m.cube('body', 'driver_seat', (-9, 24, -6), (9, 25, 0), solid(RED, 3))
    m.group('coachman', 'body', (0, 25, -3))
    livery, cuff = hexc('#7E1622'), GOLD

    def coat_paint(f):
        solid(livery, 4)(f)
        fr = f['north']
        for y in range(1, fr.h - 1, 3):
            fr.set(fr.w // 2, y, cuff, 1)
        fr.row(fr.h - 1, BLACK, 2)
    m.cube('coachman', 'coachman_body', (-4, 25, -5), (4, 37, -1), coat_paint)
    m.cube('coachman', 'coachman_head', (-4, 37, -7), (4, 45, 1),
           head_painter(hexc('#3B2A20'), mustache=hexc('#3B2A20')))
    m.cube('coachman', 'coachman_hat', (-4, 45, -7), (4, 50, 1), solid(BLACK, 3, top=shade(BLACK, 1.3),
           front=lambda fr: fr.row(fr.h - 2, GOLD, 1)), inflate=0.3)
    m.cube('coachman', 'coachman_brim', (-5.5, 45, -8.5), (5.5, 46, 2.5), solid(BLACK, 2))
    m.cube('coachman', 'coachman_arm_r', (4, 30, -13), (8, 34, -5), solid(livery, 4, front=lambda fr: fr.fill(WHITE, 2)))
    m.cube('coachman', 'coachman_arm_l', (-8, 30, -13), (-4, 34, -5), solid(livery, 4, front=lambda fr: fr.fill(WHITE, 2)))
    m.cube('coachman', 'coachman_legs', (-4, 25, -13), (4, 29, -5), solid(BLACK, 3))
    m.cube('coachman', 'whip', (8, 30, -14), (9, 48, -13), solid(WOOD_D, 3, top=RED))
    lantern(m, 'lantern_r', 13, 24, 0, gold, 'business_lantern')
    lantern(m, 'lantern_l', -13, 24, 0, gold, 'business_lantern')
    wheel_pair(m, 12, -13, 5, 6, 10, GOLD_D, RED, GOLD, 'front')
    wheel_pair(m, 12, -13, 7, 24, 14, GOLD_D, RED, GOLD, 'rear')
    return m


MODELS = [taxi_economy, taxi_comfort, taxi_business]

if __name__ == '__main__':
    for make in MODELS:
        t = make()
        t.save()
        print(t.id, len(t.cubes), 'кубов, UV до y =', t.shelf[1] + t.shelf[2], 'из', t.height)
