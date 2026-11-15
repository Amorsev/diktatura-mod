"""Анимации GeckoLib для транспорта: повозка и три кареты такси.

Пишет blockbench/out/<id>.anims.json — описание ключевых кадров. Скрипт gl_build.js в Blockbench
собирает по нему анимации в проекте GeckoLib (их видно и можно поправить в Blockbench) и выгружает
geo.json, animation.json и .bbmodel.

Знаки поворотов (как в Blockbench): по X отрицательный угол наклоняет верх кости вперёд (к -Z),
поэтому колесо катится вперёд при уменьшении угла, а стопа ноги уходит вперёд при положительном.

Длина цикла рыси подобрана так, чтобы за цикл каждое колесо делало целое число оборотов, а пройденный
путь совпадал с пройденным в игре на «номинальной» скорости класса. В игре скорость анимации умножается
на (реальная скорость / номинальная) — колёса катятся без проскальзывания при любой скорости.

Запуск: python blockbench/gl_anims.py
"""
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out')

# Для Java (TaxiRenderer / WagonRenderer): путь за цикл рыси в блоках и длина цикла в секундах.
CYCLES = {}


def wheel_turns(radius_px, turns, length):
    """Колесо: turns полных оборотов вперёд за length секунд."""
    return {'rotation': [[0, 0, 0, 0], [length, -360 * turns, 0, 0]]}


def swing(length, amp, phase=0.0):
    """Качание по X: amp → −amp → amp за цикл; phase — сдвиг на полцикла (0 или 0.5)."""
    a = amp if phase == 0 else -amp
    return {'rotation': [[0, a, 0, 0, 'catmullrom'], [length / 2, -a, 0, 0, 'catmullrom'], [length, a, 0, 0, 'catmullrom']]}


def horse_trot(bones, prefix, length, i):
    bones[f'{prefix}_leg_fr'] = swing(length, 26)
    bones[f'{prefix}_leg_bl'] = swing(length, 26)
    bones[f'{prefix}_leg_fl'] = swing(length, 26, 0.5)
    bones[f'{prefix}_leg_br'] = swing(length, 26, 0.5)
    # Кивок дважды за цикл (на каждый толчок пары ног), у соседних лошадей чуть по-разному.
    n = -5 - i % 2
    bones[f'{prefix}_neck'] = {'rotation': [[0, 0, 0, 0, 'catmullrom'], [length / 4, n, 0, 0, 'catmullrom'],
                                            [length / 2, 0, 0, 0, 'catmullrom'], [3 * length / 4, n, 0, 0, 'catmullrom'],
                                            [length, 0, 0, 0, 'catmullrom']]}
    s = 10 if i % 2 == 0 else -10
    bones[f'{prefix}_tail'] = {'rotation': [[0, -12, s, 0, 'catmullrom'], [length / 2, -16, -s, 0, 'catmullrom'],
                                            [length, -12, s, 0, 'catmullrom']]}


def horse_idle(bones, prefix, i):
    t = 4.0
    d = 0.3 * i
    bones[f'{prefix}_neck'] = {'rotation': [[0, 0, 0, 0, 'catmullrom'], [1.2 + d, 3, -2, 0, 'catmullrom'],
                                            [2.6 + d, -2, 2, 0, 'catmullrom'], [t, 0, 0, 0, 'catmullrom']]}
    bones[f'{prefix}_tail'] = {'rotation': [[0, 0, 8, 0, 'catmullrom'], [1.0 + d, 0, -8, 0, 'catmullrom'],
                                            [2.0 + d, 0, 6, 0, 'catmullrom'], [3.0, 0, -6, 0, 'catmullrom'],
                                            [t, 0, 8, 0, 'catmullrom']]}


def taxi(id_, horses, wheels, length):
    """wheels: [(кость, радиус в пикселях, оборотов за цикл)]."""
    trot = {}
    for i, h in enumerate(horses):
        horse_trot(trot, h, length, i)
    for bone, r, turns in wheels:
        trot[bone] = wheel_turns(r, turns, length)
    if id_ == 'taxi_economy':
        # Телегу трясёт на ухабах: подскок дважды за цикл и покачивание с боку на бок.
        q = length / 4
        trot['body'] = {
            'position': [[0, 0, 0, 0, 'catmullrom'], [q, 0, 0.5, 0, 'catmullrom'], [2 * q, 0, 0, 0, 'catmullrom'],
                         [3 * q, 0, 0.4, 0, 'catmullrom'], [length, 0, 0, 0, 'catmullrom']],
            'rotation': [[0, 0, 0, 1.5, 'catmullrom'], [length / 2, 0, 0, -1.5, 'catmullrom'], [length, 0, 0, 1.5, 'catmullrom']],
        }
    if id_ == 'taxi_business':
        # Кучер погоняет: рука с кнутом вверх-вниз раз за цикл.
        trot['coachman_whip_arm'] = {'rotation': [[0, 0, 0, 0, 'catmullrom'], [length * 0.3, -18, 0, 4, 'catmullrom'],
                                                  [length * 0.55, 6, 0, 0, 'catmullrom'], [length, 0, 0, 0, 'catmullrom']]}
    idle = {}
    for i, h in enumerate(horses):
        horse_idle(idle, h, i)
    bone, r, turns = wheels[0]
    CYCLES[id_] = (round(turns * 2 * math.pi * r / 16, 4), length)
    return [
        {'name': 'animation.taxi.trot', 'length': length, 'loop': True, 'bones': trot},
        {'name': 'animation.taxi.idle', 'length': 4.0, 'loop': True, 'bones': idle},
    ]


def wagon():
    length = 0.49
    roll = {'wheel_left': wheel_turns(6, 5, length), 'wheel_right': wheel_turns(6, 5, length)}
    CYCLES['wagon'] = (round(5 * 2 * math.pi * 6 / 16, 4), length)
    return [{'name': 'animation.wagon.roll', 'length': length, 'loop': True, 'bones': roll}]


SPECS = {
    # Эконом: заднее колесо r=6, 3 оборота за 0.65 с ≈ 10.9 блока/с (номинал 0.55 блока/тик).
    'taxi_economy': taxi('taxi_economy', ['horse'],
                         [('wheel_rear_left', 6, 3), ('wheel_rear_right', 6, 3)], 0.65),
    # Комфорт: переднее r=5 (6 оборотов), заднее r=6 (5 оборотов) — одинаковый путь ≈ 11.8 блока за 0.65 с.
    'taxi_comfort': taxi('taxi_comfort', ['horse_r', 'horse_l'],
                         [('wheel_rear_left', 6, 5), ('wheel_rear_right', 6, 5),
                          ('wheel_front_left', 5, 6), ('wheel_front_right', 5, 6)], 0.65),
    # Бизнес: переднее r=5 (7 оборотов), заднее r=7 (5 оборотов) ≈ 13.7 блока за 0.49 с (28 блоков/с).
    'taxi_business': taxi('taxi_business', ['horse_ar', 'horse_al', 'horse_br', 'horse_bl'],
                          [('wheel_rear_left', 7, 5), ('wheel_rear_right', 7, 5),
                           ('wheel_front_left', 5, 7), ('wheel_front_right', 5, 7)], 0.49),
    'wagon': wagon(),
}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for id_, anims in SPECS.items():
        with open(os.path.join(OUT, id_ + '.anims.json'), 'w', encoding='utf-8') as f:
            json.dump({'id': id_, 'animations': anims}, f, ensure_ascii=False)
        dist, length = CYCLES[id_]
        print(f'{id_}: {len(anims)} анимаций, цикл {length} с = {dist} блока')
