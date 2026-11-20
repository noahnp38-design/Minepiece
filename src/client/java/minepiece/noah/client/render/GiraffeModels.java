package minepiece.noah.client.render;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Modèles 3D du Ushi Ushi no Mi. Coordonnées du modèle du joueur : Y vers le bas, pieds à y = 24, l'avant est du côté -Z.
 * Texture : textures/entity/giraffe.png (128x128, voir tools/make_giraffe_assets.py).
 *  - HYBRID : accessoires copiant la pose du joueur (cou, tête de girafe, cornes, taches sur le corps, bras et jambes, queue).
 *  - GIRAFFE : girafe complète (4 pattes, corps massif, cou, tête, queue) qui REMPLACE le corps du joueur.
 */
public final class GiraffeModels {
	public static final int TEX_W = 128;
	public static final int TEX_H = 128;

	/** Longueur du cou "unité" (pixels) : le cou réel est cette boîte mise à l'échelle en hauteur. */
	public static final float NECK_UNIT = 16.0F;

	public static final ModelLayerLocation HYBRID = loc("giraffe_hybrid");
	public static final ModelLayerLocation GIRAFFE = loc("giraffe_full");

	private GiraffeModels() {}

	private static ModelLayerLocation loc(String name) {
		return new ModelLayerLocation(MinePiece.id(name), "main");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(HYBRID, GiraffeModels::hybrid);
		ModelLayerRegistry.registerModelLayer(GIRAFFE, GiraffeModels::giraffe);
	}

	// ------------------------------------------------------------------ cou + tête (commun)

	/** Cou (boîte mise à l'échelle en hauteur) et tête de girafe (crâne, museau, cornes, oreilles) au bout du cou. */
	private static void neckAndHead(PartDefinition parent, float x, float y, float z) {
		PartDefinition pivot = parent.addOrReplaceChild("neck_pivot", CubeListBuilder.create(), PartPose.offset(x, y, z));
		PartDefinition neck = pivot.addOrReplaceChild("neck",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -NECK_UNIT, -2.0F, 4.0F, NECK_UNIT, 4.0F), PartPose.ZERO);
		// Crinière : fine bande sombre le long de l'arrière du cou (enfant du cou : elle suit son étirement).
		neck.addOrReplaceChild("mane",
				CubeListBuilder.create().texOffs(100, 14).addBox(-0.5F, -NECK_UNIT, 2.0F, 1.0F, NECK_UNIT, 1.0F), PartPose.ZERO);

		PartDefinition head = pivot.addOrReplaceChild("head_pivot", CubeListBuilder.create(), PartPose.offset(0.0F, -NECK_UNIT, 0.0F));
		head.addOrReplaceChild("skull",
				CubeListBuilder.create().texOffs(0, 22).addBox(-2.5F, -4.0F, -4.0F, 5.0F, 5.0F, 7.0F), PartPose.ZERO);
		head.addOrReplaceChild("snout",
				CubeListBuilder.create().texOffs(0, 36).addBox(-1.5F, -2.5F, -8.5F, 3.0F, 3.0F, 5.0F), PartPose.ZERO);
		// Deux petites cornes (ossicônes) avec leur pointe sombre, et deux oreilles.
		head.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(100, 10).addBox(-1.9F, -7.0F, -1.0F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(100, 10).addBox(0.9F, -7.0F, -1.0F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("knob_r", CubeListBuilder.create().texOffs(108, 10).addBox(-2.4F, -8.0F, -1.5F, 2.0F, 1.0F, 2.0F), PartPose.ZERO);
		head.addOrReplaceChild("knob_l", CubeListBuilder.create().texOffs(108, 10).addBox(0.4F, -8.0F, -1.5F, 2.0F, 1.0F, 2.0F), PartPose.ZERO);
		head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(100, 20).addBox(-5.0F, -3.0F, 0.5F, 2.5F, 1.0F, 1.5F), PartPose.rotation(0.0F, 0.0F, 0.25F));
		head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(100, 20).addBox(2.5F, -3.0F, 0.5F, 2.5F, 1.0F, 1.5F), PartPose.rotation(0.0F, 0.0F, -0.25F));
	}

	// ------------------------------------------------------------------ hybride

	private static LayerDefinition hybrid() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// "head" : le crâne du joueur est caché ; le cou et la tête de girafe sont ses enfants (ils suivent le regard).
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		neckAndHead(head, 0.0F, 0.0F, 0.0F);

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		body.addOrReplaceChild("spots", CubeListBuilder.create().texOffs(0, 90)
				.addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.6F)), PartPose.ZERO);
		PartDefinition tail = body.addOrReplaceChild("tail_pivot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 10.0F, 2.5F, 0.45F, 0.0F, 0.0F));
		tail.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(80, 64).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F), PartPose.ZERO);
		tail.addOrReplaceChild("tuft", CubeListBuilder.create().texOffs(86, 64).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);

		// Bras et jambes plus imposants : coques gonflées par-dessus les membres du joueur.
		CubeDeformation bigArm = new CubeDeformation(0.9F);
		CubeDeformation bigLeg = new CubeDeformation(0.8F);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F))
				.addOrReplaceChild("spots", CubeListBuilder.create().texOffs(26, 90).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, bigArm), PartPose.ZERO);
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F))
				.addOrReplaceChild("spots", CubeListBuilder.create().texOffs(26, 90).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, bigArm), PartPose.ZERO);
		PartDefinition rl = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		rl.addOrReplaceChild("spots", CubeListBuilder.create().texOffs(44, 90).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, bigLeg), PartPose.ZERO);
		rl.addOrReplaceChild("hoof", CubeListBuilder.create().texOffs(100, 0).addBox(-2.0F, 10.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(1.0F)), PartPose.ZERO);
		PartDefinition ll = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		ll.addOrReplaceChild("spots", CubeListBuilder.create().texOffs(44, 90).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, bigLeg), PartPose.ZERO);
		ll.addOrReplaceChild("hoof", CubeListBuilder.create().texOffs(100, 0).addBox(-2.0F, 10.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(1.0F)), PartPose.ZERO);

		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	// ------------------------------------------------------------------ girafe complète

	/** Hauteurs de référence (pixels) de la girafe : pattes de 14 px, corps de 7 px de haut, longueur 15 px. */
	public static final float LEG_LEN = 14.0F;
	public static final float BODY_Y = 6.5F;
	public static final float LEG_Y = 10.0F;

	private static LayerDefinition giraffe() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64)
				.addBox(-4.5F, -3.5F, -7.5F, 9.0F, 7.0F, 15.0F), PartPose.offset(0.0F, BODY_Y, 0.0F));
		// Quatre longues pattes + sabots.
		leg(root, "leg_fr", -2.9F, -5.5F);
		leg(root, "leg_fl", 2.9F, -5.5F);
		leg(root, "leg_br", -2.9F, 5.5F);
		leg(root, "leg_bl", 2.9F, 5.5F);

		// Queue.
		PartDefinition tail = root.addOrReplaceChild("tail_pivot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 4.0F, 7.5F, 0.5F, 0.0F, 0.0F));
		tail.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(80, 64).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F), PartPose.ZERO);
		tail.addOrReplaceChild("tuft", CubeListBuilder.create().texOffs(86, 64).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);

		// Cou vertical (à l'avant, sur le garrot) et tête.
		neckAndHead(root, 0.0F, 4.0F, -5.5F);
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	private static void leg(PartDefinition root, String name, float x, float z) {
		PartDefinition leg = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(50, 64)
				.addBox(-1.5F, 0.0F, -1.5F, 3.0F, LEG_LEN, 3.0F), PartPose.offset(x, LEG_Y, z));
		leg.addOrReplaceChild("hoof", CubeListBuilder.create().texOffs(100, 0)
				.addBox(-1.5F, LEG_LEN - 2.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.15F)), PartPose.ZERO);
	}
}
