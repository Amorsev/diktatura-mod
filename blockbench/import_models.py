"""Переносит экспорт из Blockbench в мод.

Берёт из папки экспорта (по умолчанию blockbench/export):
  <id>.java          — экспорт «Модовая сущность → 1.17+ Yarn» для НПС;
  <id>.bbmodel       — исходники моделей (кладутся в blockbench/models);
  item_<id>.json     — экспорт «Модель для Minecraft JE» для оружия вождя;
и раскладывает: Java-код моделей НПС → NpcModelData.java, текстуры НПС → textures/entity/npc_model,
модели предметов → models/item, текстуры предметов → textures/item/<id>_model.png.

Запуск: python blockbench/import_models.py [папка_экспорта]
        python blockbench/import_models.py --taxi [папка_экспорта] — только кареты такси (gen_taxi.py)
"""
import json
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, 'src/main/resources/assets/diktatura')
JAVA_OUT = os.path.join(ROOT, 'src/client/java/com/diktatura/client/npc/NpcModelData.java')

NPCS = ['dictator', 'tax_inspector', 'customs', 'courier', 'croupier', 'dice_croupier', 'slots_operator',
        'employer', 'licensor', 'recipient', 'prisoner']
ITEMS = ['scepter', 'megaphone', 'slipper', 'law_book']
WAGON_OUT = os.path.join(ROOT, 'src/client/java/com/diktatura/client/transport/WagonModelData.java')
TAXIS = ['taxi_economy', 'taxi_comfort', 'taxi_business']
TAXI_OUT = os.path.join(ROOT, 'src/client/java/com/diktatura/client/transport/TaxiModelData.java')


def method_body(java):
    m = re.search(r'public static TexturedModelData getTexturedModelData\(\) \{\n(.*?)\n\t\}', java, re.S)
    if not m:
        raise ValueError('не найден getTexturedModelData()')
    return m.group(1)


def main(src):
    os.makedirs(os.path.join(HERE, 'models'), exist_ok=True)
    os.makedirs(os.path.join(RES, 'textures/entity/npc_model'), exist_ok=True)
    methods = []
    for npc in NPCS:
        with open(os.path.join(src, npc + '.java'), encoding='utf-8') as f:
            body = method_body(f.read())
        methods.append(f'\tstatic TexturedModelData {camel(npc)}() {{\n{body}\n\t}}\n')
        shutil.copy(os.path.join(src, npc + '.bbmodel'), os.path.join(HERE, 'models', npc + '.bbmodel'))
        shutil.copy(os.path.join(HERE, 'out', npc + '.png'), os.path.join(RES, 'textures/entity/npc_model', npc + '.png'))
    cases = ''.join(f'\t\t\tcase "{n}" -> {camel(n)}();\n' for n in NPCS)
    java = HEADER + f'\tpublic static final String[] IDS = {{{", ".join(chr(34) + n + chr(34) for n in NPCS)}}};\n\n' + \
        '\tpublic static TexturedModelData of(String id) {\n\t\treturn switch (id) {\n' + cases + \
        '\t\t\tdefault -> throw new IllegalArgumentException(id);\n\t\t};\n\t}\n\n' + '\n'.join(methods) + '}\n'
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, 'w', encoding='utf-8') as f:
        f.write(java)

    # Повозка госперевозки: своя модель (не гуманоид) и текстура 128x256.
    with open(os.path.join(src, 'wagon.java'), encoding='utf-8') as f:
        body = method_body(f.read())
    with open(WAGON_OUT, 'w', encoding='utf-8') as f:
        f.write(WAGON_HEADER + f'\tpublic static TexturedModelData wagon() {{\n{body}\n\t}}\n}}\n')
    shutil.copy(os.path.join(src, 'wagon.bbmodel'), os.path.join(HERE, 'models', 'wagon.bbmodel'))
    shutil.copy(os.path.join(HERE, 'out', 'wagon.png'), os.path.join(RES, 'textures/entity/wagon.png'))

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
    print('ok:', len(NPCS), 'НПС, повозка,', len(ITEMS), 'предмета')


def camel(s):
    a, *rest = s.split('_')
    return a + ''.join(x.title() for x in rest)


HEADER = '''package com.diktatura.client.npc;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/**
 * Модели НПС, сделанные в Blockbench (исходники — blockbench/models/*.bbmodel).
 * Файл создан скриптом blockbench/import_models.py из экспорта Blockbench «1.17+ Yarn» — не править руками.
 */
@SuppressWarnings("unused")
public final class NpcModelData {
	private NpcModelData() {
	}

'''

WAGON_HEADER = '''package com.diktatura.client.transport;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/**
 * Модель повозки госперевозки, сделанная в Blockbench (исходник — blockbench/models/wagon.bbmodel).
 * Файл создан скриптом blockbench/import_models.py из экспорта Blockbench «1.17+ Yarn» — не править руками.
 */
@SuppressWarnings("unused")
public final class WagonModelData {
	private WagonModelData() {
	}

'''


TAXI_HEADER = '''package com.diktatura.client.transport;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/**
 * Модели карет госТакси, сделанные в Blockbench (исходники — blockbench/models/taxi_*.bbmodel,
 * генератор — blockbench/gen_taxi.py). Файл создан скриптом blockbench/import_models.py --taxi
 * из экспорта Blockbench «1.17+ Yarn» — не править руками.
 */
@SuppressWarnings("unused")
public final class TaxiModelData {
	private TaxiModelData() {
	}

'''


def import_taxi(src):
    """Кареты такси: Java-модели → TaxiModelData.java, текстуры → textures/entity/taxi_*.png."""
    os.makedirs(os.path.join(HERE, 'models'), exist_ok=True)
    methods = []
    for t in TAXIS:
        with open(os.path.join(src, t + '.java'), encoding='utf-8') as f:
            body = method_body(f.read())
        name = camel(t.replace('taxi_', ''))
        methods.append(f'\tpublic static TexturedModelData {name}() {{\n{body}\n\t}}\n')
        shutil.copy(os.path.join(src, t + '.bbmodel'), os.path.join(HERE, 'models', t + '.bbmodel'))
        shutil.copy(os.path.join(HERE, 'out', t + '.png'), os.path.join(RES, 'textures/entity', t + '.png'))
    with open(TAXI_OUT, 'w', encoding='utf-8') as f:
        f.write(TAXI_HEADER + '\n'.join(methods) + '}\n')
    print('ok:', len(TAXIS), 'кареты такси')


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if a != '--taxi']
    folder = args[0] if args else os.path.join(HERE, 'export')
    if '--taxi' in sys.argv:
        import_taxi(folder)
    else:
        main(folder)
