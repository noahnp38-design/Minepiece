package minepiece.noah.client.haki;

import com.mojang.blaze3d.vertex.PoseStack;
import minepiece.noah.MinePiece;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Rendu du Haki sur le joueur (visible par tous, en 3e personne ; la 1re personne passe par submitFirstPerson) :
 * - Koka : noir uni, sans détail (9 étapes de propagation) ;
 *   9 étapes de texture par partie, avec un front irrégulier
 *   en gouttes. Selon la maîtrise de l'Armement : 1 avant-bras, 2 bras entiers, 3 + jambes (depuis les pieds),
 *   4 + tête (depuis le cou), 5 corps entier (le torse depuis les épaules). Les parties se recouvrent l'une après l'autre
 *   à l'activation et se retirent ensemble quand le Haki est coupé ;
 * - imprégnation des Rois : coque d'arcs noirs et rouges animée (6 images, pleine luminosité) autour des membres ;
 * - Observation : yeux rouges brièvement à l'activation, en continu pendant la Vision du futur.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class HakiLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Koka (style Vergo) : 9 étapes de propagation, 5 intensités de reflets, 6 images de l'éclat de durcissement. */
	private static final Identifier[] KOKA = new Identifier[9];
	/** Montée des reflets après le recouvrement d'une partie, et durée de chaque image de l'éclat (ticks). */
	private static final float GLOSS_TICKS = 8.0F;
	private static final float SHINE_STEP = 2.0F;
	/** Ordre de propagation (indices bras 0, jambes 1, tête 2, torse 3) : bras -> torse -> jambes -> tête. */
	private static final int[] ORDER = {0, 3, 1, 2};
	private static final Identifier[] ARCS = new Identifier[6];
	private static final Identifier EYES_TEX = MinePiece.id("textures/entity/haki/eyes.png");
	/** Durée de recouvrement de chaque partie (ticks) et du retrait. */
	private static final float PART_TICKS = 14.0F;
	private static final float UNCOVER_TICKS = 12.0F;

	static {
		for (int i = 0; i < KOKA.length; i++) {
			KOKA[i] = MinePiece.id("textures/entity/haki/koka_" + i + ".png");
		}
		for (int i = 0; i < ARCS.length; i++) {
			ARCS[i] = MinePiece.id("textures/entity/haki/king_arcs_" + i + ".png");
		}
	}

	private static HakiArmModel firstWide;
	private static HakiArmModel firstSlim;
	private static HakiArmModel firstArcs;

	private final HakiArmModel kokaWide;
	private final HakiArmModel kokaSlim;
	private final HakiArmModel arcsWide;
	private final HakiArmModel arcsSlim;
	private final HakiArmModel eyes;

	public HakiLayer(AvatarRenderer renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.kokaWide = new HakiArmModel(context.bakeLayer(HakiArmModel.KOKA_WIDE));
		this.kokaSlim = new HakiArmModel(context.bakeLayer(HakiArmModel.KOKA_SLIM));
		this.arcsWide = new HakiArmModel(context.bakeLayer(HakiArmModel.ARCS_WIDE));
		this.arcsSlim = new HakiArmModel(context.bakeLayer(HakiArmModel.ARCS_SLIM));
		this.eyes = new HakiArmModel(context.bakeLayer(HakiArmModel.EYES));
		if (firstWide == null) {
			firstWide = new HakiArmModel(context.bakeLayer(HakiArmModel.KOKA_WIDE));
			firstSlim = new HakiArmModel(context.bakeLayer(HakiArmModel.KOKA_SLIM));
			firstArcs = new HakiArmModel(context.bakeLayer(HakiArmModel.ARCS_WIDE));
		}
	}

	/**
	 * État du Koka (null si absent) : [0..3] recouvrement {bras, jambes, tête, torse} (0..1), [4..7] intensité des
	 * reflets de chaque partie (0..1), [8] image de l'éclat de durcissement (-1 = aucun).
	 * Activation : 1. concentration (pose) ; 2. propagation, les parties débloquées l'une après l'autre (bras -> torse ->
	 * jambes -> tête), front violet ; 3. durcissement : les reflets blancs et violets apparaissent sur chaque partie
	 * recouverte, puis un éclat traverse tout le revêtement ; 4. Haki actif, reflets STABLES (pas de clignotement).
	 * Désactivation : tout se retire ensemble.
	 */
	public static float[] covers(Entity e, float partial) {
		Integer f = e.getAttached(ModAttachments.HAKI_FLAGS);
		Long t = e.getAttached(ModAttachments.HAKI_ARM_TIME);
		int flags = f == null ? 0 : f;
		int level = (flags >> 5) & 7;
		boolean on = (flags & 1) != 0;
		if (level <= 0 || (!on && t == null)) {
			return null;
		}
		float[] max = {level == 1 ? 0.55F : 1.0F, level >= 3 ? 1.0F : 0.0F, level >= 4 ? 1.0F : 0.0F, level >= 5 ? 1.0F : 0.0F};
		float age = t == null ? 1000.0F : e.level().getGameTime() - t + partial;
		float[] out = new float[9];
		out[8] = -1.0F;
		if (on) {
			int order = 0;
			for (int i : ORDER) {
				if (max[i] <= 0.0F) {
					continue;
				}
				float start = order * PART_TICKS;
				out[i] = Math.max(0.0F, Math.min(1.0F, (age - start) / PART_TICKS)) * max[i];
				out[4 + i] = Math.max(0.0F, Math.min(1.0F, (age - start - PART_TICKS) / GLOSS_TICKS));
				order++;
			}
			out[0] = Math.max(0.02F, out[0]);
			float end = order * PART_TICKS + GLOSS_TICKS * 0.5F;
			if (age >= end && age < end + 6 * SHINE_STEP) {
				out[8] = (int) ((age - end) / SHINE_STEP);
			}
			return out;
		}
		float k = 1.0F - age / UNCOVER_TICKS;
		if (k <= 0.0F) {
			return null;
		}
		for (int i = 0; i < 4; i++) {
			out[i] = max[i] * k;
			out[4 + i] = max[i] > 0.0F ? k : 0.0F;
		}
		return out;
	}

	/** Recouvrement des bras seuls (vue à la 1re personne). */
	public static float cover(Entity e, float partial) {
		float[] c = covers(e, partial);
		return c == null || c[0] <= 0.0F ? -1.0F : c[0];
	}

	/** Image des arcs noirs et rouges (-1 si l'imprégnation n'est pas active). */
	public static int kingFrame(Entity e) {
		Integer f = e.getAttached(ModAttachments.HAKI_FLAGS);
		return f != null && (f & (8 | 16)) != 0 ? (int) ((e.level().getGameTime() / 2 + e.getId()) % ARCS.length) : -1;
	}

	private static Identifier koka(float cover) {
		return KOKA[Math.max(1, Math.min(8, Math.round(cover * 8.0F)))];
	}


	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
		float[] koka = state.getData(HakiRenderData.KOKA);
		if (koka != null) {
			HakiArmModel model = slim ? kokaSlim : kokaWide;
			int fullMask = 0;
			for (int i = 0; i < 4; i++) {
				if (koka[i] <= 0.0F) {
					continue;
				}
				// 1. matière noire (éclairée par le monde : volume) avec sa propre étape de propagation
				ArmorRenderer.submitTransformCopyingModel(this.getParentModel(), state, model, 1 << i, true,
						collector.order(2), poseStack, RenderTypes.entityTranslucent(koka(koka[i])), light, OverlayTexture.NO_OVERLAY,
						state.outlineColor);
			}
		}
		Integer frame = state.getData(HakiRenderData.KING_FRAME);
		if (frame != null && frame >= 0) {
			ArmorRenderer.submitTransformCopyingModel(this.getParentModel(), state, slim ? arcsSlim : arcsWide, 3, true,
					collector.order(3), poseStack, RenderTypes.entityTranslucent(ARCS[frame]), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					state.outlineColor);
		}
		if (Boolean.TRUE.equals(state.getData(HakiRenderData.EYES))) {
			ArmorRenderer.submitTransformCopyingModel(this.getParentModel(), state, eyes, 4, true,
					collector.order(3), poseStack, RenderTypes.entityTranslucent(EYES_TEX), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					state.outlineColor);
		}
	}

	/** Vue à la 1re personne : le Koka et les arcs sont dessinés sur le bras affiché à l'écran (même pose). */
	public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, boolean right,
			Entity player, boolean slim, float partial) {
		if (firstWide == null) {
			return;
		}
		float[] k = covers(player, partial);
		if (k != null && k[0] > 0.0F) {
			HakiArmModel m = slim ? firstSlim : firstWide;
			m.setupAnim(1);
			ModelPart part = right ? m.rightArm() : m.leftArm();
			part.loadPose(arm.storePose());
			collector.order(1).submitModelPart(part, poseStack, RenderTypes.entityTranslucent(koka(k[0])), light, OverlayTexture.NO_OVERLAY, null);
		}
		int frame = kingFrame(player);
		if (frame >= 0) {
			firstArcs.setupAnim(1);
			ModelPart part = right ? firstArcs.rightArm() : firstArcs.leftArm();
			part.loadPose(arm.storePose());
			collector.order(2).submitModelPart(part, poseStack, RenderTypes.entityTranslucent(ARCS[frame]), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
		}
	}
}
