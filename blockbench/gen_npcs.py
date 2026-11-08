"""Описание моделей НПС для Blockbench.

Скрипт собирает для каждого НПС: текстуру 128x128 (box UV) и JS-сценарий, который строит проект
«Модовая сущность» в Blockbench (кости как у гуманоида Minecraft: head, hat, body, right_arm,
left_arm, right_leg, left_leg + свои детали). Сценарий запускается в Blockbench, оттуда же
экспортируется Java-код модели (Yarn) и сохраняется .bbmodel.

Координаты — как в Blockbench: Y вверх, ступни на 0, лицо смотрит на -Z, +X — правая рука персонажа.

Запуск: python blockbench/gen_npcs.py  → blockbench/out/<id>.png, <id>.js
"""
import base64
import io
import json
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), 'out')
TEX = 128


def hexc(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, k):
    return tuple(max(0, min(255, round(v * k))) for v in c)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------- холст и развёртка куба

class Canvas:
    def __init__(self, seed, height=TEX):
        self.height = height
        self.img = Image.new('RGBA', (TEX, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)

    def put(self, x, y, c, noise=4):
        if 0 <= x < TEX and 0 <= y < self.height:
            n = self.rnd.randint(-noise, noise) if noise else 0
            self.px[x, y] = tuple(max(0, min(255, v + n)) for v in c[:3]) + (255,)


class Face:
    """Одна грань развёртки: рисуем в локальных координатах (0,0 — левый верх, как видит зритель)."""

    def __init__(self, cv, name, x0, y0, w, h):
        self.cv, self.name, self.x0, self.y0, self.w, self.h = cv, name, x0, y0, w, h

    def set(self, x, y, c, noise=3):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.cv.put(self.x0 + x, self.y0 + y, c, noise)

    def fill(self, c, noise=4, y0=0, y1=None, x0=0, x1=None):
        for y in range(y0, self.h if y1 is None else y1):
            for x in range(x0, self.w if x1 is None else x1):
                self.set(x, y, c, noise)

    def row(self, y, c, noise=3):
        self.fill(c, noise, y, y + 1)

    def col(self, x, c, noise=3, y0=0, y1=None):
        self.fill(c, noise, y0, y1, x, x + 1)

    def border(self, c):
        for x in range(self.w):
            self.set(x, 0, c)
            self.set(x, self.h - 1, c)
        for y in range(self.h):
            self.set(0, y, c)
            self.set(self.w - 1, y, c)


def faces(cv, u, v, w, h, d):
    """Развёртка box UV (как в Minecraft): north — лицевая грань (-Z)."""
    return {
        'up': Face(cv, 'up', u + d, v, w, d),
        'down': Face(cv, 'down', u + d + w, v, w, d),
        'east': Face(cv, 'east', u, v + d, d, h),
        'north': Face(cv, 'north', u + d, v + d, w, h),
        'west': Face(cv, 'west', u + d + w, v + d, d, h),
        'south': Face(cv, 'south', u + 2 * d + w, v + d, w, h),
    }


SIDES = ('east', 'north', 'west', 'south')


# ---------------------------------------------------------------- модель

class Model:
    def __init__(self, npc_id, seed=1, height=TEX):
        self.id = npc_id
        self.height = height
        self.cv = Canvas(seed, height)
        self.groups = []      # (name, parent, origin, rotation)
        self.cubes = []       # dict
        self.shelf = [0, 0, 0]  # x, y, высота полки — простая упаковка UV

    def group(self, name, parent, origin, rotation=(0, 0, 0)):
        self.groups.append((name, parent, list(origin), list(rotation)))
        return name

    def _alloc(self, fw, fh):
        x, y, sh = self.shelf
        if x + fw > TEX:
            x, y, sh = 0, y + sh, 0
        if y + fh > self.height:
            raise RuntimeError(f'{self.id}: не хватило места на текстуре')
        self.shelf = [x + fw, y, max(sh, fh)]
        return x, y

    def cube(self, group, name, frm, to, paint, inflate=0.0):
        size = [to[i] - frm[i] for i in range(3)]
        bad = [x for x in size if abs(x - round(x)) > 1e-6 or round(x) <= 0]
        if bad:
            raise ValueError(f'{self.id}/{name}: размеры куба должны быть целыми: {size}')
        w, h, d = (round(x) for x in size)
        u, v = self._alloc(2 * (w + d), d + h)
        self.cubes.append({'group': group, 'name': name, 'from': list(frm), 'to': list(to),
                           'uv': [u, v], 'inflate': inflate})
        paint(faces(self.cv, u, v, w, h, d))

    def save(self):
        os.makedirs(OUT, exist_ok=True)
        path = os.path.join(OUT, self.id + '.png')
        self.cv.img.save(path)
        buf = io.BytesIO()
        self.cv.img.save(buf, 'PNG')
        data = {'id': self.id, 'height': self.height, 'groups': self.groups, 'cubes': self.cubes,
                'texture': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}
        with open(os.path.join(OUT, self.id + '.js'), 'w', encoding='utf-8') as f:
            f.write(BUILD_JS.replace('__DATA__', json.dumps(data)))


BUILD_JS = r"""(() => {
const D = __DATA__;
Undo.initEdit({outliner: true, elements: [], textures: []});
[...Outliner.elements].forEach(e => e.remove());
[...Group.all].forEach(g => g.remove());
[...Texture.all].forEach(t => t.remove());
Project.name = D.id;
Project.geometry_name = D.id;
Project.modded_entity_version = '1.17_yarn';
Project.box_uv = true;
Project.texture_width = 128;
Project.texture_height = D.height || 128;
const tex = new Texture({name: D.id + '.png'}).fromDataURL(D.texture).add(false);
const groups = {};
for (const [name, parent, origin, rotation] of D.groups) {
  const g = new Group({name, origin, rotation});
  g.addTo(parent ? groups[parent] : undefined);
  g.init();
  groups[name] = g;
}
const added = [];
for (const c of D.cubes) {
  const cube = new Cube({name: c.name, from: c.from, to: c.to, uv_offset: c.uv, inflate: c.inflate, box_uv: true});
  cube.addTo(groups[c.group]);
  cube.init();
  cube.applyTexture(tex, true);
  added.push(cube);
}
Undo.finishEdit('Build NPC ' + D.id, {outliner: true, elements: added, textures: [tex]});
Canvas.updateAll();
return {id: D.id, groups: Group.all.length, cubes: Cube.all.length};
})()"""


# ---------------------------------------------------------------- общие детали гуманоида

SKIN = hexc('#E0A982')
SKIN_D = shade(SKIN, 0.82)
WHITE = (236, 236, 230)
BLACK = (24, 22, 26)
GOLD = hexc('#E3B23C')
GOLD_D = hexc('#A87C1E')
RED = hexc('#B3202A')


def biped_bones(m):
    m.group('head', None, (0, 24, 0))
    m.group('hat', None, (0, 24, 0))
    m.group('body', None, (0, 24, 0))
    m.group('right_arm', None, (5, 22, 0))
    m.group('left_arm', None, (-5, 22, 0))
    m.group('right_leg', None, (1.9, 12, 0))
    m.group('left_leg', None, (-1.9, 12, 0))


def head_painter(hair, eyes=(40, 60, 110), mustache=None, brows=None, beard=None, skin=SKIN, hair_rows=2,
                 glasses=None, lips=None, side_hair=True, bald=False):
    brows = brows or shade(hair, 0.8)

    def paint(f):
        for n in SIDES + ('down',):
            f[n].fill(skin, 5)
        f['down'].fill(SKIN_D, 3)
        top = f['up']
        top.fill(skin if bald else hair, 6)
        fr = f['north']
        if not bald:
            fr.fill(hair, 5, 0, hair_rows)
            for n in ('east', 'west'):
                f[n].fill(hair, 5, 0, hair_rows + 1)
                if side_hair:
                    f[n].fill(hair, 5, 0, 5, x0=5)  # к затылку волосы ниже
            f['south'].fill(hair, 5, 0, 6)
        # брови и глаза
        for x in (1, 2, 5, 6):
            fr.set(x, 3, brows)
        for x0 in (1, 5):
            fr.set(x0, 4, WHITE, 2)
            fr.set(x0 + 1, 4, eyes, 2)
        fr.set(3, 4, skin)
        fr.set(4, 4, skin)
        # нос и рот
        fr.set(3, 5, SKIN_D, 2)
        fr.set(4, 5, SKIN_D, 2)
        for x in (3, 4):
            fr.set(x, 6, lips or shade(skin, 0.7), 2)
        if mustache:
            for x in range(2, 6):
                fr.set(x, 6, mustache)
            fr.set(1, 7, mustache)
            fr.set(6, 7, mustache)
        if beard:
            fr.fill(beard, 4, 7, 8)
            fr.col(0, beard, 4, 5)
            fr.col(7, beard, 4, 5)
        if glasses:
            for x0 in (1, 5):
                fr.set(x0 - (1 if x0 == 1 else 0), 4, glasses, 1)
            fr.set(3, 4, glasses, 1)
            fr.set(4, 4, glasses, 1)
            fr.set(7, 4, glasses, 1)
            f['east'].set(7, 4, glasses, 1)
            f['west'].set(0, 4, glasses, 1)
    return paint


def torso_painter(jacket, shirt=WHITE, tie=None, belt=None, buttons=None, lapels=True, open_collar=True,
                  extra=None):
    def paint(f):
        for n in SIDES + ('up', 'down'):
            f[n].fill(jacket, 5)
        fr = f['north']
        if open_collar:
            for y in range(0, 4):
                for x in range(3 - min(y, 1), 5 + min(y, 1)):
                    if y < 3:
                        fr.set(x, y, shirt, 3)
            if lapels:
                fr.set(2, 0, shade(jacket, 0.75))
                fr.set(5, 0, shade(jacket, 0.75))
                fr.set(2, 1, shade(jacket, 0.75))
                fr.set(5, 1, shade(jacket, 0.75))
        if tie:
            for y in range(0, 7):
                fr.set(3, y, tie, 3)
                fr.set(4, y, tie if y > 0 else shade(tie, 0.8), 3)
        if buttons:
            for y in (4, 6, 8, 10):
                fr.set(4 if tie else 3, y, buttons, 2)
        if belt:
            for n in SIDES:
                f[n].row(8, belt)
            fr.set(3, 8, GOLD, 2)
            fr.set(4, 8, GOLD, 2)
        if extra:
            extra(f)
    return paint


def arm_painter(sleeve, cuff=None, hand=SKIN, hand_rows=3, extra=None):
    def paint(f):
        for n in SIDES + ('up',):
            f[n].fill(sleeve, 5)
        for n in SIDES:
            f[n].fill(hand, 4, 12 - hand_rows, 12)
            if cuff:
                f[n].row(12 - hand_rows - 1, cuff)
        f['down'].fill(hand, 3)
        if extra:
            extra(f)
    return paint


def leg_painter(trousers, shoes=BLACK, shoe_rows=2, stripe=None, boots=None):
    def paint(f):
        for n in SIDES + ('up',):
            f[n].fill(trousers, 5)
        top = 12 - (boots if boots else shoe_rows)
        for n in SIDES:
            f[n].fill(shoes, 3, top, 12)
            if boots:
                f[n].row(top, shade(shoes, 1.35), 2)
        f['down'].fill(shade(shoes, 0.7), 2)
        if stripe:
            for n in ('east', 'west'):
                f[n].col(2, stripe, 3, 0, top)
    return paint


def solid(c, noise=5, top=None, bottom=None, front=None):
    def paint(f):
        for n in f:
            f[n].fill(c, noise)
        if top:
            f['up'].fill(top, noise)
        if bottom:
            f['down'].fill(bottom, noise)
        if front:
            front(f['north'])
    return paint


def base_body(m, head, torso, r_arm, l_arm, r_leg, l_leg):
    biped_bones(m)
    m.cube('head', 'head', (-4, 24, -4), (4, 32, 4), head)
    m.cube('body', 'body', (-4, 12, -2), (4, 24, 2), torso)
    m.cube('right_arm', 'right_arm', (4, 12, -2), (8, 24, 2), r_arm)
    m.cube('left_arm', 'left_arm', (-8, 12, -2), (-4, 24, 2), l_arm)
    m.cube('right_leg', 'right_leg', (-0.1, 0, -2), (3.9, 12, 2), r_leg)
    m.cube('left_leg', 'left_leg', (-3.9, 0, -2), (0.1, 12, 2), l_leg)


def peaked_cap(m, crown, band, visor=BLACK, badge=GOLD, height=3, wide=1):
    """Фуражка: тулья шире головы, околыш, козырёк и кокарда."""
    m.cube('hat', 'cap_band', (-4.5, 30, -4.5), (4.5, 32, 4.5),
           solid(band, front=lambda fr: (fr.set(4, 0, badge, 2), fr.set(4, 1, badge, 2))))
    m.cube('hat', 'cap_crown', (-4 - wide, 32, -4 - wide - 1), (4 + wide, 32 + height, 4 + wide),
           solid(crown, top=shade(crown, 1.1), front=lambda fr: [fr.set(x, fr.h - 1, shade(crown, 0.8)) for x in range(fr.w)]))
    m.cube('hat', 'cap_visor', (-4, 30, -7), (4, 31, -4), solid(visor, 3))


def mustache_cube(m, color, wide=6, z=-4.6):
    m.cube('head', 'mustache', (-wide / 2, 25, z - 1), (wide / 2, 26, z), solid(color, 4))


# ---------------------------------------------------------------- НПС

def dictator():
    m = Model('dictator', 1917)
    tunic = hexc('#4B5A2E')
    tunic_d = shade(tunic, 0.8)
    trousers = hexc('#23283A')
    hair = hexc('#1E1A18')

    def medals(fr):
        for x, c in ((1, RED), (2, GOLD), (5, hexc('#2F6FBF')), (6, RED)):
            fr.set(x, 3, c, 2)
            fr.set(x, 4, GOLD if c != GOLD else RED, 2)
        for x in range(1, 7):
            fr.set(x, 2, shade(GOLD, 0.9), 2) if x in (1, 2, 5, 6) else None

    base_body(
        m,
        head_painter(hair, eyes=(70, 40, 20), brows=BLACK, mustache=None),
        torso_painter(tunic, shirt=tunic_d, belt=hexc('#3A2414'), buttons=GOLD, lapels=False,
                      open_collar=False, extra=lambda f: medals(f['north'])),
        arm_painter(tunic, cuff=RED, hand=WHITE),
        arm_painter(tunic, cuff=RED, hand=WHITE),
        leg_painter(trousers, stripe=RED, boots=5),
        leg_painter(trousers, stripe=RED, boots=5),
    )
    # Пышные усы и брови.
    m.cube('head', 'mustache', (-3.5, 25, -5), (3.5, 26, -4), solid(hexc('#141010'), 3))
    # Маршальская фуражка: высокая тулья, красный околыш, золотой герб.
    m.cube('hat', 'cap_band', (-4.5, 30, -4.5), (4.5, 32, 4.5),
           solid(RED, front=lambda fr: [fr.set(x, y, GOLD, 2) for x in (4, 5) for y in (0, 1)]))
    m.cube('hat', 'cap_crown', (-5, 32, -6), (5, 35, 5),
           solid(tunic, top=shade(tunic, 1.1),
                 front=lambda fr: ([fr.set(x, 2, GOLD, 2) for x in range(fr.w)], fr.set(5, 0, GOLD), fr.set(4, 0, GOLD))))
    m.cube('hat', 'cap_visor', (-4, 30, -7), (4, 31, -4), solid(BLACK, 3, top=hexc('#2A2A30')))
    m.cube('hat', 'cap_cord', (-4, 31, -5.5), (4, 32, -4.5), solid(GOLD, 3))
    # Эполеты с бахромой.
    for side, grp, x0 in ((1, 'right_arm', 4), (-1, 'left_arm', -8)):
        m.cube(grp, f'epaulette_{grp}', (x0 - 0.5, 23.5, -2.5), (x0 + 4.5, 24.5, 2.5),
               solid(GOLD, top=hexc('#F2CF63')))
        m.cube(grp, f'fringe_{grp}', (x0 - 0.5, 22.5, -2.5), (x0 + 4.5, 23.5, 2.5),
               solid(GOLD_D, 6))
    # Звезда героя на груди и пряжка ремня.
    m.cube('body', 'star', (-3, 19, -2.6), (-1, 21, -1.6), solid(GOLD, 2, front=lambda fr: fr.set(0, 0, RED)))
    m.cube('body', 'buckle', (-1, 15, -2.6), (1, 16, -1.6), solid(GOLD, 2))
    # Парадная шинель-накидка: красная подкладка видна сзади.
    m.group('cape', 'body', (0, 24, 2), (6, 0, 0))
    m.cube('cape', 'cape', (-5, 4, 2), (5, 24, 3),
           solid(hexc('#2E3519'), 4, top=GOLD,
                 front=lambda fr: fr.fill(hexc('#8A1620'), 4)))
    m.cube('cape', 'cape_collar', (-5, 23, 1), (5, 25, 3), solid(hexc('#8C6A20'), 4))
    return m


def tax_inspector():
    m = Model('tax_inspector', 13)
    suit = hexc('#24304A')
    hair = hexc('#5A4632')
    base_body(
        m,
        head_painter(hair, eyes=(60, 60, 60), mustache=None, hair_rows=1, side_hair=False),
        torso_painter(suit, tie=hexc('#7A1414'), buttons=hexc('#9AA0B0')),
        arm_painter(suit, cuff=WHITE),
        arm_painter(suit, cuff=WHITE),
        leg_painter(suit, shoes=hexc('#1A1410')),
        leg_painter(suit, shoes=hexc('#1A1410')),
    )
    # Круглые очки, котелок, нарукавная повязка «НАЛОГ».
    m.cube('head', 'glasses', (-4, 27, -5), (4, 28, -4), solid((30, 30, 34), 1,
           front=lambda fr: [fr.set(x, 0, (190, 220, 235), 1) for x in (1, 2, 5, 6)]))
    m.cube('hat', 'bowler_brim', (-5, 31, -5), (5, 32, 5), solid(BLACK, 3))
    m.cube('hat', 'bowler_top', (-4, 32, -4), (4, 35, 4), solid((32, 30, 36), 3,
           front=lambda fr: fr.row(fr.h - 1, hexc('#4A3020'))))
    m.cube('right_arm', 'armband', (4, 19, -2), (8, 21, 2), solid(hexc('#C8A030'), 3), inflate=0.15)
    # Портфель с бумагами висит на ремне сбоку.
    m.cube('body', 'case_strap', (-4, 12, -2), (-3, 24, 2), solid(hexc('#3A2414'), 2), inflate=0.05)
    m.cube('body', 'case', (-6, 10, -1.5), (-4, 14, 3.5), solid(hexc('#5A3A1E'), 5, top=hexc('#7A5A2E')))
    return m


def customs():
    m = Model('customs', 21)
    green = hexc('#2E5A3A')
    hair = hexc('#3A2A1E')
    base_body(
        m,
        head_painter(hair, eyes=(50, 80, 60), mustache=hexc('#3A2A1E'), hair_rows=1),
        torso_painter(green, shirt=hexc('#C8D0B0'), tie=hexc('#1E3A26'), belt=hexc('#2A1A10'), buttons=GOLD),
        arm_painter(green),
        arm_painter(green),
        leg_painter(shade(green, 0.85), shoes=BLACK, boots=3),
        leg_painter(shade(green, 0.85), shoes=BLACK, boots=3),
    )
    m.cube('hat', 'cap_band', (-4.5, 30, -4.5), (4.5, 32, 4.5),
           solid(hexc('#1E3A26'), front=lambda fr: [fr.set(x, y, GOLD, 2) for x in (4,) for y in (0, 1)]))
    m.cube('hat', 'cap_crown', (-5, 32, -6), (5, 34, 5), solid(green, top=shade(green, 1.15)))
    m.cube('hat', 'cap_visor', (-4, 30, -7), (4, 31, -4), solid(BLACK, 3))
    for grp, x0 in (('right_arm', 4), ('left_arm', -8)):
        m.cube(grp, f'shoulder_{grp}', (x0, 23.5, -2), (x0 + 4, 24.5, 2),
               solid(hexc('#1E3A26'), 3, top=hexc('#1E3A26'), front=None), inflate=0.1)
    # Жезл-шлагбаум (полосатая палка) за поясом.
    m.cube('body', 'baton', (3.5, 9, -1), (4.5, 17, 0), solid(WHITE, 2,
           front=lambda fr: [fr.row(y, RED) for y in range(0, fr.h, 2)]))
    return m


def courier():
    m = Model('courier', 31)
    orange = hexc('#E07A1F')
    hair = hexc('#8A5A2A')
    base_body(
        m,
        head_painter(hair, eyes=(40, 90, 140), hair_rows=1),
        torso_painter(orange, shirt=WHITE, open_collar=True, lapels=False, extra=_bag_strap),
        arm_painter(orange, hand_rows=4),
        arm_painter(orange, hand_rows=4),
        leg_painter(hexc('#3A4A6A'), shoes=hexc('#5A3A20')),
        leg_painter(hexc('#3A4A6A'), shoes=hexc('#5A3A20')),
    )
    # Кепка козырьком вперёд, сумка «ГОСПОЧТА» через плечо.
    m.cube('hat', 'cap', (-4.5, 30, -4.5), (4.5, 33, 4.5), solid(orange, top=shade(orange, 1.1),
           front=lambda fr: fr.set(4, 1, WHITE)))
    m.cube('hat', 'cap_visor', (-4, 30, -7.5), (4, 31, -4.5), solid(shade(orange, 0.7), 3))
    m.group('bag', 'body', (-5, 14, 0))
    m.cube('bag', 'bag', (-7, 10, -2.5), (-4, 15, 2.5), solid(hexc('#7A5028'), 5, top=hexc('#8A6030'),
           front=lambda fr: fr.set(1, 2, GOLD)))
    return m


def _bag_strap(f):
    strap = hexc('#5A3A20')
    for y in range(12):
        x = round(y * 7 / 11)
        f['north'].set(x, y, strap, 2)
        f['south'].set(7 - x, y, strap, 2)
    f['up'].set(0, 1, strap, 2)
    f['up'].set(0, 2, strap, 2)


def croupier():
    m = Model('croupier', 41)
    vest = hexc('#1F7A3A')
    hair = hexc('#141010')
    base_body(
        m,
        head_painter(hair, eyes=(30, 30, 30), mustache=hexc('#141010'), hair_rows=1, side_hair=False),
        torso_painter(WHITE, shirt=WHITE, open_collar=False, lapels=False,
                      extra=lambda f: _vest(f, vest)),
        arm_painter(WHITE, cuff=None, extra=lambda f: [f[n].row(5, RED) for n in SIDES]),
        arm_painter(WHITE, cuff=None, extra=lambda f: [f[n].row(5, RED) for n in SIDES]),
        leg_painter(BLACK, shoes=(14, 12, 14)),
        leg_painter(BLACK, shoes=(14, 12, 14)),
    )
    # Зелёный козырёк крупье и галстук-бабочка.
    m.cube('hat', 'visor_band', (-4, 29, -4), (4, 30, 4), solid(vest, 2), inflate=0.0)
    m.cube('hat', 'visor', (-4, 29, -8), (4, 30, -4), solid(hexc('#3FAF5A'), 3))
    m.cube('body', 'bowtie', (-2, 22, -2.8), (2, 23, -1.8), solid(BLACK, 2))
    return m


def _vest(f, vest):
    fr = f['north']
    for n in ('east', 'west', 'south'):
        f[n].fill(vest, 4, 1, 11)
    for y in range(1, 11):
        for x in range(fr.w):
            if abs(x - 3.5) > (3.0 - y * 0.3) or y > 6:
                fr.set(x, y, vest, 4)
    for y in (6, 8):
        fr.set(3, y, GOLD, 1)


def dice_croupier():
    m = Model('dice_croupier', 51)
    vest = hexc('#1E5A32')
    hair = hexc('#6A3A1E')
    base_body(
        m,
        head_painter(hair, eyes=(60, 40, 20), mustache=hexc('#4A2810'), hair_rows=1),
        torso_painter(hexc('#E8E0C8'), open_collar=False, lapels=False, extra=lambda f: _vest(f, vest)),
        arm_painter(hexc('#E8E0C8'), extra=lambda f: [f[n].row(4, GOLD) for n in SIDES]),
        arm_painter(hexc('#E8E0C8'), extra=lambda f: [f[n].row(4, GOLD) for n in SIDES]),
        leg_painter(hexc('#2A2420'), shoes=(18, 14, 12)),
        leg_painter(hexc('#2A2420'), shoes=(18, 14, 12)),
    )
    # Цилиндр с игральной костью на ленте, закрученные усы.
    m.cube('hat', 'top_brim', (-5, 31, -5), (5, 32, 5), solid(BLACK, 3))
    m.cube('hat', 'top_hat', (-3.5, 32, -3.5), (3.5, 37, 3.5), solid((30, 26, 30), 3,
           front=lambda fr: (fr.row(fr.h - 2, hexc('#1E5A32')), fr.set(3, fr.h - 2, WHITE))))
    m.cube('head', 'mustache_l', (-4.5, 25, -5), (-1.5, 26, -4), solid(hexc('#4A2810'), 2))
    m.cube('head', 'mustache_r', (1.5, 25, -5), (4.5, 26, -4), solid(hexc('#4A2810'), 2))
    m.cube('body', 'bowtie', (-2, 22, -2.8), (2, 23, -1.8), solid(hexc('#7A1020'), 2))
    return m


def slots_operator():
    m = Model('slots_operator', 61)
    red = hexc('#7A1020')
    hair = hexc('#C89040')
    base_body(
        m,
        head_painter(hair, eyes=(40, 100, 60), hair_rows=2),
        torso_painter(red, open_collar=False, lapels=False,
                      extra=lambda f: [f['north'].set(x, y, GOLD, 1) for y in (2, 5, 8) for x in (2, 5)]),
        arm_painter(red, cuff=GOLD),
        arm_painter(red, cuff=GOLD),
        leg_painter(hexc('#1A1A22'), stripe=GOLD),
        leg_painter(hexc('#1A1A22'), stripe=GOLD),
    )
    # Шапочка-«таблетка» посыльного с золотой лентой и ремешком.
    m.cube('hat', 'pillbox', (-2.5, 32, -3.5), (2.5, 35, 1.5), solid(red, 3, top=shade(red, 1.15),
           front=lambda fr: fr.row(fr.h - 1, GOLD)))
    m.cube('hat', 'chinstrap', (-4, 24, -4), (4, 25, 4), solid(BLACK, 2), inflate=0.1)
    for grp, x0 in (('right_arm', 4), ('left_arm', -8)):
        m.cube(grp, f'shoulder_{grp}', (x0, 23.5, -2), (x0 + 4, 24.5, 2), solid(GOLD, 3), inflate=0.1)
    # Ящик с жетонами на ремне.
    m.cube('body', 'token_box', (-3, 10, -4.5), (3, 13, -1.5), solid(hexc('#3A2414'), 4,
           top=hexc('#C8A030'), front=lambda fr: [fr.set(x, 1, GOLD) for x in (1, 3, 5)]))
    return m


def employer():
    m = Model('employer', 71)
    grey = hexc('#5E6670')
    hair = hexc('#8A8A88')
    base_body(
        m,
        head_painter(hair, eyes=(70, 70, 80), hair_rows=1, side_hair=False, bald=False),
        torso_painter(grey, tie=hexc('#30383F'), buttons=hexc('#20262C')),
        arm_painter(grey),
        arm_painter(grey),
        leg_painter(hexc('#3A3F46'), shoes=hexc('#2A1E14')),
        leg_painter(hexc('#3A3F46'), shoes=hexc('#2A1E14')),
    )
    # Чёрные нарукавники канцеляриста, очки на носу, карандаш за ухом, лысина с венчиком.
    for grp, x0 in (('right_arm', 4), ('left_arm', -8)):
        m.cube(grp, f'sleeve_{grp}', (x0, 14, -2), (x0 + 4, 19, 2), solid((26, 26, 30), 3), inflate=0.3)
    m.cube('head', 'glasses', (-3, 26, -5), (3, 27, -4), solid((60, 50, 40), 1,
           front=lambda fr: [fr.set(x, 0, (200, 225, 235), 1) for x in (1, 4)]))
    m.cube('head', 'pencil', (4, 27, -1), (5, 28, 4), solid(hexc('#E8C030'), 2,
           front=lambda fr: fr.set(0, 0, (60, 50, 40))))
    m.cube('hat', 'bald_top', (-3, 31.5, -3), (3, 32.5, 3), solid(SKIN, 3))
    return m


def licensor():
    m = Model('licensor', 81)
    cardigan = hexc('#8A6A3A')
    hair = hexc('#B04A1E')
    base_body(
        m,
        head_painter(hair, eyes=(30, 90, 60), hair_rows=2, lips=hexc('#B02A30')),
        torso_painter(cardigan, shirt=hexc('#E8D8C0'), buttons=hexc('#4A3420'),
                      extra=lambda f: [f['north'].set(x, 1, hexc('#C83040'), 2) for x in (3, 4)]),
        arm_painter(cardigan),
        arm_painter(cardigan),
        leg_painter(hexc('#4A3A30'), shoes=hexc('#3A1E14'), shoe_rows=2),
        leg_painter(hexc('#4A3A30'), shoes=hexc('#3A1E14'), shoe_rows=2),
    )
    # Причёска-«улей», большие очки в роговой оправе, юбка поверх ног (на туловище).
    m.cube('hat', 'beehive', (-3.5, 32, -2.5), (3.5, 38, 4.5), solid(hair, 6, top=shade(hair, 1.1)))
    m.cube('hat', 'bun_back', (-2, 26, 4), (2, 30, 6), solid(shade(hair, 0.9), 5))
    m.cube('head', 'glasses', (-4, 27, -5), (4, 29, -4), solid(hexc('#3A2414'), 1,
           front=lambda fr: [fr.set(x, y, (205, 230, 240), 1) for x in (1, 2, 5, 6) for y in (0, 1)]))
    m.cube('body', 'skirt', (-4.5, 6, -2.5), (4.5, 12, 2.5), solid(hexc('#5A4632'), 5,
           front=lambda fr: [fr.col(x, shade(hexc('#5A4632'), 0.85)) for x in (2, 6)]))
    m.cube('body', 'brooch', (-1, 22, -2.6), (1, 23, -1.6), solid(GOLD, 2, front=lambda fr: fr.set(0, 0, RED)))
    return m


def recipient():
    m = Model('recipient', 91)
    blue = hexc('#2F5E9E')
    hair = hexc('#2A2018')
    base_body(
        m,
        head_painter(hair, eyes=(60, 50, 40), beard=None, hair_rows=1),
        torso_painter(blue, shirt=hexc('#D8D8D0'), buttons=WHITE,
                      extra=lambda f: f['north'].fill(shade(blue, 0.9), 3, 9, 12)),
        arm_painter(blue, cuff=shade(blue, 0.7)),
        arm_painter(blue, cuff=shade(blue, 0.7)),
        leg_painter(hexc('#3A3A3A'), shoes=hexc('#2A1E14'), boots=4),
        leg_painter(hexc('#3A3A3A'), shoes=hexc('#2A1E14'), boots=4),
    )
    # Длинный складской халат, кепка-ушанка с поднятыми ушами, нагрудный карман с карандашом.
    m.cube('body', 'coat_skirt', (-4.5, 6, -2.5), (4.5, 12, 2.5), solid(blue, 5,
           front=lambda fr: fr.col(4, shade(blue, 0.75))))
    m.cube('hat', 'ushanka', (-4.5, 30, -4.5), (4.5, 34, 4.5), solid(hexc('#6A5440'), 7, top=hexc('#7A6450'),
           front=lambda fr: fr.fill(hexc('#8A7460'), 7, 0, 2)))
    m.cube('hat', 'ear_r', (4.5, 31, -2), (5.5, 34, 2), solid(hexc('#6A5440'), 7))
    m.cube('hat', 'ear_l', (-5.5, 31, -2), (-4.5, 34, 2), solid(hexc('#6A5440'), 7))
    m.cube('body', 'pocket', (1, 19, -2.5), (3, 21, -1.5), solid(shade(blue, 0.8), 2,
           front=lambda fr: fr.set(0, 0, hexc('#E8C030'))))
    return m


def _stripes(f, a, b, period=2, skip=()):
    """Полосатая роба: горизонтальные полосы по высоте грани."""
    for n, face in f.items():
        if n in skip:
            continue
        for y in range(face.h):
            face.row(y, a if (y // period) % 2 == 0 else b, 3)


def prisoner():
    m = Model('prisoner', 404)
    white = hexc('#E6E2D6')
    black = hexc('#26242A')
    stubble = hexc('#4A3E36')

    def torso(f):
        _stripes(f, white, black)
        fr = f['north']
        # Нашивка с номером: белая табличка с «чёрными цифрами».
        fr.fill(WHITE, 2, 2, 5, 4, 8)
        for x in (4, 6):
            fr.set(x, 3, BLACK, 1)
        fr.set(5, 3, BLACK, 1)
        fr.set(4, 4, BLACK, 1)
        fr.set(6, 4, BLACK, 1)

    def arm(f):
        _stripes(f, white, black, skip=('down',))
        for n in SIDES:
            f[n].fill(SKIN, 4, 9, 12)
        f['down'].fill(SKIN, 3)

    def leg(f):
        _stripes(f, white, black, skip=('down',))
        for n in SIDES:
            f[n].fill(hexc('#3A2A1E'), 3, 10, 12)
        f['down'].fill(hexc('#2A1E14'), 2)

    base_body(
        m,
        head_painter(stubble, eyes=(70, 60, 50), hair_rows=1, side_hair=False, brows=hexc('#3A2E26')),
        torso, arm, arm, leg, leg,
    )
    # Полосатая шапочка-«таблетка», хомут-упряжь через грудь, кандалы на ногах.
    m.cube('hat', 'cap', (-4, 31, -4), (4, 34, 4), lambda f: _stripes(f, white, black, period=1))
    m.cube('body', 'harness', (-4, 13, -2), (4, 15, 2), solid(hexc('#6A4426'), 4,
           front=lambda fr: (fr.set(3, 0, hexc('#9A9AA2')), fr.set(4, 0, hexc('#9A9AA2')))), inflate=0.3)
    for grp, x0 in (('right_leg', -0.1), ('left_leg', -3.9)):
        m.cube(grp, f'shackle_{grp}', (x0, 2, -2), (x0 + 4, 3, 2), solid(hexc('#7A7A84'), 6), inflate=0.35)
    m.cube('right_leg', 'chain', (-2, 2.2, -0.5), (2, 3.2, 0.5), solid(hexc('#5A5A62'), 6))
    return m


ALL = [dictator, tax_inspector, customs, courier, croupier, dice_croupier, slots_operator, employer, licensor,
       recipient, prisoner]

if __name__ == '__main__':
    for fn in ALL:
        mdl = fn()
        mdl.save()
        print(mdl.id, len(mdl.cubes), 'кубов, UV до y =', mdl.shelf[1] + mdl.shelf[2])
