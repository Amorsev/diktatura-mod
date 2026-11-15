"""Модель игрока для GeckoLib — двойник комендантского часа (рисуется скином своего оригинала).

Раскладка UV — как у скина игрока 64x64 (с вторым слоем: шляпа, куртка, рукава, штанины), поэтому
подходит любой скин. Две модели: player_wide (руки 4 px, «Стив») и player_slim (руки 3 px, «Алекс»).

Кости как у гуманоида Minecraft (head, body, right_arm, left_arm, right_leg, left_leg) — анимации общие
с НПС (animations/entity/npc.animation.json). Дочерние кости armor_* — опоры для брони (ItemArmorGeoLayer):
их кубы повторяют части тела и не рисуются, по ним слой подгоняет ванильную броню.

Координаты — как в Blockbench: Y вверх, лицо на -Z, +X — правая рука персонажа.
Запуск: python blockbench/gen_player.py → blockbench/out/player_wide.js, player_slim.js (+ .png-заглушка)
"""
import base64
import io
import json
import os

from PIL import Image

from gen_npcs import BUILD_JS, OUT


def placeholder():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    for x in range(64):
        for y in range(16):
            if x < 32:
                px[x, y] = (200, 150, 120, 255)
    for x in range(64):
        for y in range(16, 32):
            px[x, y] = (60, 90, 160, 255) if 16 <= x < 40 else (200, 150, 120, 255) if x >= 40 else (40, 40, 110, 255)
    for x in range(16, 48):
        for y in range(48, 64):
            px[x, y] = (40, 40, 110, 255) if x < 32 else (200, 150, 120, 255)
    return img


def model(slim):
    aw = 3 if slim else 4
    ay = 21.5 if slim else 22   # опора руки (у тонких рук на полпикселя ниже, как в игре)
    groups = [
        ['head', None, [0, 24, 0], [0, 0, 0]],
        ['body', None, [0, 24, 0], [0, 0, 0]],
        ['right_arm', None, [5, ay, 0], [0, 0, 0]],
        ['left_arm', None, [-5, ay, 0], [0, 0, 0]],
        ['right_leg', None, [1.9, 12, 0], [0, 0, 0]],
        ['left_leg', None, [-1.9, 12, 0], [0, 0, 0]],
    ]
    a0 = ay - 10   # низ руки
    parts = {
        'head': ([-4, 24, -4], [4, 32, 4], [0, 0], [32, 0], 0.5),
        'body': ([-4, 12, -2], [4, 24, 2], [16, 16], [16, 32], 0.25),
        'right_arm': ([4, a0, -2], [4 + aw, a0 + 12, 2], [40, 16], [40, 32], 0.25),
        'left_arm': ([-4 - aw, a0, -2], [-4, a0 + 12, 2], [32, 48], [48, 48], 0.25),
        'right_leg': ([-0.1, 0, -2], [3.9, 12, 2], [0, 16], [0, 32], 0.25),
        'left_leg': ([-3.9, 0, -2], [0.1, 12, 2], [16, 48], [0, 48], 0.25),
    }
    names = {'head': ('hat',), 'body': ('jacket',), 'right_arm': ('right_sleeve',), 'left_arm': ('left_sleeve',),
             'right_leg': ('right_pants',), 'left_leg': ('left_pants',)}
    cubes = []
    for g, (frm, to, uv, uv2, inf) in parts.items():
        cubes.append({'group': g, 'name': g, 'from': frm, 'to': to, 'uv': uv, 'inflate': 0.0})
        cubes.append({'group': g, 'name': names[g][0], 'from': frm, 'to': to, 'uv': uv2, 'inflate': inf})
    # Опоры брони: та же опора и тот же размер, что у части тела (для рук — ширина 4, как у брони).
    armor = [('armor_head', 'head'), ('armor_body', 'body'), ('armor_right_arm', 'right_arm'),
             ('armor_left_arm', 'left_arm'), ('armor_legs_body', 'body'), ('armor_right_leg', 'right_leg'),
             ('armor_left_leg', 'left_leg'), ('armor_right_boot', 'right_leg'), ('armor_left_boot', 'left_leg')]
    pivots = {g[0]: g[2] for g in groups}
    for name, parent in armor:
        groups.append([name, parent, pivots[parent], [0, 0, 0]])
        frm, to = list(parts[parent][0]), list(parts[parent][1])
        if parent == 'right_arm':
            to[0] = frm[0] + 4
        elif parent == 'left_arm':
            frm[0] = to[0] - 4
        cubes.append({'group': name, 'name': name, 'from': frm, 'to': to, 'uv': [0, 0], 'inflate': 0.0})
    return groups, cubes


def main():
    os.makedirs(OUT, exist_ok=True)
    img = placeholder()
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    for slim in (False, True):
        pid = 'player_slim' if slim else 'player_wide'
        img.save(os.path.join(OUT, pid + '.png'))
        groups, cubes = model(slim)
        data = {'id': pid, 'width': 64, 'height': 64, 'groups': groups, 'cubes': cubes,
                'texture': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}
        with open(os.path.join(OUT, pid + '.js'), 'w', encoding='utf-8') as f:
            f.write(BUILD_JS.replace('__DATA__', json.dumps(data)))
        print(pid, len(groups), 'костей,', len(cubes), 'кубов')


if __name__ == '__main__':
    main()
