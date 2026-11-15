"""Перевод рукописных Java-моделей (Yarn, ось Y вниз) в Blockbench для GeckoLib: мимик и сбежавшая руда.

Опора верхней кости (−x, 24 − y, z), дочерней — родитель + (−x, −y, z); куб from = (опора.x − cx − w,
опора.y − cy − h, опора.z + cz); поворот (−pitch, −yaw, roll) в градусах. Текстуры — прежние.

Запуск: python blockbench/gen_java_models.py → blockbench/out/mimic.js, runaway_ore.js (+ .png).
"""
import base64
import json
import math
import os
import shutil

from gen_npcs import BUILD_JS, OUT

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(os.path.dirname(HERE), 'src/main/resources/assets/diktatura/textures/entity')

# имя, родитель, опора (Java), поворот (Java, радианы), кубы [(u, v, x, y, z, w, h, d)]
MIMIC = [
    ('base', None, (0, 21, 0), (0, 0, 0), [(0, 0, -7, -10, -7, 14, 10, 14)]),
    ('lower_teeth', 'base', (0, -10, 0), (0, 0, 0), [(34, 79, -6, -2, -7, 12, 2, 1), (0, 43, 6, -2, -6, 1, 2, 11),
                                                     (24, 43, -7, -2, -6, 1, 2, 11)]),
    ('tongue', 'base', (0, -10.5, 3), (0, 0, 0), [(0, 69, -2.5, -0.5, -9, 5, 1, 9)]),
    ('lid', None, (0, 11, 7), (0, 0, 0), [(0, 24, -7, -5, -14, 14, 5, 14)]),
    ('lock', 'lid', (0, -2, -14), (0, 0, 0), [(28, 69, -1, -1, -1, 2, 4, 1)]),
    ('upper_teeth', 'lid', (0, 0, 0), (0, 0, 0), [(0, 84, -6, 0, -14, 12, 2, 1), (0, 56, 6, 0, -13, 1, 2, 11),
                                                  (24, 56, -7, 0, -13, 1, 2, 11)]),
    ('eyes', 'lid', (0, 0, 0), (0, 0, 0), [(8, 79, -5, -1, -12, 10, 1, 3)]),
    ('leg_fl', None, (-5, 21, -5), (0, 0, 0), [(34, 69, -1, 0, -1, 2, 3, 2)]),
    ('leg_fr', None, (5, 21, -5), (0, 0, 0), [(42, 69, -1, 0, -1, 2, 3, 2)]),
    ('leg_bl', None, (-5, 21, 5), (0, 0, 0), [(50, 69, -1, 0, -1, 2, 3, 2)]),
    ('leg_br', None, (5, 21, 5), (0, 0, 0), [(0, 79, -1, 0, -1, 2, 3, 2)]),
]
# У руды тело — сам блок руды (рисует GeckoLib-слой на кости body, низ блока — 6 пикселей над землёй);
# руки и лицо — дети body, чтобы подпрыгивать вместе с блоком.
ORE = [
    ('body', None, (0, 18, 0), (0, 0, 0), []),
    ('left_leg', None, (3, 18, 0), (0, 0, 0), [(0, 0, -1, 0, -1, 2, 5, 2), (16, 0, -1.5, 4, -3, 3, 2, 4)]),
    ('right_leg', None, (-3, 18, 0), (0, 0, 0), [(8, 0, -1, 0, -1, 2, 5, 2), (30, 0, -1.5, 4, -3, 3, 2, 4)]),
    ('left_arm', 'body', (6, -6, 0), (0, 0, 0), [(0, 7, 0, -1, -1, 5, 2, 2), (44, 0, 5, -1.5, -1.5, 2, 3, 3)]),
    ('right_arm', 'body', (-6, -6, 0), (0, 0, 0), [(14, 7, -5, -1, -1, 5, 2, 2), (54, 0, -7, -1.5, -1.5, 2, 3, 3)]),
    ('face', 'body', (0, -7, -6), (0, 0, 0), [(28, 7, 1, -2.5, -1, 3, 3, 1), (36, 7, -4, -2.5, -1, 3, 3, 1),
                                              (44, 7, -2, 2, -1, 4, 3, 1)]),
]


def convert(parts):
    pivots, groups, cubes = {}, [], []
    for name, parent, (px, py, pz), (rx, ry, rz), cs in parts:
        if parent is None:
            bp = (-px, 24 - py, pz)
        else:
            q = pivots[parent]
            bp = (q[0] - px, q[1] - py, q[2] + pz)
        pivots[name] = bp
        groups.append([name, parent, list(bp), [round(-math.degrees(rx), 3), round(-math.degrees(ry), 3), round(math.degrees(rz), 3)]])
        for i, (u, v, cx, cy, cz, w, h, d) in enumerate(cs):
            f = (bp[0] - cx - w, bp[1] - cy - h, bp[2] + cz)
            cubes.append({'group': name, 'name': f'{name}_{i}', 'from': list(f), 'to': [f[0] + w, f[1] + h, f[2] + d],
                          'uv': [u, v], 'inflate': 0})
    return groups, cubes


def save(id_, parts, tex_w, tex_h, tex):
    groups, cubes = convert(parts)
    shutil.copy(tex, os.path.join(OUT, id_ + '.png'))
    with open(tex, 'rb') as f:
        png = base64.b64encode(f.read()).decode()
    data = {'id': id_, 'height': tex_h, 'groups': groups, 'cubes': cubes, 'texture': 'data:image/png;base64,' + png}
    js = BUILD_JS.replace('Project.texture_width = 128;', f'Project.texture_width = {tex_w};')
    with open(os.path.join(OUT, id_ + '.js'), 'w', encoding='utf-8') as f:
        f.write(js.replace('__DATA__', json.dumps(data)))
    print(id_, len(cubes), 'кубов')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    save('mimic', MIMIC, 64, 128, os.path.join(TEX, 'mimic.png'))
    save('runaway_ore', ORE, 64, 32, os.path.join(TEX, 'runaway_ore.png'))
