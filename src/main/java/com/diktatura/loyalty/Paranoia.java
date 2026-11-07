package com.diktatura.loyalty;

import com.diktatura.Diktatura;
import com.diktatura.network.FxPayloads;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Паранойя: чем выше страх, тем чаще мерещится. Всё это видит и слышит только сам игрок.
 * <ul>
 *   <li>страх от 20 — шаги за спиной (обернёшься — никого);</li>
 *   <li>от 35 — шёпот доносов в строке над хотбаром;</li>
 *   <li>от 50 — фантомный налоговый инспектор: стоит поодаль и смотрит, подойдёшь — исчезнет;</li>
 *   <li>от 70 — фальшивый указ вождя на весь экран, через пару секунд — «…показалось».</li>
 * </ul>
 * Раз в 30 секунд шанс видения: 0 при страхе 15 и ниже, ~45% при страхе 100.
 */
public final class Paranoia {
	public static final int WHISPERS = 8;
	public static final int FAKE_DECREES = 6;
	private static final int CHECK_TICKS = 20 * 30;

	private record Pending(long at, UUID player, Runnable run) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private Paranoia() {
	}

	public static void tick(MinecraftServer server) {
		long now = Diktatura.ticks();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.at() <= now) {
				it.remove();
				if (server.getPlayerManager().getPlayer(p.player()) != null) {
					p.run().run();
				}
			}
		}
		if (now % CHECK_TICKS != 17) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (rec.stage != Stage.FREE || p.isSpectator() || !p.isAlive()) {
				continue;
			}
			float chance = MathHelper.clamp((rec.fear - 15) / 85f, 0f, 1f) * 0.45f;
			if (p.getRandom().nextFloat() < chance) {
				hallucinate(p, rec.fear);
			}
		}
	}

	/** Видение по уровню страха (для проверки — команда /diktatura test fear). */
	public static void hallucinate(ServerPlayerEntity p, int fear) {
		int kinds = fear >= 70 ? 4 : fear >= 50 ? 3 : fear >= 35 ? 2 : 1;
		switch (p.getRandom().nextInt(kinds)) {
			case 0 -> steps(p);
			case 1 -> whisper(p);
			case 2 -> phantomInspector(p);
			default -> fakeDecree(p);
		}
	}

	/** Три шага прямо за спиной — звук только для этого игрока. */
	public static void steps(ServerPlayerEntity p) {
		for (int i = 0; i < 3; i++) {
			later(p, i * 9, () -> {
				Vec3d look = p.getRotationVec(1f);
				Vec3d behind = p.getPos().subtract(look.x * 3, 0, look.z * 3);
				sound(p, SoundEvents.BLOCK_GRAVEL_STEP, behind, 0.7f, 0.8f + p.getRandom().nextFloat() * 0.2f);
			});
		}
	}

	public static void whisper(ServerPlayerEntity p) {
		p.sendMessage(Text.translatable("diktatura.paranoia.whisper." + p.getRandom().nextInt(WHISPERS))
			.formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
		sound(p, SoundEvents.ENTITY_VILLAGER_AMBIENT, p.getPos(), 0.25f, 0.5f);
	}

	public static void phantomInspector(ServerPlayerEntity p) {
		if (ServerPlayNetworking.canSend(p, FxPayloads.Fx.ID)) {
			ServerPlayNetworking.send(p, new FxPayloads.Fx("phantom_tax", 20 * 12));
		}
	}

	public static void fakeDecree(ServerPlayerEntity p) {
		int n = p.getRandom().nextInt(FAKE_DECREES);
		Hud.headline(p, HeadlineStyle.DECREE, Text.translatable("diktatura.ui.headline.decree"),
			Text.translatable("diktatura.paranoia.decree." + n), 3);
		later(p, 70, () -> p.sendMessage(Text.translatable("diktatura.paranoia.decree.fake")
			.formatted(Formatting.GRAY, Formatting.ITALIC), true));
	}

	private static void sound(ServerPlayerEntity p, SoundEvent sound, Vec3d at, float volume, float pitch) {
		p.networkHandler.sendPacket(new PlaySoundS2CPacket(RegistryEntry.of(sound), SoundCategory.AMBIENT,
			at.x, at.y, at.z, volume, pitch, p.getRandom().nextLong()));
	}

	private static void later(ServerPlayerEntity p, int ticks, Runnable r) {
		PENDING.add(new Pending(Diktatura.ticks() + ticks, p.getUuid(), r));
	}
}
