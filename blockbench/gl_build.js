// Сборка модели GeckoLib в Blockbench (desktop): выполнить в открытом пустом проекте формата
// «GeckoLib Animated Model», затем вызвать glBuild('<id>').
//
//   1. строит модель по blockbench/out/<id>.js (генераторы gen_taxi.py / gen_wagon.py / gen_npcs.py);
//   2. создаёт анимации по blockbench/out/<id>.anims.json (генератор gl_anims.py) — их видно во вкладке
//      «Анимация» и можно поправить руками;
//   3. выгружает в blockbench/export: <id>.geo.json, <id>.animation.json и <id>.bbmodel (проект GeckoLib).
//
// Дальше: python blockbench/import_models.py --geo <id> … — перенос в мод.
window.glBuild = function (id) {
	const fs = require('fs');
	const BASE = 'F:\\claudecode\\diktatura\\blockbench\\';
	if (Format.id !== 'geckolib_model') {
		throw new Error('Откройте пустой проект формата GeckoLib Animated Model');
	}
	eval(fs.readFileSync(BASE + 'out\\' + id + '.js', 'utf8'));
	Canvas.updateAll();
	const spec = JSON.parse(fs.readFileSync(BASE + 'out\\' + id + '.anims.json', 'utf8'));
	// Перенос костей: [дочерняя, родитель] (например, шапка NPC — в голову).
	for (const [child, parent] of (spec.reparent || [])) {
		const c = Group.all.find(x => x.name === child);
		const p = Group.all.find(x => x.name === parent);
		if (c && p) {
			c.addTo(p);
		}
	}
	const optional = spec.optional || [];
	for (const a of Animation.all.slice()) {
		a.remove(false);
	}
	for (const a of spec.animations) {
		const an = new Animation({name: a.name, loop: a.loop ? 'loop' : 'once', length: a.length, snapping: 100}).add(false);
		for (const [bone, channels] of Object.entries(a.bones)) {
			const g = Group.all.find(x => x.name === bone);
			if (!g) {
				if (optional.includes(bone)) {
					continue;
				}
				throw new Error(id + ': нет кости ' + bone);
			}
			const ba = an.getBoneAnimator(g);
			for (const [channel, frames] of Object.entries(channels)) {
				for (const k of frames) {
					ba.addKeyframe({
						channel: channel,
						time: k[0],
						interpolation: k[4] || 'linear',
						data_points: [{x: k[1], y: k[2], z: k[3]}],
					});
				}
			}
		}
	}
	Project.geckolib_modid = 'diktatura';
	const dir = BASE + 'export\\';
	if (!fs.existsSync(dir)) {
		fs.mkdirSync(dir);
	}
	fs.writeFileSync(dir + id + '.geo.json', Codecs.bedrock.compile());
	fs.writeFileSync(dir + id + '.animation.json', JSON.stringify(Animator.buildFile(null, true), null, '\t'));
	fs.writeFileSync(dir + id + '.bbmodel', Codecs.project.compile());
	return {id: id, bones: Group.all.length, cubes: Cube.all.length, animations: Animation.all.map(a => a.name)};
};

// Кадр анимации для проверки: показать анимацию name в момент time (секунды).
window.glPose = function (name, time) {
	const an = Animation.all.find(a => a.name === name);
	an.select();
	Timeline.setTime(time);
	Animator.preview();
	return an.name + ' @ ' + time;
};
