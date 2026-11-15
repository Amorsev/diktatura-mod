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
    # Заключённый бежит в упряжке: 4 шага за цикл, наклон вперёд, руки держат оглобли и качаются.
    q = length / 4
    legs_r, legs_l, arms = [], [], []
    for i in range(5):
        t = round(q * i, 4)
        a = 40 if i % 2 == 0 else -40
        legs_r.append(k(t, a))
        legs_l.append(k(t, -a))
        arms.append(k(t, 6 if i % 2 == 0 else -6))
    roll['p_right_leg'] = {'rotation': legs_r}
    roll['p_left_leg'] = {'rotation': legs_l}
    roll['p_right_arm'] = {'rotation': arms}
    roll['p_left_arm'] = {'rotation': [[f[0], -f[1], 0, 0, f[4]] for f in arms]}
    roll['prisoner'] = {'rotation': [k(0, -8), k(length, -8)],
                        'position': [k(0, 0, 0, 0), k(q / 2, 0, 0.6, 0), k(q, 0, 0, 0), k(3 * q / 2, 0, 0.6, 0),
                                     k(2 * q, 0, 0, 0), k(5 * q / 2, 0, 0.6, 0), k(3 * q, 0, 0, 0), k(7 * q / 2, 0, 0.6, 0),
                                     k(length, 0, 0, 0)]}
    roll['p_head'] = {'rotation': [k(0, 0), k(length / 2, 3), k(length, 0)]}
    CYCLES['wagon'] = (round(5 * 2 * math.pi * 6 / 16, 4), length)
    return [{'name': 'animation.wagon.roll', 'length': length, 'loop': True, 'bones': roll}]


def k(t, x=0, y=0, z=0, interp='catmullrom'):
    return [t, x, y, z, interp]


def goose():
    """Гусь: ходьба вперевалку, покой (оглядывается, виляет хвостом), шипение, бег с добычей (машет крыльями), гогот."""
    L = 0.6
    walk = {
        'left_leg': {'rotation': [k(0, 35), k(L / 2, -35), k(L, 35)]},
        'right_leg': {'rotation': [k(0, -35), k(L / 2, 35), k(L, -35)]},
        'body': {'rotation': [k(0, 0, 0, 6), k(L / 2, 0, 0, -6), k(L, 0, 0, 6)]},
        'neck': {'rotation': [k(0, 0, 0, 3.5), k(L / 4, -5, 0, 0), k(L / 2, 0, 0, -3.5), k(3 * L / 4, -5, 0, 0), k(L, 0, 0, 3.5)]},
        'tail': {'rotation': [k(0, 0, 8, 0), k(L / 2, 0, -8, 0), k(L, 0, 8, 0)]},
        'left_wing': {'rotation': [k(0, 0, 0, -3), k(L / 2, 0, 0, 0), k(L, 0, 0, -3)]},
        'right_wing': {'rotation': [k(0, 0, 0, 0), k(L / 2, 0, 0, 3), k(L, 0, 0, 0)]},
    }
    idle = {
        'neck': {'rotation': [k(0), k(1.0, -3, 12), k(2.2, 2, -10), k(3.2, 0, 0), k(4.0)]},
        'tail': {'rotation': [k(0), k(3.2), k(3.35, 0, 16), k(3.5, 0, -16), k(3.65, 0, 10), k(3.8), k(4.0)]},
        'body': {'rotation': [k(0), k(2.0, -1.5), k(4.0)]},
    }
    H = 0.5
    hiss = {
        'neck': {'rotation': [k(0, -60), k(H / 2, -63), k(H, -60)]},
        'head': {'rotation': [k(0, 50), k(H / 2, 53), k(H, 50)]},
        'jaw': {'rotation': [k(0, -28), k(H / 2, -36), k(H, -28)]},
        'left_wing': {'rotation': [k(0, 0, 0, -25), k(H / 2, 0, 0, -34), k(H, 0, 0, -25)]},
        'right_wing': {'rotation': [k(0, 0, 0, 25), k(H / 2, 0, 0, 34), k(H, 0, 0, 25)]},
        'body': {'rotation': [k(0, -6), k(H, -6)]},
    }
    R = 0.36
    run = {
        'left_leg': {'rotation': [k(0, 45), k(R / 2, -45), k(R, 45)]},
        'right_leg': {'rotation': [k(0, -45), k(R / 2, 45), k(R, -45)]},
        'body': {'rotation': [k(0, -8, 0, 5), k(R / 2, -8, 0, -5), k(R, -8, 0, 5)]},
        'neck': {'rotation': [k(0, -22), k(R / 2, -26), k(R, -22)]},
        'left_wing': {'rotation': [k(0, 0, 0, -5), k(R / 4, 0, 0, -70), k(R / 2, 0, 0, -5), k(3 * R / 4, 0, 0, -70), k(R, 0, 0, -5)]},
        'right_wing': {'rotation': [k(0, 0, 0, 5), k(R / 4, 0, 0, 70), k(R / 2, 0, 0, 5), k(3 * R / 4, 0, 0, 70), k(R, 0, 0, 5)]},
    }
    honk = {
        'jaw': {'rotation': [k(0), k(0.08, -32), k(0.3, -32), k(0.4)]},
        'neck': {'rotation': [k(0), k(0.1, 10), k(0.3, 8), k(0.4)]},
    }
    CYCLES['goose'] = (0, L)
    return [
        {'name': 'animation.goose.walk', 'length': L, 'loop': True, 'bones': walk},
        {'name': 'animation.goose.idle', 'length': 4.0, 'loop': True, 'bones': idle},
        {'name': 'animation.goose.hiss', 'length': H, 'loop': True, 'bones': hiss},
        {'name': 'animation.goose.run', 'length': R, 'loop': True, 'bones': run},
        {'name': 'animation.goose.honk', 'length': 0.4, 'loop': False, 'bones': honk},
    ]


def mimic():
    """Мимик: спит обычным сундуком (ножки, зубы, язык и глаза спрятаны), проснулся — пасть приоткрыта и дышит,
    ходит на ножках, кусает (chomp — поверх любой позы)."""
    hidden = {'scale': [k(0, 0, 0, 0, 'linear'), k(1.0, 0, 0, 0, 'linear')]}
    sleep = {
        'base': {'position': [k(0, 0, -3, 0, 'linear'), k(1.0, 0, -3, 0, 'linear')]},
        'lid': {'position': [k(0, 0, -3, 0, 'linear'), k(1.0, 0, -3, 0, 'linear')]},
    }
    for b in ('leg_fl', 'leg_fr', 'leg_bl', 'leg_br', 'lower_teeth', 'upper_teeth', 'tongue', 'eyes'):
        sleep[b] = hidden
    lid_breath = {'rotation': [k(0, 19), k(1.25, 33), k(2.5, 19)]}
    tongue = {'rotation': [k(0, 11, 0, 0), k(0.6, 30, 12, 0), k(1.25, -8, -12, 0), k(1.9, 25, 8, 0), k(2.5, 11, 0, 0)]}
    awake = {'lid': lid_breath, 'tongue': tongue}
    W = 0.5
    walk = {
        'lid': {'rotation': [k(0, 24), k(W / 2, 30), k(W, 24)]},
        'tongue': {'rotation': [k(0, 20, 8, 0), k(W / 2, 5, -8, 0), k(W, 20, 8, 0)]},
        'leg_fl': {'rotation': [k(0, 50), k(W / 2, -50), k(W, 50)]},
        'leg_bl': {'rotation': [k(0, 50), k(W / 2, -50), k(W, 50)]},
        'leg_fr': {'rotation': [k(0, -50), k(W / 2, 50), k(W, -50)]},
        'leg_br': {'rotation': [k(0, -50), k(W / 2, 50), k(W, -50)]},
        'base': {'position': [k(0), k(W / 4, 0, 0.6, 0), k(W / 2), k(3 * W / 4, 0, 0.6, 0), k(W)]},
    }
    chomp = {'lid': {'rotation': [k(0, 26), k(0.12, 63), k(0.25, 3), k(0.37, 63), k(0.5, 26)]}}
    CYCLES['mimic'] = (0, W)
    return [
        {'name': 'animation.mimic.sleep', 'length': 1.0, 'loop': True, 'bones': sleep},
        {'name': 'animation.mimic.awake', 'length': 2.5, 'loop': True, 'bones': awake},
        {'name': 'animation.mimic.walk', 'length': W, 'loop': True, 'bones': walk},
        {'name': 'animation.mimic.chomp', 'length': 0.5, 'loop': False, 'bones': chomp},
    ]


def runaway_ore():
    """Сбежавшая руда: в панике машет ручками; бежит — ножки, подпрыгивает, лицо трясётся."""
    R = 0.36
    flail_l = [k(0, 0, 0, -52), k(R / 4, 0, 0, -86), k(R / 2, 0, 0, -52), k(3 * R / 4, 0, 0, -17), k(R, 0, 0, -52)]
    flail_r = [[f[0], 0, 0, -f[3], f[4]] for f in flail_l]
    run = {
        'left_leg': {'rotation': [k(0, 70), k(R / 2, -70), k(R, 70)]},
        'right_leg': {'rotation': [k(0, -70), k(R / 2, 70), k(R, -70)]},
        'body': {'position': [k(0), k(R / 4, 0, 1.8, 0), k(R / 2), k(3 * R / 4, 0, 1.8, 0), k(R)],
                 'rotation': [k(0, 0, 0, 3.5), k(R / 2, 0, 0, -3.5), k(R, 0, 0, 3.5)]},
        'left_arm': {'rotation': flail_l},
        'right_arm': {'rotation': flail_r},
        'face': {'position': [k(0, -0.3, 0, 0), k(R / 2, 0.3, 0, 0), k(R, -0.3, 0, 0)]},
    }
    I = 0.72
    idle = {
        'left_arm': {'rotation': [[f[0] * 2, f[1], f[2], f[3], f[4]] for f in flail_l]},
        'right_arm': {'rotation': [[f[0] * 2, f[1], f[2], f[3], f[4]] for f in flail_r]},
        'body': {'rotation': [k(0, 0, 0, 3.4), k(I / 2, 0, 0, -3.4), k(I, 0, 0, 3.4)]},
        'face': {'position': [k(0, -0.3, 0, 0), k(I / 2, 0.3, 0, 0), k(I, -0.3, 0, 0)]},
    }
    CYCLES['runaway_ore'] = (0, R)
    return [
        {'name': 'animation.runaway_ore.run', 'length': R, 'loop': True, 'bones': run},
        {'name': 'animation.runaway_ore.idle', 'length': I, 'loop': True, 'bones': idle},
    ]


NPCS = ['dictator', 'tax_inspector', 'customs', 'courier', 'croupier', 'dice_croupier', 'slots_operator',
        'employer', 'licensor', 'recipient', 'poker_dealer']


def npc():
    """Общие анимации NPC (кости гуманоида: head с hat, body, руки, ноги).

    move: idle (дыхание, руки чуть качаются), walk (для ходячих посетителей ведомств);
    action (по событию): talk — жест рукой при разговоре, wave — вождь машет, write — инспектор пишет,
    spin — крупье раскручивает рулетку, lever — оператор дёргает рычаг, shake — крупье трясёт стакан с костями.
    Знак по X: положительный угол — рука/нога вперёд; по Z: правая рука в сторону — плюс, левая — минус.
    Голову в покое и при ходьбе не анимируем: она поворачивается за взглядом (NpcRenderer); в жестах — анимация.
    """
    idle = {
        'body': {'rotation': [k(0), k(2.0, -1), k(4.0)], 'position': [k(0), k(2.0, 0, 0.15, 0), k(4.0)]},
        'right_arm': {'rotation': [k(0, 0, 0, 2), k(2.0, -2, 0, 4), k(4.0, 0, 0, 2)]},
        'left_arm': {'rotation': [k(0, 0, 0, -2), k(2.0, 2, 0, -4), k(4.0, 0, 0, -2)]},
        'cape': {'rotation': [k(0, 4), k(2.0, 7), k(4.0, 4)]},
    }
    W = 1.0
    walk = {
        'right_leg': {'rotation': [k(0, 30), k(W / 2, -30), k(W, 30)]},
        'left_leg': {'rotation': [k(0, -30), k(W / 2, 30), k(W, -30)]},
        'right_arm': {'rotation': [k(0, -25), k(W / 2, 25), k(W, -25)]},
        'left_arm': {'rotation': [k(0, 25), k(W / 2, -25), k(W, 25)]},
        'body': {'position': [k(0), k(W / 4, 0, 0.4, 0), k(W / 2), k(3 * W / 4, 0, 0.4, 0), k(W)]},
        'cape': {'rotation': [k(0, 10), k(W / 2, 16), k(W, 10)]},
    }
    talk = {
        'right_arm': {'rotation': [k(0), k(0.25, 45, 0, 8), k(0.55, 35, 12, -4), k(0.85, 50, -8, 10), k(1.2)]},
        'head': {'rotation': [k(0), k(0.3, 5), k(0.6, -3), k(0.9, 4), k(1.2)]},
    }
    wave = {
        'right_arm': {'rotation': [k(0), k(0.3, 0, 0, 150), k(0.5, 0, 0, 125), k(0.7, 0, 0, 150), k(0.9, 0, 0, 125),
                                   k(1.1, 0, 0, 150), k(1.6)]},
        'head': {'rotation': [k(0), k(0.3, -6), k(1.2, -6), k(1.6)]},
        'body': {'rotation': [k(0), k(0.3, 0, 0, -3), k(1.2, 0, 0, -3), k(1.6)]},
    }
    write = {
        'right_arm': {'rotation': [k(0), k(0.2, 40, -10, 0)] + [k(0.2 + 0.1 * i, 40 + (3 if i % 2 else -3), -10 + (6 if i % 2 else -6), 0)
                                                               for i in range(1, 11)] + [k(1.6)]},
        'left_arm': {'rotation': [k(0), k(0.2, 35, 20, 0), k(1.4, 35, 20, 0), k(1.6)]},
        'head': {'rotation': [k(0), k(0.2, -18), k(1.4, -18), k(1.6)]},
    }
    spin = {
        'right_arm': {'rotation': [k(0), k(0.25, 60, 0, 0), k(0.45, 60, -25, 0), k(0.7, 55, 30, 0, 'linear'), k(1.0, 45, 10, 0), k(1.5)]},
        'body': {'rotation': [k(0), k(0.45, 0, 10, 0), k(0.7, 0, -12, 0), k(1.5)]},
        'head': {'rotation': [k(0), k(0.3, -10), k(1.2, -10), k(1.5)]},
    }
    lever = {
        'right_arm': {'rotation': [k(0), k(0.3, 150, 0, 6), k(0.45, 150, 0, 6), k(0.65, 40, 0, 6, 'linear'), k(0.9, 40, 0, 6), k(1.2)]},
        'body': {'rotation': [k(0), k(0.45, -4), k(0.7, 6), k(1.2)]},
    }
    shake = {
        'right_arm': {'rotation': [k(0), k(0.2, 70, 0, -10)] + [k(0.2 + 0.1 * i, 62 if i % 2 else 82, 0, -10) for i in range(1, 10)]
                      + [k(1.2, 95, 0, -10), k(1.4, 40, 0, -6, 'linear'), k(1.7)]},
        'left_arm': {'rotation': [k(0), k(0.2, 70, 0, 10)] + [k(0.2 + 0.1 * i, 62 if i % 2 else 82, 0, 10) for i in range(1, 10)]
                     + [k(1.2, 95, 0, 10), k(1.4, 40, 0, 6, 'linear'), k(1.7)]},
        'head': {'rotation': [k(0), k(0.2, -12), k(1.4, -12), k(1.7)]},
    }
    # Удар (вождь-босс скипетром, двойник — оружием оригинала): замах вверх и рубящий взмах вниз.
    attack = {
        'right_arm': {'rotation': [k(0), k(0.12, 150, 0, 8), k(0.2, 150, 0, 8), k(0.32, 25, 0, -6, 'linear'), k(0.55)]},
        'body': {'rotation': [k(0), k(0.12, 4, 12, 0), k(0.32, -6, -10, 0), k(0.55)]},
        'left_arm': {'rotation': [k(0), k(0.12, -15, 0, -6), k(0.32, 20, 0, -4), k(0.55)]},
    }

    # Присед — как у игрока: корпус наклонён, руки вдоль корпуса, ноги отставлены назад, всё чуть ниже.
    def crouch(t, legs=0.0, arms=0.0):
        return {
            'body': [k(t, -28.6)], 'body@p': [k(t, 0, -3.2, 0)],
            'head@p': [k(t, 0, -4.2, 0)],
            'right_arm': [k(t, -23 + arms)], 'left_arm': [k(t, -23 - arms)],
            'right_arm@p': [k(t, 0, -3.2, 0)], 'left_arm@p': [k(t, 0, -3.2, 0)],
            'right_leg': [k(t, legs)], 'left_leg': [k(t, -legs)],
            'right_leg@p': [k(t, 0, -0.2, 4)], 'left_leg@p': [k(t, 0, -0.2, 4)],
        }

    def frames(*poses):
        bones = {}
        for pose in poses:
            for key, ks in pose.items():
                bone, ch = (key.split('@')[0], 'position') if key.endswith('@p') else (key, 'rotation')
                bones.setdefault(bone, {}).setdefault(ch, []).extend(ks)
        return bones

    # Крупье покера: тасует колоду, сдаёт карту, переворачивает общие карты, двигает банк победителю.
    shuffle = {
        'right_arm': {'rotation': [k(0), k(0.2, 55, 0, -18)] + [k(0.2 + 0.15 * i, 55 + (8 if i % 2 else -4), 0, -18)
                                                               for i in range(1, 9)] + [k(1.6)]},
        'left_arm': {'rotation': [k(0), k(0.2, 55, 0, 18)] + [k(0.2 + 0.15 * i, 55 + (-4 if i % 2 else 8), 0, 18)
                                                             for i in range(1, 9)] + [k(1.6)]},
        'head': {'rotation': [k(0), k(0.2, -15), k(1.4, -15), k(1.6)]},
    }
    deal = {
        'right_arm': {'rotation': [k(0), k(0.15, 50, 0, -10), k(0.3, 80, -25, -4, 'linear'), k(0.55)]},
        'left_arm': {'rotation': [k(0), k(0.1, 40, 0, 12), k(0.45, 40, 0, 12), k(0.55)]},
    }
    flip = {
        'right_arm': {'rotation': [k(0), k(0.2, 60, 0, -12), k(0.35, 60, 0, 10, 'linear'), k(0.6)]},
        'head': {'rotation': [k(0), k(0.2, -12), k(0.6)]},
    }
    push = {
        'right_arm': {'rotation': [k(0), k(0.25, 70, 20, -10), k(0.6, 70, -25, -10), k(1.0)]},
        'left_arm': {'rotation': [k(0), k(0.25, 70, 20, 10), k(0.6, 70, -25, 10), k(1.0)]},
        'body': {'rotation': [k(0), k(0.25, -8, 6, 0), k(0.6, -8, -10, 0), k(1.0)]},
    }

    S = 1.2
    sneak = frames(crouch(0), crouch(2.0))
    sneak_walk = frames(crouch(0, 22, 8), crouch(S / 2, -22, -8), crouch(S, 22, 8))
    CYCLES['npc'] = (0, W)
    anims = [
        {'name': 'animation.npc.idle', 'length': 4.0, 'loop': True, 'bones': idle},
        {'name': 'animation.npc.walk', 'length': W, 'loop': True, 'bones': walk},
        {'name': 'animation.npc.talk', 'length': 1.2, 'loop': False, 'bones': talk},
        {'name': 'animation.npc.wave', 'length': 1.6, 'loop': False, 'bones': wave},
        {'name': 'animation.npc.write', 'length': 1.6, 'loop': False, 'bones': write},
        {'name': 'animation.npc.spin', 'length': 1.5, 'loop': False, 'bones': spin},
        {'name': 'animation.npc.lever', 'length': 1.2, 'loop': False, 'bones': lever},
        {'name': 'animation.npc.shake', 'length': 1.7, 'loop': False, 'bones': shake},
        {'name': 'animation.npc.attack', 'length': 0.55, 'loop': False, 'bones': attack},
        {'name': 'animation.npc.sneak', 'length': 2.0, 'loop': True, 'bones': sneak},
        {'name': 'animation.npc.sneak_walk', 'length': S, 'loop': True, 'bones': sneak_walk},
        {'name': 'animation.npc.shuffle', 'length': 1.6, 'loop': False, 'bones': shuffle},
        {'name': 'animation.npc.deal', 'length': 0.55, 'loop': False, 'bones': deal},
        {'name': 'animation.npc.flip', 'length': 0.6, 'loop': False, 'bones': flip},
        {'name': 'animation.npc.push', 'length': 1.0, 'loop': False, 'bones': push},
    ]
    return anims


def poker_table():
    """Покерный стол: колода «тасуется» (подпрыгивает и покачивается) в начале раздачи."""
    CYCLES['poker_table'] = (0, 1.6)
    shuffle = {'deck': {'rotation': [k(0), k(0.2, 0, 20, 0), k(0.4, 0, -20, 0), k(0.6, 0, 15, 0), k(0.8, 0, -15, 0),
                                     k(1.0, 0, 10, 0), k(1.2, 0, -5, 0), k(1.6)],
                        'position': [k(0), k(0.3, 0, 1, 0), k(0.6), k(0.9, 0, 1, 0), k(1.2), k(1.6)]}}
    return [{'name': 'animation.poker_table.shuffle', 'length': 1.6, 'loop': False, 'bones': shuffle}]


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
    'goose': goose(),
    'mimic': mimic(),
    'runaway_ore': runaway_ore(),
    'poker_table': poker_table(),
}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for id_, anims in SPECS.items():
        with open(os.path.join(OUT, id_ + '.anims.json'), 'w', encoding='utf-8') as f:
            json.dump({'id': id_, 'animations': anims}, f, ensure_ascii=False)
    # NPC: одна и та же анимация для всех; шапка (hat) — дочерняя кость головы, поворачивается вместе с ней.
    # Кости, которых у NPC нет (cape есть только у вождя), gl_build.js пропускает.
    shared = npc()
    # Двойник комендантского часа — модель игрока (gen_player.py) с теми же анимациями.
    for id_ in NPCS + ['player_wide', 'player_slim']:
        with open(os.path.join(OUT, id_ + '.anims.json'), 'w', encoding='utf-8') as f:
            json.dump({'id': id_, 'animations': shared, 'reparent': [['hat', 'head']], 'optional': ['cape', 'hat']},
                      f, ensure_ascii=False)
    for id_, anims in SPECS.items():
        dist, length = CYCLES[id_]
        print(f'{id_}: {len(anims)} анимаций, цикл {length} с = {dist} блока')
    print('npc:', len(shared), 'анимаций для', len(NPCS), 'NPC')
