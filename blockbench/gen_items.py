"""Модели оружия вождя для Blockbench (формат «Модель для Minecraft JE», предметы).

Для каждого предмета: текстура 32x32 (палитра «образцов» + узорные участки) и JS-сценарий,
который строит проект Blockbench с кубами, UV граней и положениями в руке/в инвентаре.
Из Blockbench экспортируется JSON-модель предмета и .bbmodel.

Запуск: python blockbench/gen_items.py → blockbench/out/item_<id>.png, item_<id>.js
"""
import base64
import io
import json
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), 'out')


def hexc(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


class Atlas:
    """Текстура 32x32; UV граней задаётся в пикселях текстуры."""

    def __init__(self, seed):
        self.img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)
        self.next = [0, 0]
        self.sw = {}

    def swatch(self, name, color, noise=10, w=4, h=4, painter=None):
        x, y = self.next
        if x + w > 32:
            x, y = 0, y + 4
        self.next = [x + w, y]
        for j in range(h):
            for i in range(w):
                n = self.rnd.randint(-noise, noise)
                c = color if painter is None else painter(i, j)
                self.px[x + i, y + j] = tuple(max(0, min(255, v + n)) for v in c[:3]) + (255,)
        self.sw[name] = [x, y, x + w, y + h]  # в пикселях: Blockbench сам пересчитает в 0..16
        return self.sw[name]


class Item:
    def __init__(self, item_id, seed):
        self.id = item_id
        self.atlas = Atlas(seed)
        self.elements = []
        self.display = {}

    def box(self, name, frm, to, uv, faces_uv=None, rotation=None):
        """uv — образец для всех граней; faces_uv — {грань: образец} для отдельных граней."""
        faces = {}
        for f in ('north', 'east', 'south', 'west', 'up', 'down'):
            faces[f] = (faces_uv or {}).get(f, uv)
        self.elements.append({'name': name, 'from': list(frm), 'to': list(to), 'faces': faces,
                              'rotation': rotation})

    def save(self):
        os.makedirs(OUT, exist_ok=True)
        self.atlas.img.save(os.path.join(OUT, f'item_{self.id}.png'))
        buf = io.BytesIO()
        self.atlas.img.save(buf, 'PNG')
        data = {'id': self.id, 'elements': self.elements, 'display': self.display,
                'texture': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}
        with open(os.path.join(OUT, f'item_{self.id}.js'), 'w', encoding='utf-8') as f:
            f.write(BUILD_JS.replace('__DATA__', json.dumps(data)))


BUILD_JS = r"""(() => {
const D = __DATA__;
Undo.initEdit({outliner: true, elements: [], textures: []});
[...Outliner.elements].forEach(e => e.remove());
[...Group.all].forEach(g => g.remove());
[...Texture.all].forEach(t => t.remove());
Project.name = D.id;
Project.box_uv = false;
Project.texture_width = 32;
Project.texture_height = 32;
const tex = new Texture({name: D.id, namespace: 'diktatura', folder: 'item'}).fromDataURL(D.texture).add(false);
tex.namespace = 'diktatura';
tex.folder = 'item';
tex.name = D.id + '_model';
const added = [];
for (const e of D.elements) {
  const opts = {name: e.name, from: e.from, to: e.to, box_uv: false, faces: {}};
  for (const f in e.faces) opts.faces[f] = {uv: e.faces[f], texture: tex.uuid};
  if (e.rotation) { opts.rotation = e.rotation.angles; opts.origin = e.rotation.origin; }
  const cube = new Cube(opts);
  cube.addTo();
  cube.init();
  for (const f in e.faces) { cube.faces[f].uv = e.faces[f]; cube.faces[f].texture = tex.uuid; }
  added.push(cube);
}
for (const slot in D.display) {
  const s = D.display[slot];
  Project.display_settings[slot] = new DisplaySlot(slot, {rotation: s.rotation, translation: s.translation, scale: s.scale});
}
Undo.finishEdit('Build item ' + D.id, {outliner: true, elements: added, textures: [tex]});
Canvas.updateAll();
return {id: D.id, cubes: Cube.all.length};
})()"""

GOLD = hexc('#E3B23C')
GOLD_L = hexc('#F6D46A')
GOLD_D = hexc('#A87C1E')
RED = hexc('#C0202A')
RED_D = hexc('#801018')
WHITE = hexc('#ECECE4')
BLACK = hexc('#1E1C22')


def held(tp_rot, tp_tr, tp_scale, fp_rot, fp_tr, fp_scale, gui_rot, gui_tr, gui_scale, ground_scale=0.5):
    return {
        'thirdperson_righthand': {'rotation': tp_rot, 'translation': tp_tr, 'scale': [tp_scale] * 3},
        'thirdperson_lefthand': {'rotation': tp_rot, 'translation': tp_tr, 'scale': [tp_scale] * 3},
        'firstperson_righthand': {'rotation': fp_rot, 'translation': fp_tr, 'scale': [fp_scale] * 3},
        'firstperson_lefthand': {'rotation': fp_rot, 'translation': fp_tr, 'scale': [fp_scale] * 3},
        'gui': {'rotation': gui_rot, 'translation': gui_tr, 'scale': [gui_scale] * 3},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [ground_scale] * 3},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [gui_scale] * 3},
    }


def scepter():
    it = Item('scepter', 7)
    a = it.atlas
    gold = a.swatch('gold', GOLD, 12)
    gold_l = a.swatch('gold_l', GOLD_L, 8)
    gold_d = a.swatch('gold_d', GOLD_D, 8)
    red = a.swatch('red', RED, 10)
    grip = a.swatch('grip', RED, 6, painter=lambda i, j: RED_D if (i + j) % 2 else RED)
    ruby = a.swatch('ruby', hexc('#E0303A'), 18)
    it.box('pommel', (7, 0, 7), (9, 1, 9), gold_d)
    it.box('rod', (7.5, 1, 7.5), (8.5, 12, 8.5), gold)
    it.box('grip', (7.25, 2, 7.25), (8.75, 6, 8.75), grip)
    it.box('collar', (7, 11, 7), (9, 12, 9), gold_d)
    it.box('orb', (6.5, 12, 6.5), (9.5, 15, 9.5), gold_l, {'down': gold_d})
    it.box('orb_band', (6.4, 13.2, 6.4), (9.6, 13.8, 9.6), ruby)
    it.box('star_v', (7.5, 15, 7.6), (8.5, 19.5, 8.4), red)
    it.box('star_h', (5.75, 16.75, 7.6), (10.25, 17.75, 8.4), red)
    it.box('star_d', (7, 16.2, 7.7), (9, 18.2, 8.3), ruby,
           rotation={'angles': [0, 0, 45], 'origin': [8, 17.2, 8]})
    it.display = held([90, 0, 0], [0, 2, 0], 0.85, [80, -10, 0], [1, 2, 0], 0.7,
                      [0, 0, -40], [1, -1.5, 0], 0.6)
    return it


def megaphone():
    it = Item('megaphone', 11)
    a = it.atlas
    red = a.swatch('red', RED, 10)
    white = a.swatch('white', WHITE, 6)
    black = a.swatch('black', BLACK, 6)
    stripe = a.swatch('stripe', RED, 6, painter=lambda i, j: WHITE if j in (1, 2) else RED)
    inside = a.swatch('inside', hexc('#2A2A30'), 8, painter=lambda i, j: (20, 20, 24) if 0 < i < 3 and 0 < j < 3 else (70, 70, 76))
    gold = a.swatch('gold', GOLD, 10)
    it.box('handle', (7, 0, 9), (9, 5, 11), black)
    it.box('trigger', (7.5, 3.5, 8), (8.5, 4.5, 9), gold)
    it.box('tube', (6.5, 5, 6), (9.5, 8, 13), white, {'north': inside})
    it.box('band', (6.4, 4.9, 9), (9.6, 8.1, 10), red)
    it.box('mouthpiece', (7, 5.5, 13), (9, 7.5, 15), black)
    it.box('horn1', (6, 4.5, 4), (10, 8.5, 6), stripe, {'north': inside, 'south': red})
    it.box('horn2', (5, 3.5, 2), (11, 9.5, 4), white, {'north': inside, 'south': white})
    it.box('horn3', (4, 2.5, 0), (12, 10.5, 2), red, {'north': inside})
    it.box('star', (7, 10.5, 0.5), (9, 11.5, 1.5), gold)
    it.display = held([90, 0, 0], [0, 2, 0], 0.8, [80, 10, 0], [0, 2, 0], 0.65,
                      [25, 135, 0], [0, 0, 0], 0.68)
    return it


def slipper():
    it = Item('slipper', 13)
    a = it.atlas
    sole = a.swatch('sole', hexc('#6A4A2A'), 8)
    sole_d = a.swatch('sole_d', hexc('#4A3018'), 6)
    check = a.swatch('check', RED, 6, w=8, h=8,
                     painter=lambda i, j: hexc('#B02028') if ((i // 2) + (j // 2)) % 2 else hexc('#E8D8C8'))
    inner = a.swatch('inner', hexc('#D8C8B0'), 8)
    fluff = a.swatch('fluff', WHITE, 14)
    it.box('sole', (4, 0, 1), (12, 1, 15), sole, {'down': sole_d})
    it.box('heel', (4, 0, 12), (12, 1.5, 15), sole_d)
    it.box('insole', (4.5, 1, 7), (11.5, 1.2, 14.5), inner)
    it.box('upper', (4, 1, 1), (12, 3.5, 7), check)
    it.box('toe', (4.5, 1, 0.5), (11.5, 3, 1), check)
    it.box('pompon', (6.5, 3.5, 2), (9.5, 5, 5), fluff)
    it.display = held([0, 0, 0], [0, 3, 0], 0.85, [0, -10, 10], [1, 3, 0], 0.7,
                      [40, -30, 0], [0, 0.5, 0], 0.85)
    return it


def law_book():
    it = Item('law_book', 17)
    a = it.atlas
    cover = a.swatch('cover', hexc('#9A1A22'), 8)
    spine = a.swatch('spine', hexc('#6A0E14'), 6, painter=lambda i, j: GOLD if j in (0, 3) else hexc('#6A0E14'))
    pages = a.swatch('pages', hexc('#EDE3C8'), 4, painter=lambda i, j: hexc('#D8CCA8') if j % 2 else hexc('#EFE6CC'))
    gold = a.swatch('gold', GOLD, 10)
    star = a.swatch('star', hexc('#9A1A22'), 4, w=8, h=8,
                    painter=lambda i, j: GOLD if (abs(i - 3.5) + abs(j - 3.5) < 3 and (i in (3, 4) or j in (3, 4) or abs(i - 3.5) == abs(j - 3.5))) else hexc('#9A1A22'))
    it.box('front', (2, 0, 6), (14, 16, 7), cover, {'north': star})
    it.box('back', (2, 0, 10), (14, 16, 11), cover)
    it.box('pages', (3, 0.5, 7), (13.5, 15.5, 10), pages)
    it.box('spine', (1.5, 0, 6), (3, 16, 11), spine)
    for n, (x, y) in enumerate(((13, 15), (13, 0))):
        it.box(f'corner{n}', (x, y, 5.8), (x + 1.2, y + 1, 7.2), gold)
    it.box('clasp', (13.5, 6.5, 6.5), (14.5, 9.5, 10.5), gold)
    it.display = held([0, 90, 0], [0, 1, 0], 0.55, [0, 90, -10], [0, 2, 0], 0.5,
                      [0, 0, 0], [0, 0, 0], 0.85)
    return it


ALL = [scepter, megaphone, slipper, law_book]

if __name__ == '__main__':
    for fn in ALL:
        item = fn()
        item.save()
        print(item.id, len(item.elements))
