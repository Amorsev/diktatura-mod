"""Переносит экспорт из Blockbench в мод.

Берёт из папки экспорта (по умолчанию blockbench/export):
  item_<id>.json     — экспорт «Модель для Minecraft JE» для оружия вождя (+ item_<id>.bbmodel);
и раскладывает: текстуры НПС (out/<id>.png) → textures/entity/npc_model, модели предметов → models/item,
текстуры предметов → textures/item/<id>_model.png. Модели НПС — GeckoLib (gl_build.js): export/<id>.geo.json →
geo/entity/npc, общие анимации (export/dictator.animation.json) → animations/entity/npc.animation.json.

Запуск: python blockbench/import_models.py [папка_экспорта]
        python blockbench/import_models.py --geo id [id …] — модели GeckoLib (gl_build.js в Blockbench):
            export/<id>.geo.json → assets/diktatura/geo/entity, export/<id>.animation.json → animations/entity,
            out/<id>.png → textures/entity, export/<id>.bbmodel → blockbench/models
"""
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, 'src/main/resources/assets/diktatura')

NPCS = ['dictator', 'tax_inspector', 'customs', 'courier', 'croupier', 'dice_croupier', 'slots_operator',
        'employer', 'licensor', 'recipient', 'poker_dealer']
ITEMS = ['scepter', 'megaphone', 'slipper', 'law_book']
# Модели GeckoLib (повозка, кареты такси): python import_models.py --geo <id> …
GEO = ['wagon', 'taxi_economy', 'taxi_comfort', 'taxi_business', 'goose', 'mimic', 'runaway_ore', 'player_wide', 'player_slim', 'poker_table']
# Модели GeckoLib со своими текстурами в другом месте (у гуся — textures/entity/goose/*.png, их не трогаем).
OWN_TEXTURES = {'goose', 'player_wide', 'player_slim'}
# Модель игрока (двойник) рисуется скином оригинала; анимации — общие с НПС (npc.animation.json).
NO_ANIMS = {'player_wide', 'player_slim'}


def main(src):
    os.makedirs(os.path.join(HERE, 'models'), exist_ok=True)
    os.makedirs(os.path.join(RES, 'textures/entity/npc_model'), exist_ok=True)
    for npc in NPCS:
        shutil.copy(os.path.join(HERE, 'out', npc + '.png'), os.path.join(RES, 'textures/entity/npc_model', npc + '.png'))
    # Повозка и кареты такси — модели GeckoLib: см. --geo.

    for item in ITEMS:
        with open(os.path.join(src, f'item_{item}.json'), encoding='utf-8') as f:
            model = json.load(f)
        for k in ('format_version', 'credit', 'texture_size'):
            model.pop(k, None)
        tex = f'diktatura:item/{item}_model'
        model['textures'] = {'0': tex, 'particle': tex}
        for e in model['elements']:
            e.pop('name', None)
        with open(os.path.join(RES, 'models/item', item + '.json'), 'w', encoding='utf-8') as f:
            json.dump(model, f, indent='\t', ensure_ascii=False)
            f.write('\n')
        shutil.copy(os.path.join(HERE, 'out', f'item_{item}.png'), os.path.join(RES, 'textures/item', f'{item}_model.png'))
        shutil.copy(os.path.join(src, f'item_{item}.bbmodel'), os.path.join(HERE, 'models', f'item_{item}.bbmodel'))
    print('ok:', len(ITEMS), 'предмета')


def import_geo(ids, src=os.path.join(HERE, 'export')):
    """Модели GeckoLib: геометрия, анимации, текстура и проект Blockbench."""
    for sub in ('geo/entity', 'animations/entity', 'textures/entity'):
        os.makedirs(os.path.join(RES, sub), exist_ok=True)
    os.makedirs(os.path.join(HERE, 'models'), exist_ok=True)
    for i in ids:
        shutil.copy(os.path.join(src, i + '.geo.json'), os.path.join(RES, 'geo/entity', i + '.geo.json'))
        if i not in NO_ANIMS:
            shutil.copy(os.path.join(src, i + '.animation.json'), os.path.join(RES, 'animations/entity', i + '.animation.json'))
        if i not in OWN_TEXTURES:
            shutil.copy(os.path.join(HERE, 'out', i + '.png'), os.path.join(RES, 'textures/entity', i + '.png'))
        shutil.copy(os.path.join(src, i + '.bbmodel'), os.path.join(HERE, 'models', i + '.bbmodel'))
    print('ok:', len(ids), 'моделей GeckoLib')


if __name__ == '__main__':
    if '--geo' in sys.argv:
        import_geo([a for a in sys.argv[1:] if a != '--geo'] or GEO)
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'export'))
