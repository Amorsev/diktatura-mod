#!/usr/bin/env python3
"""
Генератор структуры тюрьмы: src/main/resources/data/diktatura/structure/prison.nbt

Без внешних зависимостей (свой мини-писатель NBT). Запуск из корня репозитория:
    python tools/gen_prison.py

Разметка должна совпадать с com.diktatura.prison.PrisonLayout:
размер 21x9x21, камеры с центрами (2|6|10|14|18, 2|18) на y=1, трон диктатора x=18 z=10,
вход с запада (x=0, z=9..11). Структуру можно и перестроить в игре и сохранить
структурным блоком под именем diktatura:prison, сохранив размер и точки.
"""
import gzip
import os
import struct

DATA_VERSION = 3955  # Minecraft 1.21.1
SX, SY, SZ = 21, 9, 21
CELL_X = [2, 6, 10, 14, 18]
CELL_Z = [2, 18]
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                   'data', 'diktatura', 'structure', 'prison.nbt')

# ---------------------------------------------------------------- NBT
TAG_END, TAG_BYTE, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 8, 9, 10


class Int(int):
    pass


def _name(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _tag_type(v):
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, int):
        return TAG_INT
    raise TypeError(v)


def _payload(v):
    t = _tag_type(v)
    if t == TAG_INT:
        return struct.pack('>i', v)
    if t == TAG_STRING:
        return _name(v)
    if t == TAG_LIST:
        et = _tag_type(v[0]) if v else TAG_END
        return struct.pack('>bi', et, len(v)) + b''.join(_payload(x) for x in v)
    out = b''
    for k, x in v.items():
        out += struct.pack('>b', _tag_type(x)) + _name(k) + _payload(x)
    return out + b'\x00'


def write_nbt(path, root):
    data = struct.pack('>b', TAG_COMPOUND) + _name('') + _payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(data)


# ---------------------------------------------------------------- постройка
grid = {}  # (x,y,z) -> (name, props)


def put(x, y, z, name, **props):
    grid[(x, y, z)] = (name, props)


def fill(x1, y1, z1, x2, y2, z2, name, **props):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                put(x, y, z, name, **props)


AIR = 'minecraft:air'
WALL = 'minecraft:stone_bricks'
MOSSY = 'minecraft:mossy_stone_bricks'
CRACKED = 'minecraft:cracked_stone_bricks'
FLOOR = 'minecraft:polished_andesite'
BARS = 'minecraft:iron_bars'

# весь объём — воздух (чтобы постройка вычищала рельеф внутри)
fill(0, 0, 0, SX - 1, SY - 1, SZ - 1, AIR)
# пол
fill(0, 0, 0, SX - 1, 0, SZ - 1, FLOOR)
# внешние стены y=1..6 с узором
for x in range(SX):
    for z in range(SZ):
        if x in (0, SX - 1) or z in (0, SZ - 1):
            for y in range(1, 7):
                n = (x * 7 + y * 13 + z * 5) % 11
                put(x, y, z, MOSSY if n == 0 else CRACKED if n == 1 else WALL)
# потолок y=7 и зубцы y=8
fill(0, 7, 0, SX - 1, 7, SZ - 1, WALL)
for x in range(SX):
    for z in range(SZ):
        if (x in (0, SX - 1) or z in (0, SZ - 1)) and (x + z) % 2 == 0:
            put(x, 8, z, 'minecraft:stone_brick_wall')
# световые блоки в потолке зала
for x in (4, 8, 12, 16):
    for z in (7, 13):
        put(x, 7, z, 'minecraft:glowstone')

# камеры: внутренность 3x3, высота 3 (y=1..3), потолок y=4, решётки на стороне зала
for cz, bars_z, inner in ((2, 4, range(1, 4)), (18, 16, range(17, 20))):
    # перегородки между камерами
    z1, z2 = min(min(inner), bars_z), max(max(inner), bars_z)
    for wx in (4, 8, 12, 16):
        fill(wx, 1, z1, wx, 4, z2, WALL)
    # потолок камер
    fill(1, 4, z1, SX - 2, 4, z2, WALL)
    for cx in CELL_X:
        # решётки
        for x in range(cx - 1, cx + 2):
            for y in range(1, 4):
                put(x, y, bars_z, BARS)
        # нары и свет
        put(cx - 1, 1, cz, 'minecraft:white_carpet')
        put(cx + 1, 1, cz, 'minecraft:cauldron')
        put(cx, 3, (1 if cz == 2 else 19), 'minecraft:lantern', hanging='true', waterlogged='false')

# зал: красная дорожка от входа к трону
fill(1, 1, 10, 15, 1, 10, 'minecraft:red_carpet')
# трон
fill(17, 1, 8, 19, 1, 12, 'minecraft:polished_blackstone')
put(18, 1, 10, 'minecraft:gold_block')
put(19, 1, 10, 'minecraft:gold_block')
put(19, 2, 10, 'minecraft:gold_block')
put(19, 3, 10, 'minecraft:gold_block')
put(19, 2, 9, 'minecraft:gold_block')
put(19, 2, 11, 'minecraft:gold_block')
put(16, 1, 9, 'minecraft:polished_blackstone_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
put(16, 1, 10, 'minecraft:polished_blackstone_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
put(16, 1, 11, 'minecraft:polished_blackstone_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
# решётка-барьер вокруг трона (диктатор недосягаем)
for z in range(7, 14):
    put(15, 1, z, BARS)
    put(15, 2, z, BARS)
fill(15, 1, 10, 15, 2, 10, AIR)
put(15, 1, 10, 'minecraft:iron_door', facing='west', half='lower', hinge='left', open='false', powered='false')
put(15, 2, 10, 'minecraft:iron_door', facing='west', half='upper', hinge='left', open='false', powered='false')
# знамёна-«портреты» вождя на стене за троном
for z in (8, 12):
    put(19, 4, z, 'minecraft:red_wall_banner', facing='west')
# вход: решётчатые ворота с запада
for z in range(9, 12):
    for y in range(1, 4):
        put(0, y, z, BARS)
put(0, 4, 10, 'minecraft:chiseled_stone_bricks')

# ---------------------------------------------------------------- сериализация
palette = []
index = {}
blocks = []
for (x, y, z), (name, props) in sorted(grid.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        index[key] = len(palette)
        entry = {'Name': name}
        if props:
            entry['Properties'] = dict(sorted(props.items()))
        palette.append(entry)
    blocks.append({'pos': [Int(x), Int(y), Int(z)], 'state': Int(index[key])})

root = {
    'DataVersion': Int(DATA_VERSION),
    'size': [Int(SX), Int(SY), Int(SZ)],
    'palette': palette,
    'blocks': blocks,
    'entities': [],
}
write_nbt(OUT, root)
print(f'{OUT}: {len(blocks)} блоков, палитра {len(palette)}')
