package minepiece.noah.client.render;

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
 * Pheasant Beak : gangue de glace autour du bras droit du joueur. Les parties portent les noms du modèle du joueur
 * (right_arm...) pour en copier la pose. La glace gagne la main, puis l'avant-bras, puis le haut du bras (progression 0 à 1),
 * et des cristaux / griffes de glace apparaissent.
 */
public class IceArmModel extends Model<Float> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MinePiece.id("ice_arm"), "main");

	private final ModelPart hand;
	private final ModelPart claws;
	private final ModelPart fore;
	private final ModelPart upper;
	private final ModelPart crystals;

	private final ModelPart arm;

	public IceArmModel(ModelPart root) {
		super(root, id -> RenderTypes.entityTranslucent(id));
		ModelPart arm = root.getChild("right_arm");
		this.arm = arm;
		this.hand = arm.getChild("ice_hand");
		this.claws = arm.getChild("ice_claws");
		this.fore = arm.getChild("ice_fore");
		this.upper = arm.getChild("ice_upper");
		this.crystals = arm.getChild("ice_crystals");
	}

	/** Partie "bras droit" (avec toute la glace en enfants). */
	public ModelPart rightArm() {
		return arm;
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, IceArmModel::create);
	}

	private static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		PartDefinition arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));

		// Coordonnées du bras du joueur : de y = -2 (épaule) à y = 10 (main), x de -3 à 1, z de -2 à 2.
		arm.addOrReplaceChild("ice_hand", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3.0F, 6.5F, -2.0F, 4.0F, 4.5F, 4.0F, new CubeDeformation(0.9F)), PartPose.ZERO);
		arm.addOrReplaceChild("ice_fore", CubeListBuilder.create().texOffs(16, 0)
				.addBox(-3.0F, 2.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.7F)), PartPose.ZERO);
		arm.addOrReplaceChild("ice_upper", CubeListBuilder.create().texOffs(32, 0)
				.addBox(-3.0F, -2.0F, -2.0F, 4.0F, 4.5F, 4.0F, new CubeDeformation(0.55F)), PartPose.ZERO);

		// Griffes de glace au bout de la main.
		PartDefinition claws = arm.addOrReplaceChild("ice_claws", CubeListBuilder.create(), PartPose.offset(-1.0F, 11.5F, 0.0F));
		float[][] c = {{-1.8F, -1.6F, 0.25F}, {-0.5F, -1.8F, 0.0F}, {0.8F, -1.6F, -0.25F}, {-0.5F, 1.4F, 0.0F}};
		for (int i = 0; i < c.length; i++) {
			claws.addOrReplaceChild("claw" + i, CubeListBuilder.create().texOffs(48, 0)
					.addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.5F, 1.0F), PartPose.offsetAndRotation(c[i][0], 0.0F, c[i][1], 0.25F, 0.0F, c[i][2]));
		}

		// Cristaux qui pointent hors du bras.
		PartDefinition crystals = arm.addOrReplaceChild("ice_crystals", CubeListBuilder.create(), PartPose.ZERO);
		float[][] k = {
				{-3.8F, 8.0F, 0.0F, 0.0F, 0.0F, 0.9F, 4.0F},
				{-3.6F, 4.0F, -1.0F, -0.5F, 0.0F, 1.1F, 3.0F},
				{1.6F, 6.0F, 0.5F, 0.3F, 0.0F, -0.9F, 3.5F},
				{-1.0F, 3.0F, -2.8F, -1.0F, 0.0F, 0.2F, 3.0F},
				{-1.0F, 7.5F, 2.8F, 1.1F, 0.0F, -0.2F, 3.5F},
				{-3.5F, -0.5F, 0.5F, 0.2F, 0.0F, 1.2F, 2.5F}};
		for (int i = 0; i < k.length; i++) {
			crystals.addOrReplaceChild("crystal" + i, CubeListBuilder.create().texOffs(48, 8)
					.addBox(-0.75F, -k[i][6], -0.75F, 1.5F, k[i][6], 1.5F),
					PartPose.offsetAndRotation(k[i][0], k[i][1], k[i][2], k[i][3], k[i][4], k[i][5]));
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(Float progress) {
		float p = progress == null ? 0.0F : progress;
		this.hand.visible = p > 0.05F;
		this.claws.visible = p > 0.35F;
		this.fore.visible = p > 0.40F;
		this.crystals.visible = p > 0.60F;
		this.upper.visible = p > 0.80F;
		// Les cristaux "poussent".
		float g = Math.max(0.0F, Math.min(1.0F, (p - 0.6F) / 0.4F));
		this.crystals.xScale = g;
		this.crystals.yScale = g;
		this.crystals.zScale = g;
	}
}
