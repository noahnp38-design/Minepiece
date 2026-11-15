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
 * La texture (textures/entity/race_extras.png) fait 128x64.
 */
public final class RaceModels {
	public static final int TEX_W = 128;
	public static final int TEX_H = 64;

	public static final float WING_TILT_LEFT = -0.35F;
	public static final float WING_TILT_RIGHT = 0.35F;
	private static final float WING_SWEEP = 0.5F;

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

	private static LayerDefinition skypiean() {
		Parts p = new Parts(none(), none(), none(), none(), none(), none());
		wings(p.body, 0);
		return p.build();
	}

	// ------------------------------------------------------------------ queue + flamme

	private static void tail(PartDefinition body, boolean withTail, boolean withFlame) {
		PartDefinition t1 = body.addOrReplaceChild("tail1",
				withTail ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F) : none(),
				PartPose.offsetAndRotation(0.0F, 10.5F, 2.0F, 0.45F, 0.0F, 0.0F));
		PartDefinition t2 = t1.addOrReplaceChild("tail2",
				withTail ? CubeListBuilder.create().texOffs(64, 8).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F) : none(),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.35F, 0.0F, 0.0F));
		PartDefinition t3 = t2.addOrReplaceChild("tail3",
				withTail ? CubeListBuilder.create().texOffs(64, 16).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F) : none(),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.35F, 0.0F, 0.0F));
		// La flamme compense la courbure cumulee de la queue (0.45 + 0.35 + 0.35) pour pointer vers le haut.
		t3.addOrReplaceChild("flame",
				withFlame
						? CubeListBuilder.create()
								.texOffs(64, 24).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F)
								.texOffs(64, 30).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 3.0F, 2.0F)
								.texOffs(64, 36).addBox(-0.5F, -9.0F, -0.5F, 1.0F, 3.0F, 1.0F)
						: none(),
				PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, -1.15F, 0.0F, 0.0F));
	}

	/** Ailes noires + queue (sans flamme). */
	private static LayerDefinition lunarian() {
		Parts p = new Parts(none(), none(), none(), none(), none(), none());
		wings(p.body, 32);
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
