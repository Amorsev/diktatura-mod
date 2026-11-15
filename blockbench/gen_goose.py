"""Гусь для GeckoLib — та же модель и текстура 64x64, что были в GooseModel.java, в координатах Blockbench.

Перевод из Java-модели (Yarn, ось Y вниз): опора верхней кости (−x, 24 − y, z), дочерней — родитель + (−x, −y, z);
куб from = (опора.x − cx − w, опора.y − cy − h, опора.z + cz); поворот (−pitch, −yaw, roll) в градусах.

Запуск: python blockbench/gen_goose.py → blockbench/out/goose.js и goose.png (текстура дикого гуся;
у служебного — своя текстура textures/entity/goose/corrupted_goose.png на той же развёртке).
"""
import base64
import json
import os
import shutil

from gen_npcs import BUILD_JS, OUT

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(os.path.dirname(HERE), 'src/main/resources/assets/diktatura/textures/entity/goose/wild_goose.png')

GROUPS = [
    # имя, родитель, опора, поворот
    ('body', None, (0, 7, 0), (0, 0, 0)),
    ('tail', 'body', (0, 9, 5.5), (28.65, 0, 0)),
    ('neck', None, (0, 9, -4), (0, 0, 0)),
    ('head', 'neck', (0, 18, -4), (0, 0, 0)),
    ('beak', 'head', (0, 20, -7.5), (0, 0, 0)),
    ('jaw', 'head', (0, 19, -7.5), (0, 0, 0)),
    ('left_wing', None, (-3.5, 9, -2), (0, 0, 0)),
    ('right_wing', None, (3.5, 9, -2), (0, 0, 0)),
    ('left_leg', None, (-1.5, 4, 1), (0, 0, 0)),
    ('right_leg', None, (1.5, 4, 1), (0, 0, 0)),
]
CUBES = [
    ('body', 'body_c', (-3.5, 4, -5), (3.5, 10, 6), (0, 0)),
    ('tail', 'tail_c', (-2.5, 7.5, 4.5), (2.5, 10.5, 8.5), (0, 31)),
    ('neck', 'neck_c', (-1.5, 8, -5.5), (1.5, 18, -2.5), (20, 17)),
    ('head', 'head_c', (-2, 18, -7.5), (2, 22, -1.5), (32, 17)),
    ('beak', 'beak_c', (-1.5, 20, -11.5), (1.5, 21, -7.5), (18, 31)),
    ('jaw', 'jaw_c', (-1.5, 18, -10.5), (1.5, 19, -7.5), (40, 31)),
    ('left_wing', 'left_wing_c', (-4.5, 4.5, -2), (-3.5, 9.5, 7), (36, 0)),
    ('right_wing', 'right_wing_c', (3.5, 4.5, -2), (4.5, 9.5, 7), (0, 17)),
    ('left_leg', 'left_shin', (-2, 0, 0.5), (-1, 4, 1.5), (32, 31)),
    ('left_leg', 'left_foot', (-3, 0, -1.5), (0, 1, 1.5), (52, 31)),
    ('right_leg', 'right_shin', (1, 0, 0.5), (2, 4, 1.5), (36, 31)),
    ('right_leg', 'right_foot', (0, 0, -1.5), (3, 1, 1.5), (0, 38)),
]

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    shutil.copy(TEX, os.path.join(OUT, 'goose.png'))
    with open(TEX, 'rb') as f:
        png = base64.b64encode(f.read()).decode()
    data = {'id': 'goose', 'height': 64,
            'groups': [[n, p, list(o), list(r)] for n, p, o, r in GROUPS],
            'cubes': [{'group': g, 'name': n, 'from': list(a), 'to': list(b), 'uv': list(uv), 'inflate': 0}
                      for g, n, a, b, uv in CUBES],
            'texture': 'data:image/png;base64,' + png}
    js = BUILD_JS.replace('Project.texture_width = 128;', 'Project.texture_width = 64;')
    with open(os.path.join(OUT, 'goose.js'), 'w', encoding='utf-8') as f:
        f.write(js.replace('__DATA__', json.dumps(data)))
    print('goose', len(CUBES), 'кубов')
