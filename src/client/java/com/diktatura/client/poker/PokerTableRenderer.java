package com.diktatura.client.poker;

import com.diktatura.Diktatura;
import com.diktatura.casino.poker.PokerTableEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Покерный стол: модель GeckoLib ×2 и всё, что лежит на сукне, — карты (сдача из колоды по очереди,
 * переворот общих карт и карт на вскрытии), фишки ставок и банка (банк уезжает победителю), кнопка дилера,
 * а над местами — имя и стек. Всё это берётся из вида стола ({@link PokerTableEntity#view()}) и времени мира.
 */
public class PokerTableRenderer extends GeoEntityRenderer<PokerTableEntity> {
	private static final float SCALE = 2.0f;

	public PokerTableRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model());
		withScale(SCALE);
		this.shadowRadius = 2.0f;
		addRenderLayer(new Felt(this));
	}

	@Override
	protected void applyRotations(PokerTableEntity e, MatrixStack m, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
		super.applyRotations(e, m, ageInTicks, e.getYaw(), partialTick, nativeScale);
	}

	@Override
	public boolean shouldRender(PokerTableEntity e, Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBox().expand(2.5, 1.5, 2.5));
	}

	static final class Model extends GeoModel<PokerTableEntity> {
		@Override
		public Identifier getModelResource(PokerTableEntity e) {
			return Diktatura.id("geo/entity/poker_table.geo.json");
		}

		@Override
		public Identifier getTextureResource(PokerTableEntity e) {
			return Diktatura.id("textures/entity/poker_table.png");
		}

		@Override
		public Identifier getAnimationResource(PokerTableEntity e) {
			return Diktatura.id("animations/entity/poker_table.animation.json");
		}
	}

	/** Карты, фишки и подписи на столе. */
	static final class Felt extends GeoRenderLayer<PokerTableEntity> {
		private static final RenderLayer CARDS = RenderLayer.getEntityCutoutNoCull(PokerScreen.CARDS);
		private static final float CW = 0.28f;
		private static final float CH = 0.39f;
		private static final float Y = (float) PokerTableEntity.FELT + 0.004f;
		private static final int[] CHIP_VALUES = {500, 100, 25, 5, 1};
		private static final int[] CHIP_TILE = {4, 3, 2, 1, 0};

		Felt(PokerTableRenderer r) {
			super(r);
		}

		@Override
		public void render(MatrixStack m, PokerTableEntity e, BakedGeoModel model, @Nullable RenderLayer rt,
		                   VertexConsumerProvider vcp, @Nullable VertexConsumer buffer, float tickDelta, int light, int overlay) {
			NbtCompound v = e.view();
			if (v.isEmpty()) {
				return;
			}
			float t = e.getWorld().getTime() + tickDelta;
			m.push();
			m.scale(1 / SCALE, 1 / SCALE, 1 / SCALE);
			float rot = 180.0f - e.getYaw();
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rot));
			VertexConsumer vc = vcp.getBuffer(CARDS);
			NbtList seats = v.getList("seats", NbtElement.COMPOUND_TYPE);
			int dealer = v.getInt("dealer");
			long dealt = v.getLong("dealt");
			boolean live = v.getBoolean("live");
			long showAt = v.getLong("showAt");
			// Порядок сдачи: слева от дилера по кругу, по карте за круг.
			int[] order = new int[6];
			int n = 0;
			for (int k = 1; k <= 6; k++) {
				int i = (dealer + k) % 6;
				if (i < seats.size() && seats.getCompound(i).getBoolean("in")) {
					order[n++] = i;
				}
			}
			for (int idx = 0; idx < n; idx++) {
				int i = order[idx];
				NbtCompound s = seats.getCompound(i);
				if (s.getBoolean("fold")) {
					continue;
				}
				int[] shown = s.getIntArray("cards");
				for (int c = 0; c < 2; c++) {
					float start = dealt + (c * n + idx) * 3;
					if (t < start) {
						continue;
					}
					float fly = MathHelper.clamp((t - start) / 6f, 0, 1);
					double[] target = holePos(i, c);
					double yaw = seatYaw(i);
					float flip = shown.length == 2 && showAt > 0 ? MathHelper.clamp((t - showAt) / 8f, 0, 1) : 0;
					drawCard(m, vc, light, lerp(PokerTableEntity.DECK, target, fly), arc(fly), (float) yaw + (c == 0 ? -8 : 8),
						shown.length == 2 ? shown[c] : -1, flip);
				}
			}
			// Общие карты: прилетают рубашкой вверх и переворачиваются.
			int[] board = v.getIntArray("board");
			long[] boardAt = v.getLongArray("boardAt");
			for (int i = 0; i < board.length && i < boardAt.length; i++) {
				if (boardAt[i] <= 0 || t < boardAt[i] - 6) {
					continue;
				}
				float fly = MathHelper.clamp((t - (boardAt[i] - 6)) / 6f, 0, 1);
				float flip = MathHelper.clamp((t - boardAt[i]) / 6f, 0, 1);
				double[] target = {(i - 2) * 0.34, 0.0};
				drawCard(m, vc, light, lerp(PokerTableEntity.DECK, target, fly), arc(fly), 180f, board[i], flip);
			}
			// Ставки перед местами.
			int wonTotal = 0;
			for (int i = 0; i < seats.size(); i++) {
				NbtCompound s = seats.getCompound(i);
				int bet = s.getInt("bet");
				wonTotal += s.getInt("won");
				if (bet > 0 && live) {
					double[] p = toward(i, 0.36);
					chips(m, vc, light, p[0], p[1], bet);
				}
			}
			// Банк: в центре, после розыгрыша едет к победителю.
			long potAt = v.getLong("potAt");
			int winner = v.getInt("winner");
			double[] potPos = {0.0, 0.48};
			if (live && v.getInt("pot") > 0) {
				int inBets = 0;
				for (int i = 0; i < seats.size(); i++) {
					inBets += seats.getCompound(i).getInt("bet");
				}
				if (v.getInt("pot") - inBets > 0) {
					chips(m, vc, light, potPos[0], potPos[1], v.getInt("pot") - inBets);
				}
			} else if (potAt > 0 && wonTotal > 0 && winner >= 0 && t < potAt + 14) {
				float k = MathHelper.clamp((t - potAt) / 12f, 0, 1);
				double[] p = lerp(potPos, toward(winner, 0.5), k);
				chips(m, vc, light, p[0], p[1], wonTotal);
			}
			// Кнопка дилера.
			if (dealer >= 0 && dealer < 6) {
				double[] p = toward(dealer, 0.42);
				double[] side = perp(dealer, 0.28);
				chipQuad(m, vc, light, p[0] + side[0], Y + 0.012f, p[1] + side[1], 0.12f, 5);
			}
			// Подписи над местами.
			labels(m, e, vcp, seats, v.getInt("toAct"), live, rot, light);
			m.pop();
		}

		private static float arc(float k) {
			return (float) Math.sin(k * Math.PI) * 0.25f;
		}

		private static double[] lerp(double[] a, double[] b, float k) {
			return new double[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k};
		}

		/** Точка на линии «место → центр стола»: k — доля пути от центра к месту. */
		private static double[] toward(int seat, double k) {
			double[] s = PokerTableEntity.SEATS[seat];
			return new double[]{s[0] * k, s[1] * k};
		}

		private static double[] perp(int seat, double d) {
			double[] s = PokerTableEntity.SEATS[seat];
			double len = Math.sqrt(s[0] * s[0] + s[1] * s[1]);
			return new double[]{-s[1] / len * d, s[0] / len * d};
		}

		private static double[] holePos(int seat, int c) {
			double[] p = toward(seat, 0.52);
			double[] side = perp(seat, c == 0 ? -0.1 : 0.1);
			return new double[]{p[0] + side[0], p[1] + side[1]};
		}

		/** Поворот карты: верх смотрит от места к центру (хозяин читает её прямо). */
		private static double seatYaw(int seat) {
			double[] s = PokerTableEntity.SEATS[seat];
			return Math.toDegrees(Math.atan2(s[0], s[1]));
		}

		/**
		 * Карта на сукне в точке p (x, z), приподнята на lift, повёрнута на yaw (градусы вокруг вертикали).
		 * flip 0 — рубашкой вверх, 1 — лицом вверх (card &lt; 0 — всегда рубашка).
		 */
		private static void drawCard(MatrixStack m, VertexConsumer vc, int light, double[] p, float lift, float yaw, int card, float flip) {
			m.push();
			m.translate(p[0], Y + lift, p[1]);
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
			float angle = card < 0 ? 180f : 180f * (1 - flip);
			boolean faceUp = angle <= 90f;
			m.translate(0, Math.sin(Math.toRadians(angle)) * CW / 2, 0);
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(faceUp ? angle : angle - 180f));
			float u0;
			float v0;
			if (faceUp) {
				u0 = (card % 13) * 20;
				v0 = (card / 13) * 28;
			} else {
				u0 = 13 * 20;
				v0 = 0;
			}
			quadUp(m, vc, light, -CW / 2, -CH / 2, CW / 2, CH / 2, 0, u0 / 512f, v0 / 128f, (u0 + 20) / 512f, (v0 + 28) / 128f);
			m.pop();
		}

		/** Горизонтальный прямоугольник [x0..x1]×[z0..z1] на высоте y, верх текстуры — к -Z. */
		private static void quadUp(MatrixStack m, VertexConsumer vc, int light, float x0, float z0, float x1, float z1, float y,
		                           float u0, float v0, float u1, float v1) {
			MatrixStack.Entry en = m.peek();
			Matrix4f mat = en.getPositionMatrix();
			vc.vertex(mat, x0, y, z0).color(-1).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, 0, 1, 0);
			vc.vertex(mat, x0, y, z1).color(-1).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, 0, 1, 0);
			vc.vertex(mat, x1, y, z1).color(-1).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, 0, 1, 0);
			vc.vertex(mat, x1, y, z0).color(-1).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, 0, 1, 0);
		}

		/** Стопки фишек на сумму amount (по номиналам, до 6 стопок по 8 фишек). */
		private static void chips(MatrixStack m, VertexConsumer vc, int light, double x, double z, int amount) {
			int stack = 0;
			int left = amount;
			for (int d = 0; d < CHIP_VALUES.length && stack < 6; d++) {
				int count = left / CHIP_VALUES[d];
				left -= count * CHIP_VALUES[d];
				while (count > 0 && stack < 6) {
					int h = Math.min(8, count);
					count -= h;
					double sx = x + (stack % 3 - 1) * 0.11;
					double sz = z + (stack / 3) * 0.11;
					for (int c = 0; c < h; c++) {
						chipBox(m, vc, light, sx, Y + c * 0.02f, sz, CHIP_TILE[d]);
					}
					stack++;
				}
			}
		}

		private static void chipBox(MatrixStack m, VertexConsumer vc, int light, double x, float y, double z, int tile) {
			float r = 0.05f;
			float h = 0.02f;
			m.push();
			m.translate(x, y, z);
			float tu0 = tile * 8 / 512f;
			float tu1 = (tile * 8 + 8) / 512f;
			quadUp(m, vc, light, -r, -r, r, r, h, tu0, 112 / 128f, tu1, 120 / 128f);
			MatrixStack.Entry en = m.peek();
			Matrix4f mat = en.getPositionMatrix();
			float sv0 = 120 / 128f;
			float sv1 = 122 / 128f;
			for (int side = 0; side < 4; side++) {
				m.push();
				m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 90));
				MatrixStack.Entry e2 = m.peek();
				Matrix4f m2 = e2.getPositionMatrix();
				vc.vertex(m2, -r, 0, -r).color(-1).texture(tu0, sv1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e2, 0, 0, -1);
				vc.vertex(m2, -r, h, -r).color(-1).texture(tu0, sv0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e2, 0, 0, -1);
				vc.vertex(m2, r, h, -r).color(-1).texture(tu1, sv0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e2, 0, 0, -1);
				vc.vertex(m2, r, 0, -r).color(-1).texture(tu1, sv1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e2, 0, 0, -1);
				m.pop();
			}
			m.pop();
		}

		/** Плоский кружок-фишка (кнопка дилера: tile 5). */
		private static void chipQuad(MatrixStack m, VertexConsumer vc, int light, double x, float y, double z, float size, int tile) {
			m.push();
			m.translate(x, y, z);
			float u0 = tile * 8 / 512f;
			quadUp(m, vc, light, -size / 2, -size / 2, size / 2, size / 2, 0, u0, 112 / 128f, u0 + 8 / 512f, 120 / 128f);
			m.pop();
		}

		private static void labels(MatrixStack m, PokerTableEntity e, VertexConsumerProvider vcp, NbtList seats, int toAct,
		                           boolean live, float rot, int light) {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || mc.player.squaredDistanceTo(e) > 16 * 16) {
				return;
			}
			TextRenderer tr = mc.textRenderer;
			for (int i = 0; i < seats.size(); i++) {
				NbtCompound s = seats.getCompound(i);
				if (!s.getBoolean("on")) {
					continue;
				}
				Text name;
				try {
					name = Text.Serialization.fromJson(s.getString("name"), e.getRegistryManager());
				} catch (RuntimeException ex) {
					name = null;
				}
				Text line = Text.empty().append(name == null ? Text.literal("?") : name).append(" · " + s.getInt("stack"));
				double[] p = PokerTableEntity.SEATS[i];
				m.push();
				m.translate(p[0], 1.55, p[1]);
				m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rot));
				m.multiply(mc.getEntityRenderDispatcher().getRotation());
				m.scale(0.02f, -0.02f, 0.02f);
				Matrix4f mat = m.peek().getPositionMatrix();
				int color = live && toAct == i ? 0xFFFFD040 : s.getBoolean("fold") && live ? 0xFF9A9AA0 : 0xFFFFFFFF;
				float x = -tr.getWidth(line) / 2f;
				tr.draw(line, x, 0, color, false, mat, vcp, TextRenderer.TextLayerType.NORMAL, 0x60000000, light);
				int won = s.getInt("won");
				if (won > 0) {
					Text w = Text.translatable("diktatura.poker.won", won);
					tr.draw(w, -tr.getWidth(w) / 2f, -10, 0xFF60FF60, false, mat, vcp, TextRenderer.TextLayerType.NORMAL, 0x60000000, light);
				}
				m.pop();
			}
		}
	}
}
