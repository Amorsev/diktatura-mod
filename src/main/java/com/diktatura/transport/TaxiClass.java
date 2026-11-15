package com.diktatura.transport;

import com.diktatura.config.DiktaturaConfig;
import net.minecraft.util.math.Vec3d;

/**
 * Классы госТакси. Модели — {@code blockbench/gen_taxi.py}. Место пассажира {@link #seat} — в блоках
 * относительно кареты (z < 0 — назад от центра): игрок сидит бёдрами на сиденье модели.
 */
public enum TaxiClass {
	/** «Телега трудящегося»: медленно, трясёт, иногда ломается. */
	ECONOMY("economy", 0.55, seat(17, 15)),
	/** «Повозка служащего». */
	COMFORT("comfort", 0.9, seat(15, 21)),
	/** «Карета номенклатуры»: быстро, с кучером. */
	BUSINESS("business", 1.4, seat(16, 23));

	public final String id;
	/** Скорость в блоках за тик. */
	public final double speed;
	public final Vec3d seat;

	TaxiClass(String id, double speed, Vec3d seat) {
		this.id = id;
		this.speed = speed;
		this.seat = seat;
	}

	/**
	 * Сиденье модели (пиксели Blockbench: высота поверхности и расстояние к задку) → точка посадки.
	 * У игрока точка «транспорта» на 0.6 блока выше ступней, бёдра — на 0.75: отсюда −0.15.
	 */
	private static Vec3d seat(int y, int z) {
		return new Vec3d(0.0, y / 16.0 - 0.15, -z / 16.0);
	}

	public int basePrice() {
		DiktaturaConfig c = DiktaturaConfig.get();
		return switch (this) {
			case ECONOMY -> c.taxiPriceEconomy;
			case COMFORT -> c.taxiPriceComfort;
			case BUSINESS -> c.taxiPriceBusiness;
		};
	}

	public String nameKey() {
		return "diktatura.taxi." + id;
	}

	public static TaxiClass byOrdinal(int i) {
		TaxiClass[] v = values();
		return i >= 0 && i < v.length ? v[i] : ECONOMY;
	}
}
