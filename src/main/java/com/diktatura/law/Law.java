package com.diktatura.law;

import com.diktatura.punishment.Punishment;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Закон, загруженный из data/&lt;namespace&gt;/diktatura/laws/&lt;id&gt;.json.
 *
 * @param prevent          отменять ли само действие (сломать блок, написать в чат...), если это возможно
 * @param enabledByDefault действует с самого начала мира
 * @param permanent        диктатор никогда его не отменяет, и он действует даже во время восстания
 * @param weight           шанс, что диктатор выберет этот закон в новом указе
 * @param exemptLicenses   лицензии, владельцы которых этот закон не нарушают (поле exempt_license)
 */
public record Law(Identifier id, LawCheck check, List<Punishment> punishments, int cooldownTicks,
                  boolean prevent, boolean enabledByDefault, boolean permanent, int weight,
                  List<String> exemptLicenses) {

	public String key() {
		return id.toString();
	}

	public static String translationKey(String lawId) {
		Identifier id = Identifier.tryParse(lawId);
		return id == null ? "law.unknown" : "law." + id.getNamespace() + "." + id.getPath();
	}

	public MutableText name() {
		return Text.translatable(translationKey(key()));
	}

	public MutableText description() {
		return Text.translatable(translationKey(key()) + ".desc");
	}
}
