package minepiece.noah.client.render;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Definition des modeles 3D de chaque race.
 * Les coordonnees sont celles du modele du joueur : Y vers le bas, le dos est du cote +Z.
 * La texture (textures/entity/race_extras.png) fait 128x224 : ailes Lunarien (plumes noires) a partir de y = 64,
 * ailes Skypieen (memes plumes, blanches) 80 px plus bas, a partir de y = 144 (tools/make_black_wings.py).
 */
public final class RaceModels {
	public static final int TEX_W = 128;
	public static final int TEX_H = 224;

	public static final float WING_TILT_LEFT = -0.35F;
	public static final float WING_TILT_RIGHT = 0.35F;
	public static final float WING_SWEEP = 0.5F;

	public static final ModelLayerLocation SKYPIEAN = loc("skypiean");
	public static final ModelLayerLocation FISHMAN = loc("fishman");
	public static final ModelLayerLocation LUNARIAN = loc("lunarian");
	public static final ModelLayerLocation LUNARIAN_FLAME = loc("lunarian_flame");

	private RaceModels() {}

	private static ModelLayerLocation loc(String name) {
		return new ModelLayerLocation(MinePiece.id(name), "main");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(SKYPIEAN, RaceModels::skypiean);
		ModelLayerRegistry.registerModelLayer(FISHMAN, RaceModels::fishman);
		ModelLayerRegistry.registerModelLayer(LUNARIAN, RaceModels::lunarian);
		ModelLayerRegistry.registerModelLayer(LUNARIAN_FLAME, RaceModels::lunarianFlame);
	}

	// ------------------------------------------------------------------ squelette commun

	private static final class Parts {
		final MeshDefinition mesh = new MeshDefinition();
		final PartDefinition root = mesh.getRoot();
		PartDefinition body;

		Parts(CubeListBuilder head, CubeListBuilder body, CubeListBuilder rightArm, CubeListBuilder leftArm,
				CubeListBuilder rightLeg, CubeListBuilder leftLeg) {
			root.addOrReplaceChild("head", head, PartPose.ZERO);
			this.body = root.addOrReplaceChild("body", body, PartPose.ZERO);
			root.addOrReplaceChild("right_arm", rightArm, PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", leftArm, PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", rightLeg, PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", leftLeg, PartPose.offset(1.9F, 12.0F, 0.0F));
		}

		LayerDefinition build() {
			return LayerDefinition.create(mesh, TEX_W, TEX_H);
		}
	}

	private static CubeListBuilder none() {
		return CubeListBuilder.create();
	}

	// ------------------------------------------------------------------ ailes

	/** Ailes a 5 rangees de plumes. u = 0 (blanc) ou 32 (noir) dans la texture. */
	private static void wings(PartDefinition body, int u) {
		float[] lens = {12, 14, 13, 10, 7};
		CubeListBuilder left = CubeListBuilder.create();
		CubeListBuilder right = CubeListBuilder.create();
		for (int i = 0; i < lens.length; i++) {
			float y = -6.0F + i * 3.0F;
			left.texOffs(u, i * 4).addBox(0.0F, y, 0.0F, lens[i], 3.0F, 1.0F);
			right.texOffs(u, i * 4).addBox(-lens[i], y, 0.0F, lens[i], 3.0F, 1.0F);
		}
		body.addOrReplaceChild("wing_l", left, PartPose.offsetAndRotation(1.5F, 3.0F, 2.0F, 0.0F, -WING_SWEEP, WING_TILT_LEFT));
		body.addOrReplaceChild("wing_r", right, PartPose.offsetAndRotation(-1.5F, 3.0F, 2.0F, 0.0F, WING_SWEEP, WING_TILT_RIGHT));
	}

	// ------------------------------------------------------------------ grandes ailes Lunarien

	private static final int FEATHERS = 9;
	/** Longueur des plumes (premier plan / arriere-plan). Doit correspondre a tools/make_lunarian_wings.py. */
	private static final int[] LEN_FRONT = {13, 16, 19, 21, 23, 25, 27, 27, 28};
	private static final int[] LEN_BACK = {11, 14, 16, 18, 20, 21, 23, 23, 24};
	private static final int COVERT_LEN = 10;
	private static final int ARM_SEGMENTS = 6;

	/** Point de l'arche (bord d'attaque) de l'aile, t de 0 (epaule) a 1 (bout). x vers l'exterieur, y vers le bas. */
	private static float archX(float t) {
		return 20.0F * t;
	}

	/** Courbure de l'arche : 1 = ailes Lunarien ; plus grand = aile plus arquée (Skypiéen). */
	private static float arc = 1.0F;

	private static float archY(float t) {
		float r = 1.0F - t;
		// arche qui monte puis dont le bout retombe : plus "arc" est grand, plus la courbe est prononcée
		return -12.0F * arc * (1.0F - r * r) + 3.0F * arc * arc * t * t * t;
	}

	/**
	 * Aile en eventail : une arche (os de l'aile) et trois rangees de plumes qui retombent, de plus en plus longues
	 * vers le bout, ecartees en eventail. side = +1 pour l'aile gauche (x positif), -1 pour la droite.
	 */
	private static void bigWing(PartDefinition body, String name, float side, float ox, float yRot, float zRot, int v) {
		PartDefinition wing = body.addOrReplaceChild(name, none(),
				PartPose.offsetAndRotation(ox, 3.0F, 2.0F, 0.0F, yRot, zRot));

		// Arche : segments de 6x3x2 le long de la courbe (texture a (0, 136)).
		for (int k = 0; k < ARM_SEGMENTS; k++) {
			float t0 = k / (float) ARM_SEGMENTS;
			float t1 = (k + 1) / (float) ARM_SEGMENTS;
			float dx = archX(t1) - archX(t0);
			float dy = archY(t1) - archY(t0);
			float angle = (float) Math.atan2(dy, dx);
			CubeListBuilder bar = side > 0
					? CubeListBuilder.create().texOffs(0, 136 + v).addBox(-0.5F, -1.5F, 0.5F, 6.0F, 3.0F, 2.0F)
					: CubeListBuilder.create().texOffs(0, 136 + v).addBox(-5.5F, -1.5F, 0.5F, 6.0F, 3.0F, 2.0F);
			wing.addOrReplaceChild("arm" + k, bar,
					PartPose.offsetAndRotation(side * archX(t0), archY(t0), 0.0F, 0.0F, 0.0F, side * angle));
		}

		// Plumes. Chaque plume = boite 3 x (L+1) x 1 pivotant a son point d'attache sur l'arche.
		for (int i = 0; i < FEATHERS; i++) {
			float t = (i + 0.5F) / FEATHERS;
			float fan = (-0.25F + 0.075F * i) * (0.6F + 0.4F * arc);
			float px = side * archX(t);
			float py = archY(t);

			// arriere-plan (z 0..1), decale d'une demi-plume pour boucher les trous
			float tb = (i + 1.0F) / FEATHERS;
			{
				int kb = FEATHERS + i;
				wing.addOrReplaceChild("fb" + i,
						CubeListBuilder.create().texOffs((kb % 16) * 8, 64 + v + (kb / 16) * 30)
								.addBox(-1.5F, -1.0F, 0.0F, 3.0F, LEN_BACK[i] + 1.0F, 1.0F),
						PartPose.offsetAndRotation(side * archX(Math.min(1.0F, tb)), archY(Math.min(1.0F, tb)) + 0.5F, 0.0F,
								0.0F, 0.0F, -side * (fan + 0.04F)));
			}
			// premier plan (z 1..2)
			wing.addOrReplaceChild("fa" + i,
					CubeListBuilder.create().texOffs((i % 16) * 8, 64 + v + (i / 16) * 30)
							.addBox(-1.5F, -1.0F, 1.0F, 3.0F, LEN_FRONT[i] + 1.0F, 1.0F),
					PartPose.offsetAndRotation(px, py, 0.0F, 0.0F, 0.0F, -side * fan));
			// petites plumes de couverture (z 2..3)
			wing.addOrReplaceChild("fc" + i,
					CubeListBuilder.create().texOffs(i * 8, 124 + v)
							.addBox(-1.5F, -1.0F, 2.0F, 3.0F, COVERT_LEN + 1.0F, 1.0F),
					PartPose.offsetAndRotation(px, py + 0.5F, 0.0F, 0.0F, 0.0F, -side * fan));
		}
	}

	/** Grandes ailes blanches a plumes (meme forme que les ailes Lunarien, texture blanche / bleu clair). */
	private static LayerDefinition skypiean() {
		Parts p = new Parts(none(), none(), none(), none(), none(), none());
		arc = 1.45F;
		bigWing(p.body, "wing_l", 1.0F, 1.5F, -WING_SWEEP, WING_TILT_LEFT, 80);
		bigWing(p.body, "wing_r", -1.0F, -1.5F, WING_SWEEP, WING_TILT_RIGHT, 80);
		arc = 1.0F;
		return p.build();
	}

	// ------------------------------------------------------------------ queue + flamme

	/** Nombre de vertebres de la queue (RaceExtrasModel retrouve la flamme au bout de la chaine). */
	public static final int TAIL_SEGMENTS = 9;
	/** Courbure (radians) de chaque articulation : la queue part vers l'arriere puis remonte en S. */
	private static final float[] TAIL_BEND = {-0.10F, 0.00F, 0.05F, 0.10F, 0.15F, 0.20F, 0.25F, 0.30F, 0.35F};

	/** Queue segmentee (vertebres violet sombre + crete dorsale) terminee par une flamme couleur feu. */
	private static void tail(PartDefinition body, boolean withTail, boolean withFlame) {
		PartDefinition parent = body;
		float total = 0.0F;
		for (int i = 0; i < TAIL_SEGMENTS; i++) {
			boolean thick = i < 4;
			float size = thick ? 3.0F : 2.0F;
			CubeListBuilder cubes = none();
			if (withTail) {
				cubes = thick
						? CubeListBuilder.create().texOffs(64, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F)
						: CubeListBuilder.create().texOffs(64, 8).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F);
				if (i % 2 == 0) {
					cubes = cubes.texOffs(80, 0).addBox(-0.5F, -size / 2.0F - 1.0F, 0.5F, 1.0F, 1.0F, 2.0F);
				}
			}
			PartPose pose = i == 0
					? PartPose.offsetAndRotation(0.0F, 10.5F, 2.0F, TAIL_BEND[0], 0.0F, 0.0F)
					: PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, TAIL_BEND[i], 0.0F, 0.0F);
			parent = parent.addOrReplaceChild("tail" + (i + 1), cubes, pose);
			total += TAIL_BEND[i];
		}
		// La flamme compense la courbure cumulee pour pointer vers le haut (legerement penchee vers l'arriere).
		// Elle est faite de plans SANS epaisseur (aucun bord visible) : 3 couches croisees (rouge, orange, coeur clair)
		// + 4 braises. Toutes les parties existent aussi sans flamme (vides) pour garder la meme structure.
		PartDefinition flame = parent.addOrReplaceChild("flame", none(),
				PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, -total + 0.2F, 0.0F, 0.0F));
		String[] layers = {"outer", "mid", "core"};
		for (int l = 0; l < layers.length; l++) {
			PartDefinition layer = flame.addOrReplaceChild(layers[l], none(), PartPose.ZERO);
			for (int k = 0; k < 2; k++) {
				layer.addOrReplaceChild(layers[l] + k, withFlame ? flamePlane(l) : none(),
						PartPose.rotation(0.0F, (float) (k * Math.PI / 2.0), 0.0F));
			}
		}
		for (int e = 0; e < EMBERS; e++) {
			flame.addOrReplaceChild("ember" + e,
					withFlame ? CubeListBuilder.create().texOffs(88, 40).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F) : none(),
					PartPose.offset(0.0F, -8.0F, 0.0F));
		}
	}

	/** Nombre de braises qui s'envolent de la flamme. */
	public static final int EMBERS = 4;

	/** Un plan de flamme d'epaisseur 0 : 0 = couche rouge (8x14), 1 = orange (6x11), 2 = coeur clair (3x7). */
	private static CubeListBuilder flamePlane(int layer) {
		return switch (layer) {
			case 0 -> CubeListBuilder.create().texOffs(64, 24).addBox(-4.0F, -14.0F, 0.0F, 8.0F, 14.0F, 0.0F);
			case 1 -> CubeListBuilder.create().texOffs(64, 40).addBox(-3.0F, -11.0F, 0.0F, 6.0F, 11.0F, 0.0F);
			default -> CubeListBuilder.create().texOffs(80, 40).addBox(-1.5F, -7.0F, 0.0F, 3.0F, 7.0F, 0.0F);
		};
	}

	/** Ailes noires + queue (sans flamme). */
	private static LayerDefinition lunarian() {
		Parts p = new Parts(none(), none(), none(), none(), none(), none());
		arc = 1.45F;   // mêmes ailes arquées que les Skypiéens, plumes noires (texture y 64..143)
		bigWing(p.body, "wing_l", 1.0F, 1.5F, -WING_SWEEP, WING_TILT_LEFT, 0);
		bigWing(p.body, "wing_r", -1.0F, -1.5F, WING_SWEEP, WING_TILT_RIGHT, 0);
		arc = 1.0F;
		tail(p.body, true, false);
		return p.build();
	}

	/** Uniquement la flamme (rendue a pleine luminosite). */
	private static LayerDefinition lunarianFlame() {
		Parts p = new Parts(none(), none(), none(), none(), none(), none());
		tail(p.body, false, true);
		return p.build();
	}

	// ------------------------------------------------------------------ nageoires

	private static LayerDefinition fishman() {
		CubeListBuilder head = CubeListBuilder.create()
				.texOffs(96, 0).addBox(-0.5F, -11.0F, -3.0F, 1.0F, 3.0F, 7.0F);          // crete
		CubeListBuilder rightArm = CubeListBuilder.create()
				.texOffs(96, 12).addBox(-5.0F, 3.0F, -0.5F, 2.0F, 6.0F, 1.0F);           // nageoire bras
		CubeListBuilder leftArm = CubeListBuilder.create()
				.texOffs(96, 12).addBox(3.0F, 3.0F, -0.5F, 2.0F, 6.0F, 1.0F);
		CubeListBuilder rightLeg = CubeListBuilder.create()
				.texOffs(96, 12).addBox(-4.0F, 4.0F, -0.5F, 2.0F, 6.0F, 1.0F);           // nageoire jambe
		CubeListBuilder leftLeg = CubeListBuilder.create()
				.texOffs(96, 12).addBox(2.0F, 4.0F, -0.5F, 2.0F, 6.0F, 1.0F);

		Parts p = new Parts(head, none(), rightArm, leftArm, rightLeg, leftLeg);
		// nageoire dorsale le long de la colonne
		p.body.addOrReplaceChild("dorsal",
				CubeListBuilder.create().texOffs(96, 20).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 8.0F, 3.0F),
				PartPose.offset(0.0F, 1.0F, 2.0F));
		return p.build();
	}
}
