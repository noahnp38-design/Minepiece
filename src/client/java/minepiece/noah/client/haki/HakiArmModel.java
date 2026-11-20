package minepiece.noah.client.haki;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Coque des membres du joueur pour le Haki : mêmes cubes et mêmes coordonnées de texture que le modèle du joueur
 * (skin 64x64), légèrement gonflés pour passer par-dessus la peau et les manches. Les parties portent les noms du
 * modèle du joueur : leur pose est copiée à chaque image (elles suivent parfaitement les mouvements).
 * Variantes : bras larges / fins (skins "slim"), et 3 épaisseurs : Koka (0,28 px), arcs du Haki des Rois (0,7 px),
 * yeux (tête, 0,52 px).
 * setupAnim(masque) : 1 = bras, 2 = jambes, 4 = tête, 8 = torse.
 */
public class HakiArmModel extends Model<Integer> {
	public static final ModelLayerLocation KOKA_WIDE = new ModelLayerLocation(MinePiece.id("haki_koka_wide"), "main");
	public static final ModelLayerLocation KOKA_SLIM = new ModelLayerLocation(MinePiece.id("haki_koka_slim"), "main");
	public static final ModelLayerLocation ARCS_WIDE = new ModelLayerLocation(MinePiece.id("haki_arcs_wide"), "main");
	public static final ModelLayerLocation ARCS_SLIM = new ModelLayerLocation(MinePiece.id("haki_arcs_slim"), "main");
	public static final ModelLayerLocation EYES = new ModelLayerLocation(MinePiece.id("haki_eyes"), "main");

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public HakiArmModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	public ModelPart rightArm() {
		return rightArm;
	}

	public ModelPart leftArm() {
		return leftArm;
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(KOKA_WIDE, () -> create(false, 0.28F, false, true));
		ModelLayerRegistry.registerModelLayer(KOKA_SLIM, () -> create(true, 0.28F, false, true));
		ModelLayerRegistry.registerModelLayer(ARCS_WIDE, () -> create(false, 0.7F, false, false));
		ModelLayerRegistry.registerModelLayer(ARCS_SLIM, () -> create(true, 0.7F, false, false));
		ModelLayerRegistry.registerModelLayer(EYES, () -> create(false, 0.62F, true, false));
	}

	private static LayerDefinition create(boolean slim, float g, boolean head, boolean wholeBody) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation d = new CubeDeformation(g);
		// Koka : la tête passe par-dessus les cheveux / chapeaux du skin (0,56), le torse par-dessus la veste (0,3)
		CubeDeformation dh = new CubeDeformation(head ? g : 0.56F);
		root.addOrReplaceChild("head", head || wholeBody ? CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, dh)
				: CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", wholeBody ? CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F,
				new CubeDeformation(0.3F)) : CubeListBuilder.create(), PartPose.ZERO);
		if (head) {
			root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
			return LayerDefinition.create(mesh, 64, 64);
		}
		if (slim) {
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, d),
					PartPose.offset(-5.0F, 2.5F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, d),
					PartPose.offset(5.0F, 2.5F, 0.0F));
		} else {
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, d),
					PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, d),
					PartPose.offset(5.0F, 2.0F, 0.0F));
		}
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, d),
				PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, d),
				PartPose.offset(1.9F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(Integer mask) {
		int m = mask == null ? 1 : mask;
		this.rightArm.visible = (m & 1) != 0;
		this.leftArm.visible = (m & 1) != 0;
		this.rightLeg.visible = (m & 2) != 0;
		this.leftLeg.visible = (m & 2) != 0;
		this.head.visible = (m & 4) != 0;
		this.body.visible = (m & 8) != 0;
	}
}
