package minepiece.noah.client.ishi;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Géométrie des modèles du Ishi Ishi no Mi. FICHIER GÉNÉRÉ par tools/make_ishi.py (même source que les textures et les
 * .bbmodel) : chaque plaque de pierre est une sous-partie centrée sur elle-même, animable séparément (apparition,
 * effondrement). PIECES : chemin de la pièce (parties séparées par "/") et ordre d'apparition (0..1).
 */
public final class IshiModels {
	public static final ModelLayerLocation TITAN = new ModelLayerLocation(MinePiece.id("ishi_titan"), "main");
	public static final ModelLayerLocation STONE_ARM = new ModelLayerLocation(MinePiece.id("ishi_stone_arm"), "main");
	/** Hauteur du titan dans l'unité du modèle (pieds en y = 0, sommet du crâne). */
	public static final float TITAN_HEIGHT_UNITS = 105.0F;

	private IshiModels() {}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(TITAN, IshiModels::titan);
		ModelLayerRegistry.registerModelLayer(STONE_ARM, IshiModels::stoneArm);
	}

	public static LayerDefinition titan() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -44F, 0F, 0F, 0F, 0F));
		p_hips.addOrReplaceChild("hips_pelvis", CubeListBuilder.create().texOffs(0, 75).addBox(-13F, -5F, -8F, 26F, 10F, 16F), PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0F, 0F));
		p_hips.addOrReplaceChild("hips_belt", CubeListBuilder.create().texOffs(228, 175).addBox(-12F, -2.5F, -1F, 24F, 5F, 2F), PartPose.offsetAndRotation(0F, -1.5F, -8.5F, 0F, 0F, 0F));
		p_hips.addOrReplaceChild("hips_hip_l", CubeListBuilder.create().texOffs(65, 127).addBox(-2F, -3.5F, -6F, 4F, 7F, 12F), PartPose.offsetAndRotation(-12.5F, 0.5F, 0F, 0F, 0F, 0F));
		p_hips.addOrReplaceChild("hips_hip_r", CubeListBuilder.create().texOffs(98, 127).addBox(-2F, -3.5F, -6F, 4F, 7F, 12F), PartPose.offsetAndRotation(12.5F, 0.5F, 0F, 0F, 0F, 0F));
		PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -48F, 0F, 0.12F, 0F, 0F));
		p_body.addOrReplaceChild("body_abdomen", CubeListBuilder.create().texOffs(283, 40).addBox(-12F, -7F, -8F, 24F, 14F, 16F), PartPose.offsetAndRotation(0F, -6F, 0F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_abs0", CubeListBuilder.create().texOffs(409, 175).addBox(-5F, -2F, -1F, 10F, 4F, 2F), PartPose.offsetAndRotation(-5.448F, -10.5F, -8.711F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_abs1", CubeListBuilder.create().texOffs(434, 175).addBox(-5F, -2F, -1F, 10F, 4F, 2F), PartPose.offsetAndRotation(6.176F, -10.5F, -8.656F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_abs2", CubeListBuilder.create().texOffs(459, 175).addBox(-5F, -2F, -1F, 10F, 4F, 2F), PartPose.offsetAndRotation(-5.314F, -6.3F, -9.016F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_abs3", CubeListBuilder.create().texOffs(484, 175).addBox(-5F, -2F, -1F, 10F, 4F, 2F), PartPose.offsetAndRotation(6.279F, -6.3F, -8.992F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_abs4", CubeListBuilder.create().texOffs(0, 185).addBox(-5F, -2F, -1F, 10F, 4F, 2F), PartPose.offsetAndRotation(-5.791F, -2.1F, -8.917F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_abs5", CubeListBuilder.create().texOffs(25, 185).addBox(-5F, -2F, -1F, 10F, 4F, 2F), PartPose.offsetAndRotation(5.845F, -2.1F, -8.618F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_oblique_r", CubeListBuilder.create().texOffs(381, 75).addBox(-1.5F, -6F, -6F, 3F, 12F, 12F), PartPose.offsetAndRotation(-12F, -6F, 0F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_oblique_l", CubeListBuilder.create().texOffs(412, 75).addBox(-1.5F, -6F, -6F, 3F, 12F, 12F), PartPose.offsetAndRotation(12F, -6F, 0F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_chest", CubeListBuilder.create().texOffs(0, 0).addBox(-17F, -9.5F, -10F, 34F, 19F, 20F), PartPose.offsetAndRotation(0F, -21.5F, 0F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_pec_r", CubeListBuilder.create().texOffs(463, 127).addBox(-8F, -5.5F, -1.5F, 16F, 11F, 3F), PartPose.offsetAndRotation(-8.5F, -24.5F, -11F, 0.06F, 0.08F, 0F));
		p_body.addOrReplaceChild("body_pec_l", CubeListBuilder.create().texOffs(0, 148).addBox(-8F, -5.5F, -1.5F, 16F, 11F, 3F), PartPose.offsetAndRotation(8.5F, -24.5F, -11F, 0.06F, -0.08F, 0F));
		p_body.addOrReplaceChild("body_pec_edge_r", CubeListBuilder.create().texOffs(125, 185).addBox(-7F, -1.5F, -1F, 14F, 3F, 2F), PartPose.offsetAndRotation(-8F, -18.5F, -10.6F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_pec_edge_l", CubeListBuilder.create().texOffs(158, 185).addBox(-7F, -1.5F, -1F, 14F, 3F, 2F), PartPose.offsetAndRotation(8F, -18.5F, -10.6F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_back", CubeListBuilder.create().texOffs(334, 102).addBox(-16F, -8F, -2F, 32F, 16F, 4F), PartPose.offsetAndRotation(0F, -22F, 11F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_trapeze", CubeListBuilder.create().texOffs(131, 127).addBox(-11F, -2.5F, -7F, 22F, 5F, 14F), PartPose.offsetAndRotation(0F, -32.5F, 1F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_collar_r", CubeListBuilder.create().texOffs(281, 175).addBox(-5F, -1.5F, -2F, 10F, 3F, 4F), PartPose.offsetAndRotation(-9F, -31.5F, -7F, 0F, 0F, 0.15F));
		p_body.addOrReplaceChild("body_collar_l", CubeListBuilder.create().texOffs(310, 175).addBox(-5F, -1.5F, -2F, 10F, 3F, 4F), PartPose.offsetAndRotation(9F, -31.5F, -7F, 0F, 0F, -0.15F));
		p_body.addOrReplaceChild("body_ridge0", CubeListBuilder.create().texOffs(201, 148).addBox(-2.5F, -3.5F, -2.5F, 5F, 7F, 5F), PartPose.offsetAndRotation(-6.5F, -33.5F, 11.5F, 0.45F, 0F, 0.35F));
		p_body.addOrReplaceChild("body_ridge1", CubeListBuilder.create().texOffs(222, 148).addBox(-2.5F, -3.5F, -2.5F, 5F, 7F, 5F), PartPose.offsetAndRotation(1.5F, -31.5F, 11.5F, 0.45F, 0F, 0.05F));
		p_body.addOrReplaceChild("body_ridge2", CubeListBuilder.create().texOffs(243, 148).addBox(-2.5F, -3.5F, -2.5F, 5F, 7F, 5F), PartPose.offsetAndRotation(8.5F, -33.5F, 11.5F, 0.45F, 0F, -0.3F));
		p_body.addOrReplaceChild("body_lat_r", CubeListBuilder.create().texOffs(364, 40).addBox(-1.5F, -7F, -7.5F, 3F, 14F, 15F), PartPose.offsetAndRotation(-16.5F, -21F, 0.5F, 0F, 0F, 0F));
		p_body.addOrReplaceChild("body_lat_l", CubeListBuilder.create().texOffs(401, 40).addBox(-1.5F, -7F, -7.5F, 3F, 14F, 15F), PartPose.offsetAndRotation(16.5F, -21F, 0.5F, 0F, 0F, 0F));
		PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -34F, -2F, -0.1F, 0F, 0F));
		p_head.addOrReplaceChild("head_neck", CubeListBuilder.create().texOffs(302, 127).addBox(-5F, -2.5F, -5F, 10F, 5F, 10F), PartPose.offsetAndRotation(0F, -1.5F, 0F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_skull", CubeListBuilder.create().texOffs(220, 40).addBox(-8F, -8F, -7.5F, 16F, 16F, 15F), PartPose.offsetAndRotation(0F, -11F, -0.5F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_brow", CubeListBuilder.create().texOffs(50, 185).addBox(-8.5F, -1.5F, -1.5F, 17F, 3F, 3F), PartPose.offsetAndRotation(0F, -12.5F, -8.1F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_nose", CubeListBuilder.create().texOffs(63, 175).addBox(-1.5F, -2.5F, -1.5F, 3F, 5F, 3F), PartPose.offsetAndRotation(0F, -9.5F, -9.3F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_cheek_r", CubeListBuilder.create().texOffs(91, 185).addBox(-2.5F, -1.5F, -1.5F, 5F, 3F, 3F), PartPose.offsetAndRotation(-5.5F, -8.5F, -7.9F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_cheek_l", CubeListBuilder.create().texOffs(108, 185).addBox(-2.5F, -1.5F, -1.5F, 5F, 3F, 3F), PartPose.offsetAndRotation(5.5F, -8.5F, -7.9F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_jaw", CubeListBuilder.create().texOffs(467, 163).addBox(-6.5F, -2F, -2.5F, 13F, 4F, 5F), PartPose.offsetAndRotation(0F, -4F, -6.5F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_eye_r", CubeListBuilder.create().texOffs(295, 185).addBox(-1.5F, -0.5F, -0.5F, 3F, 1F, 1F), PartPose.offsetAndRotation(-4F, -11.3F, -8.6F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_eye_l", CubeListBuilder.create().texOffs(304, 185).addBox(-1.5F, -0.5F, -0.5F, 3F, 1F, 1F), PartPose.offsetAndRotation(4F, -11.3F, -8.6F, 0F, 0F, 0F));
		p_head.addOrReplaceChild("head_crown_a", CubeListBuilder.create().texOffs(352, 148).addBox(-3F, -2.5F, -3F, 6F, 5F, 6F), PartPose.offsetAndRotation(-3F, -20.5F, -1F, 0F, 0F, 0.3F));
		p_head.addOrReplaceChild("head_crown_b", CubeListBuilder.create().texOffs(0, 175).addBox(-2.5F, -2F, -2.5F, 5F, 4F, 5F), PartPose.offsetAndRotation(3.5F, -20F, 0.5F, 0F, 0F, -0.25F));
		PartDefinition p_right_arm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offsetAndRotation(-20F, -28F, 0F, 0.05F, 0F, 0.24F));
		p_right_arm.addOrReplaceChild("right_arm_shoulder", CubeListBuilder.create().texOffs(235, 0).addBox(-9F, -7.5F, -10F, 18F, 15F, 20F), PartPose.offsetAndRotation(-1F, -0.5F, 0F, 0F, 0F, 0F));
		p_right_arm.addOrReplaceChild("right_arm_shoulder_top", CubeListBuilder.create().texOffs(85, 75).addBox(-9.5F, -2F, -11F, 19F, 4F, 22F), PartPose.offsetAndRotation(-1.5F, -8F, 0F, 0F, 0F, 0.12F));
		p_right_arm.addOrReplaceChild("right_arm_shoulder_spike", CubeListBuilder.create().texOffs(264, 148).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.offsetAndRotation(-6F, -11F, 0F, 0F, 0F, 0.35F));
		p_right_arm.addOrReplaceChild("right_arm_upper", CubeListBuilder.create().texOffs(0, 40).addBox(-6.5F, -10F, -7F, 13F, 20F, 14F), PartPose.offsetAndRotation(-0.5F, 14F, 0F, 0F, 0F, 0F));
		p_right_arm.addOrReplaceChild("right_arm_biceps", CubeListBuilder.create().texOffs(343, 127).addBox(-5.5F, -6F, -1.5F, 11F, 12F, 3F), PartPose.offsetAndRotation(-0.5F, 12F, -7F, 0F, 0F, 0F));
		p_right_arm.addOrReplaceChild("right_arm_triceps", CubeListBuilder.create().texOffs(39, 148).addBox(-5F, -6F, -1F, 10F, 12F, 2F), PartPose.offsetAndRotation(-1F, 13F, 7F, 0F, 0F, 0F));
		p_right_arm.addOrReplaceChild("right_arm_elbow", CubeListBuilder.create().texOffs(204, 127).addBox(-6F, -2.5F, -6F, 12F, 5F, 12F), PartPose.offsetAndRotation(0F, 24.5F, 0F, 0F, 0F, 0F));
		PartDefinition p_right_forearm = p_right_arm.addOrReplaceChild("right_forearm", CubeListBuilder.create(), PartPose.offsetAndRotation(0.5F, 25F, 0F, -0.42F, 0F, -0.06F));
		p_right_forearm.addOrReplaceChild("right_forearm_core", CubeListBuilder.create().texOffs(312, 0).addBox(-7.5F, -10F, -7.5F, 15F, 20F, 15F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
		p_right_forearm.addOrReplaceChild("right_forearm_plate_f", CubeListBuilder.create().texOffs(113, 163).addBox(-7F, -4F, -1F, 14F, 8F, 2F), PartPose.offsetAndRotation(0F, 6F, -8F, 0F, 0F, 0F));
		p_right_forearm.addOrReplaceChild("right_forearm_plate_b", CubeListBuilder.create().texOffs(377, 148).addBox(-6F, -4.5F, -1F, 12F, 9F, 2F), PartPose.offsetAndRotation(0F, 14.5F, 8F, 0F, 0F, 0F));
		p_right_forearm.addOrReplaceChild("right_forearm_plate_o", CubeListBuilder.create().texOffs(284, 102).addBox(-1F, -6F, -5F, 2F, 12F, 10F), PartPose.offsetAndRotation(-8F, 10F, 0F, 0F, 0F, 0F));
		p_right_forearm.addOrReplaceChild("right_forearm_cuff", CubeListBuilder.create().texOffs(407, 102).addBox(-8F, -2F, -8F, 16F, 4F, 16F), PartPose.offsetAndRotation(0F, 18F, 0F, 0F, 0F, 0F));
		PartDefinition p_right_hand = p_right_forearm.addOrReplaceChild("right_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 20F, 0F, 0F, 0F, 0F));
		p_right_hand.addOrReplaceChild("right_hand_palm", CubeListBuilder.create().texOffs(168, 75).addBox(-8.5F, -5.5F, -7.5F, 17F, 11F, 15F), PartPose.offsetAndRotation(0F, 5.5F, 0F, 0F, 0F, 0F));
		p_right_hand.addOrReplaceChild("right_hand_back", CubeListBuilder.create().texOffs(146, 163).addBox(-7.5F, -4F, -1F, 15F, 8F, 2F), PartPose.offsetAndRotation(0F, 5F, 7.5F, 0F, 0F, 0F));
		PartDefinition p_right_finger0 = p_right_hand.addOrReplaceChild("right_finger0", CubeListBuilder.create(), PartPose.offsetAndRotation(6.4F, 9F, -4F, 0F, 0F, 0F));
		p_right_finger0.addOrReplaceChild("right_finger0_base", CubeListBuilder.create().texOffs(406, 148).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_right_finger0.addOrReplaceChild("right_finger0_mid", CubeListBuilder.create().texOffs(181, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_right_finger0.addOrReplaceChild("right_finger0_tip", CubeListBuilder.create().texOffs(76, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_right_finger0.addOrReplaceChild("right_finger0_knuckle", CubeListBuilder.create().texOffs(191, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(-0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_right_finger1 = p_right_hand.addOrReplaceChild("right_finger1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.1F, 9F, -4F, 0F, 0F, 0F));
		p_right_finger1.addOrReplaceChild("right_finger1_base", CubeListBuilder.create().texOffs(427, 148).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_right_finger1.addOrReplaceChild("right_finger1_mid", CubeListBuilder.create().texOffs(200, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_right_finger1.addOrReplaceChild("right_finger1_tip", CubeListBuilder.create().texOffs(95, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_right_finger1.addOrReplaceChild("right_finger1_knuckle", CubeListBuilder.create().texOffs(204, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(-0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_right_finger2 = p_right_hand.addOrReplaceChild("right_finger2", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.2F, 9F, -4F, 0F, 0F, 0F));
		p_right_finger2.addOrReplaceChild("right_finger2_base", CubeListBuilder.create().texOffs(448, 148).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_right_finger2.addOrReplaceChild("right_finger2_mid", CubeListBuilder.create().texOffs(219, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_right_finger2.addOrReplaceChild("right_finger2_tip", CubeListBuilder.create().texOffs(114, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_right_finger2.addOrReplaceChild("right_finger2_knuckle", CubeListBuilder.create().texOffs(217, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(-0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_right_finger3 = p_right_hand.addOrReplaceChild("right_finger3", CubeListBuilder.create(), PartPose.offsetAndRotation(-6.5F, 9F, -4F, 0F, 0F, 0F));
		p_right_finger3.addOrReplaceChild("right_finger3_base", CubeListBuilder.create().texOffs(469, 148).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_right_finger3.addOrReplaceChild("right_finger3_mid", CubeListBuilder.create().texOffs(238, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_right_finger3.addOrReplaceChild("right_finger3_tip", CubeListBuilder.create().texOffs(133, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_right_finger3.addOrReplaceChild("right_finger3_knuckle", CubeListBuilder.create().texOffs(230, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(-0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_right_thumb = p_right_hand.addOrReplaceChild("right_thumb", CubeListBuilder.create(), PartPose.offsetAndRotation(8.5F, 5F, -4F, 0F, 0F, 0.35F));
		p_right_thumb.addOrReplaceChild("right_thumb_base", CubeListBuilder.create().texOffs(289, 148).addBox(-2F, -3.5F, -2.5F, 4F, 7F, 5F), PartPose.offsetAndRotation(0F, 3.5F, 0F, 0F, 0F, 0F));
		p_right_thumb.addOrReplaceChild("right_thumb_tip", CubeListBuilder.create().texOffs(21, 175).addBox(-2F, -1.5F, -3F, 4F, 3F, 6F), PartPose.offsetAndRotation(0.5F, 7.5F, -3F, 0F, 0F, 0F));
		PartDefinition p_left_arm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offsetAndRotation(20F, -28F, 0F, 0.05F, 0F, -0.24F));
		p_left_arm.addOrReplaceChild("left_arm_shoulder", CubeListBuilder.create().texOffs(373, 0).addBox(-9F, -7.5F, -10F, 18F, 15F, 20F), PartPose.offsetAndRotation(1F, -0.5F, 0F, 0F, 0F, 0F));
		p_left_arm.addOrReplaceChild("left_arm_shoulder_top", CubeListBuilder.create().texOffs(233, 75).addBox(-9.5F, -2F, -11F, 19F, 4F, 22F), PartPose.offsetAndRotation(1.5F, -8F, 0F, 0F, 0F, -0.12F));
		p_left_arm.addOrReplaceChild("left_arm_shoulder_spike", CubeListBuilder.create().texOffs(308, 148).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.offsetAndRotation(6F, -11F, 0F, 0F, 0F, -0.35F));
		p_left_arm.addOrReplaceChild("left_arm_upper", CubeListBuilder.create().texOffs(55, 40).addBox(-6.5F, -10F, -7F, 13F, 20F, 14F), PartPose.offsetAndRotation(0.5F, 14F, 0F, 0F, 0F, 0F));
		p_left_arm.addOrReplaceChild("left_arm_biceps", CubeListBuilder.create().texOffs(372, 127).addBox(-5.5F, -6F, -1.5F, 11F, 12F, 3F), PartPose.offsetAndRotation(0.5F, 12F, -7F, 0F, 0F, 0F));
		p_left_arm.addOrReplaceChild("left_arm_triceps", CubeListBuilder.create().texOffs(64, 148).addBox(-5F, -6F, -1F, 10F, 12F, 2F), PartPose.offsetAndRotation(1F, 13F, 7F, 0F, 0F, 0F));
		p_left_arm.addOrReplaceChild("left_arm_elbow", CubeListBuilder.create().texOffs(253, 127).addBox(-6F, -2.5F, -6F, 12F, 5F, 12F), PartPose.offsetAndRotation(0F, 24.5F, 0F, 0F, 0F, 0F));
		PartDefinition p_left_forearm = p_left_arm.addOrReplaceChild("left_forearm", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, 25F, 0F, -0.42F, 0F, 0.06F));
		p_left_forearm.addOrReplaceChild("left_forearm_core", CubeListBuilder.create().texOffs(450, 0).addBox(-7.5F, -10F, -7.5F, 15F, 20F, 15F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
		p_left_forearm.addOrReplaceChild("left_forearm_plate_f", CubeListBuilder.create().texOffs(257, 163).addBox(-7F, -4F, -1F, 14F, 8F, 2F), PartPose.offsetAndRotation(0F, 6F, -8F, 0F, 0F, 0F));
		p_left_forearm.addOrReplaceChild("left_forearm_plate_b", CubeListBuilder.create().texOffs(0, 163).addBox(-6F, -4.5F, -1F, 12F, 9F, 2F), PartPose.offsetAndRotation(0F, 14.5F, 8F, 0F, 0F, 0F));
		p_left_forearm.addOrReplaceChild("left_forearm_plate_o", CubeListBuilder.create().texOffs(309, 102).addBox(-1F, -6F, -5F, 2F, 12F, 10F), PartPose.offsetAndRotation(8F, 10F, 0F, 0F, 0F, 0F));
		p_left_forearm.addOrReplaceChild("left_forearm_cuff", CubeListBuilder.create().texOffs(0, 127).addBox(-8F, -2F, -8F, 16F, 4F, 16F), PartPose.offsetAndRotation(0F, 18F, 0F, 0F, 0F, 0F));
		PartDefinition p_left_hand = p_left_forearm.addOrReplaceChild("left_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 20F, 0F, 0F, 0F, 0F));
		p_left_hand.addOrReplaceChild("left_hand_palm", CubeListBuilder.create().texOffs(316, 75).addBox(-8.5F, -5.5F, -7.5F, 17F, 11F, 15F), PartPose.offsetAndRotation(0F, 5.5F, 0F, 0F, 0F, 0F));
		p_left_hand.addOrReplaceChild("left_hand_back", CubeListBuilder.create().texOffs(290, 163).addBox(-7.5F, -4F, -1F, 15F, 8F, 2F), PartPose.offsetAndRotation(0F, 5F, 7.5F, 0F, 0F, 0F));
		PartDefinition p_left_finger0 = p_left_hand.addOrReplaceChild("left_finger0", CubeListBuilder.create(), PartPose.offsetAndRotation(-6.4F, 9F, -4F, 0F, 0F, 0F));
		p_left_finger0.addOrReplaceChild("left_finger0_base", CubeListBuilder.create().texOffs(29, 163).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_left_finger0.addOrReplaceChild("left_finger0_mid", CubeListBuilder.create().texOffs(325, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_left_finger0.addOrReplaceChild("left_finger0_tip", CubeListBuilder.create().texOffs(152, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(-0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_left_finger0.addOrReplaceChild("left_finger0_knuckle", CubeListBuilder.create().texOffs(243, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_left_finger1 = p_left_hand.addOrReplaceChild("left_finger1", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.1F, 9F, -4F, 0F, 0F, 0F));
		p_left_finger1.addOrReplaceChild("left_finger1_base", CubeListBuilder.create().texOffs(50, 163).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_left_finger1.addOrReplaceChild("left_finger1_mid", CubeListBuilder.create().texOffs(344, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_left_finger1.addOrReplaceChild("left_finger1_tip", CubeListBuilder.create().texOffs(171, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(-0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_left_finger1.addOrReplaceChild("left_finger1_knuckle", CubeListBuilder.create().texOffs(256, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_left_finger2 = p_left_hand.addOrReplaceChild("left_finger2", CubeListBuilder.create(), PartPose.offsetAndRotation(2.2F, 9F, -4F, 0F, 0F, 0F));
		p_left_finger2.addOrReplaceChild("left_finger2_base", CubeListBuilder.create().texOffs(71, 163).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_left_finger2.addOrReplaceChild("left_finger2_mid", CubeListBuilder.create().texOffs(363, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_left_finger2.addOrReplaceChild("left_finger2_tip", CubeListBuilder.create().texOffs(190, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(-0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_left_finger2.addOrReplaceChild("left_finger2_knuckle", CubeListBuilder.create().texOffs(269, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_left_finger3 = p_left_hand.addOrReplaceChild("left_finger3", CubeListBuilder.create(), PartPose.offsetAndRotation(6.5F, 9F, -4F, 0F, 0F, 0F));
		p_left_finger3.addOrReplaceChild("left_finger3_base", CubeListBuilder.create().texOffs(92, 163).addBox(-2F, -2.5F, -3F, 4F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, -1.5F, 0F, 0F, 0F));
		p_left_finger3.addOrReplaceChild("left_finger3_mid", CubeListBuilder.create().texOffs(382, 163).addBox(-2F, -2.5F, -2.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 6.5F, -3F, 0F, 0F, 0F));
		p_left_finger3.addOrReplaceChild("left_finger3_tip", CubeListBuilder.create().texOffs(209, 175).addBox(-2F, -1.5F, -2.5F, 4F, 3F, 5F), PartPose.offsetAndRotation(-0.2F, 9.5F, 0F, 0F, 0F, 0F));
		p_left_finger3.addOrReplaceChild("left_finger3_knuckle", CubeListBuilder.create().texOffs(282, 185).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(0.2F, 0.2F, -4.2F, 0F, 0F, 0F));
		PartDefinition p_left_thumb = p_left_hand.addOrReplaceChild("left_thumb", CubeListBuilder.create(), PartPose.offsetAndRotation(-8.5F, 5F, -4F, 0F, 0F, -0.35F));
		p_left_thumb.addOrReplaceChild("left_thumb_base", CubeListBuilder.create().texOffs(333, 148).addBox(-2F, -3.5F, -2.5F, 4F, 7F, 5F), PartPose.offsetAndRotation(0F, 3.5F, 0F, 0F, 0F, 0F));
		p_left_thumb.addOrReplaceChild("left_thumb_tip", CubeListBuilder.create().texOffs(42, 175).addBox(-2F, -1.5F, -3F, 4F, 3F, 6F), PartPose.offsetAndRotation(-0.5F, 7.5F, -3F, 0F, 0F, 0F));
		PartDefinition p_right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offsetAndRotation(-9F, -44F, 0F, 0F, 0F, 0.07F));
		p_right_leg.addOrReplaceChild("right_leg_hip_plate", CubeListBuilder.create().texOffs(0, 102).addBox(-8.5F, -3F, -9F, 17F, 6F, 18F), PartPose.offsetAndRotation(0F, 1F, 0F, 0F, 0F, 0F));
		p_right_leg.addOrReplaceChild("right_leg_thigh", CubeListBuilder.create().texOffs(109, 0).addBox(-7.5F, -11F, -8F, 15F, 22F, 16F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		p_right_leg.addOrReplaceChild("right_leg_quad", CubeListBuilder.create().texOffs(401, 127).addBox(-6.5F, -6.5F, -1F, 13F, 13F, 2F), PartPose.offsetAndRotation(0F, 9.5F, -8.6F, 0F, 0F, 0F));
		p_right_leg.addOrReplaceChild("right_leg_knee", CubeListBuilder.create().texOffs(401, 163).addBox(-6F, -3F, -2F, 12F, 6F, 4F), PartPose.offsetAndRotation(0F, 22F, -7.5F, 0F, 0F, 0F));
		PartDefinition p_right_shin = p_right_leg.addOrReplaceChild("right_shin", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 22F, 0F, 0F, 0F, -0.07F));
		p_right_shin.addOrReplaceChild("right_shin_core", CubeListBuilder.create().texOffs(110, 40).addBox(-6.5F, -9F, -7F, 13F, 18F, 14F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
		p_right_shin.addOrReplaceChild("right_shin_plate", CubeListBuilder.create().texOffs(89, 148).addBox(-6F, -6F, -1F, 12F, 12F, 2F), PartPose.offsetAndRotation(0F, 8F, -7.8F, 0F, 0F, 0F));
		p_right_shin.addOrReplaceChild("right_shin_calf", CubeListBuilder.create().texOffs(147, 148).addBox(-5.5F, -5.5F, -1F, 11F, 11F, 2F), PartPose.offsetAndRotation(0F, 8.5F, 7.5F, 0F, 0F, 0F));
		p_right_shin.addOrReplaceChild("right_shin_foot", CubeListBuilder.create().texOffs(71, 102).addBox(-8F, -2.5F, -9.5F, 16F, 5F, 19F), PartPose.offsetAndRotation(0F, 19.5F, -1.5F, 0F, 0F, 0F));
		p_right_shin.addOrReplaceChild("right_shin_toes", CubeListBuilder.create().texOffs(339, 175).addBox(-7F, -2F, -1.5F, 14F, 4F, 3F), PartPose.offsetAndRotation(0F, 20F, -12F, 0F, 0F, 0F));
		PartDefinition p_left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offsetAndRotation(9F, -44F, 0F, 0F, 0F, -0.07F));
		p_left_leg.addOrReplaceChild("left_leg_hip_plate", CubeListBuilder.create().texOffs(142, 102).addBox(-8.5F, -3F, -9F, 17F, 6F, 18F), PartPose.offsetAndRotation(0F, 1F, 0F, 0F, 0F, 0F));
		p_left_leg.addOrReplaceChild("left_leg_thigh", CubeListBuilder.create().texOffs(172, 0).addBox(-7.5F, -11F, -8F, 15F, 22F, 16F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		p_left_leg.addOrReplaceChild("left_leg_quad", CubeListBuilder.create().texOffs(432, 127).addBox(-6.5F, -6.5F, -1F, 13F, 13F, 2F), PartPose.offsetAndRotation(0F, 9.5F, -8.6F, 0F, 0F, 0F));
		p_left_leg.addOrReplaceChild("left_leg_knee", CubeListBuilder.create().texOffs(434, 163).addBox(-6F, -3F, -2F, 12F, 6F, 4F), PartPose.offsetAndRotation(0F, 22F, -7.5F, 0F, 0F, 0F));
		PartDefinition p_left_shin = p_left_leg.addOrReplaceChild("left_shin", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 22F, 0F, 0F, 0F, 0.07F));
		p_left_shin.addOrReplaceChild("left_shin_core", CubeListBuilder.create().texOffs(165, 40).addBox(-6.5F, -9F, -7F, 13F, 18F, 14F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
		p_left_shin.addOrReplaceChild("left_shin_plate", CubeListBuilder.create().texOffs(118, 148).addBox(-6F, -6F, -1F, 12F, 12F, 2F), PartPose.offsetAndRotation(0F, 8F, -7.8F, 0F, 0F, 0F));
		p_left_shin.addOrReplaceChild("left_shin_calf", CubeListBuilder.create().texOffs(174, 148).addBox(-5.5F, -5.5F, -1F, 11F, 11F, 2F), PartPose.offsetAndRotation(0F, 8.5F, 7.5F, 0F, 0F, 0F));
		p_left_shin.addOrReplaceChild("left_shin_foot", CubeListBuilder.create().texOffs(213, 102).addBox(-8F, -2.5F, -9.5F, 16F, 5F, 19F), PartPose.offsetAndRotation(0F, 19.5F, -1.5F, 0F, 0F, 0F));
		p_left_shin.addOrReplaceChild("left_shin_toes", CubeListBuilder.create().texOffs(374, 175).addBox(-7F, -2F, -1.5F, 14F, 4F, 3F), PartPose.offsetAndRotation(0F, 20F, -12F, 0F, 0F, 0F));
		return LayerDefinition.create(mesh, 512, 512);
	}

	public static LayerDefinition stoneArm() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_arm = root.addOrReplaceChild("arm", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_core_low", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -3F, -2.5F, 5F, 6F, 5F), PartPose.offsetAndRotation(-0.75F, 7F, 0.25F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_core_high", CubeListBuilder.create().texOffs(21, 0).addBox(-2.5F, -3F, -2.5F, 5F, 6F, 5F), PartPose.offsetAndRotation(-0.75F, 0.9F, 0.25F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring0_f", CubeListBuilder.create().texOffs(9, 22).addBox(-2.5F, -2F, -0.5F, 5F, 4F, 1F), PartPose.offsetAndRotation(-0.93F, 8.6F, -2.5F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring0_b", CubeListBuilder.create().texOffs(22, 22).addBox(-2.5F, -2F, -0.5F, 5F, 4F, 1F), PartPose.offsetAndRotation(-1F, 8.9F, 2.5F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring0_o", CubeListBuilder.create().texOffs(69, 0).addBox(-0.5F, -2F, -2.5F, 1F, 4F, 5F), PartPose.offsetAndRotation(-3.5F, 8.4F, 0.2F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring0_i", CubeListBuilder.create().texOffs(25, 12).addBox(-0.5F, -2F, -2F, 1F, 4F, 4F), PartPose.offsetAndRotation(1.5F, 8.8F, -0.1F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring1_f", CubeListBuilder.create().texOffs(35, 22).addBox(-2.5F, -2F, -0.5F, 5F, 4F, 1F), PartPose.offsetAndRotation(-0.921F, 5.4F, -2.5F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring1_b", CubeListBuilder.create().texOffs(48, 22).addBox(-2.5F, -2F, -0.5F, 5F, 4F, 1F), PartPose.offsetAndRotation(-1F, 5.7F, 2.5F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring1_o", CubeListBuilder.create().texOffs(82, 0).addBox(-0.5F, -2F, -2.5F, 1F, 4F, 5F), PartPose.offsetAndRotation(-3.5F, 5.2F, 0.2F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring1_i", CubeListBuilder.create().texOffs(36, 12).addBox(-0.5F, -2F, -2F, 1F, 4F, 4F), PartPose.offsetAndRotation(1.5F, 5.6F, -0.1F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring2_f", CubeListBuilder.create().texOffs(61, 22).addBox(-2.5F, -2F, -0.5F, 5F, 4F, 1F), PartPose.offsetAndRotation(-0.943F, 2F, -2.5F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring2_b", CubeListBuilder.create().texOffs(74, 22).addBox(-2.5F, -2F, -0.5F, 5F, 4F, 1F), PartPose.offsetAndRotation(-1F, 2.3F, 2.5F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring2_o", CubeListBuilder.create().texOffs(95, 0).addBox(-0.5F, -2F, -2.5F, 1F, 4F, 5F), PartPose.offsetAndRotation(-3.5F, 1.8F, 0.2F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_ring2_i", CubeListBuilder.create().texOffs(47, 12).addBox(-0.5F, -2F, -2F, 1F, 4F, 4F), PartPose.offsetAndRotation(1.5F, 2.2F, -0.1F, 0F, 0F, 0F));
		p_arm.addOrReplaceChild("arm_shoulder", CubeListBuilder.create().texOffs(0, 12).addBox(-3F, -1.5F, -3F, 6F, 3F, 6F), PartPose.offsetAndRotation(-1.7F, -1.7F, 0F, 0F, 0F, 0.18F));
		p_arm.addOrReplaceChild("arm_shoulder_spike", CubeListBuilder.create().texOffs(26, 29).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(-3.6F, -3.6F, 0F, 0F, 0F, 0.6F));
		p_arm.addOrReplaceChild("arm_elbow", CubeListBuilder.create().texOffs(35, 29).addBox(-2F, -1F, -1F, 4F, 2F, 2F), PartPose.offsetAndRotation(-1.4F, 3.6F, 2.6F, 0F, 0F, 0F));
		PartDefinition p_fist = p_arm.addOrReplaceChild("fist", CubeListBuilder.create(), PartPose.offsetAndRotation(-1F, 10.5F, 0F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_wrist", CubeListBuilder.create().texOffs(58, 12).addBox(-3F, -1F, -3F, 6F, 2F, 6F), PartPose.offsetAndRotation(-0.2F, -0.5F, -0.2F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_palm", CubeListBuilder.create().texOffs(42, 0).addBox(-3.5F, -2.5F, -3F, 7F, 5F, 6F), PartPose.offsetAndRotation(0F, 2.5F, 0F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_back", CubeListBuilder.create().texOffs(87, 22).addBox(-3F, -2F, -0.5F, 6F, 4F, 1F), PartPose.offsetAndRotation(-0.2F, 2.5F, 3.1F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_finger0", CubeListBuilder.create().texOffs(83, 12).addBox(-1F, -1.5F, -1.5F, 2F, 3F, 3F), PartPose.offsetAndRotation(-2.6F, 5.7F, -2.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_fold0", CubeListBuilder.create().texOffs(102, 22).addBox(-1F, -0.5F, -2F, 2F, 1F, 4F), PartPose.offsetAndRotation(-2.5F, 7.1F, -1.2F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_knuckle0", CubeListBuilder.create().texOffs(57, 29).addBox(-1F, -0.5F, -1F, 2F, 1F, 2F), PartPose.offsetAndRotation(-2.7F, 3.9F, -3.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_finger1", CubeListBuilder.create().texOffs(94, 12).addBox(-1F, -1.5F, -1.5F, 2F, 3F, 3F), PartPose.offsetAndRotation(-0.8F, 5.7F, -2.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_fold1", CubeListBuilder.create().texOffs(115, 22).addBox(-1F, -0.5F, -2F, 2F, 1F, 4F), PartPose.offsetAndRotation(-0.7F, 7.1F, -1.2F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_knuckle1", CubeListBuilder.create().texOffs(66, 29).addBox(-1F, -0.5F, -1F, 2F, 1F, 2F), PartPose.offsetAndRotation(-0.9F, 3.9F, -3.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_finger2", CubeListBuilder.create().texOffs(105, 12).addBox(-1F, -1.5F, -1.5F, 2F, 3F, 3F), PartPose.offsetAndRotation(1F, 5.7F, -2.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_fold2", CubeListBuilder.create().texOffs(0, 29).addBox(-1F, -0.5F, -2F, 2F, 1F, 4F), PartPose.offsetAndRotation(1.1F, 7.1F, -1.2F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_knuckle2", CubeListBuilder.create().texOffs(75, 29).addBox(-1F, -0.5F, -1F, 2F, 1F, 2F), PartPose.offsetAndRotation(0.9F, 3.9F, -3.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_finger3", CubeListBuilder.create().texOffs(116, 12).addBox(-1F, -1.5F, -1.5F, 2F, 3F, 3F), PartPose.offsetAndRotation(2.8F, 5.7F, -2.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_fold3", CubeListBuilder.create().texOffs(13, 29).addBox(-1F, -0.5F, -2F, 2F, 1F, 4F), PartPose.offsetAndRotation(2.9F, 7.1F, -1.2F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_knuckle3", CubeListBuilder.create().texOffs(84, 29).addBox(-1F, -0.5F, -1F, 2F, 1F, 2F), PartPose.offsetAndRotation(2.7F, 3.9F, -3.9F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_thumb", CubeListBuilder.create().texOffs(0, 22).addBox(-1F, -2F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-3.6F, 3.8F, -2.4F, 0F, 0F, -0.3F));
		p_fist.addOrReplaceChild("fist_thumb_tip", CubeListBuilder.create().texOffs(48, 29).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(-3F, 5.6F, -3.6F, 0F, 0F, 0F));
		p_fist.addOrReplaceChild("fist_plate", CubeListBuilder.create().texOffs(93, 29).addBox(-3F, -1F, -0.5F, 6F, 2F, 1F), PartPose.offsetAndRotation(0F, 0.4F, -3.3F, 0F, 0F, 0F));
		return LayerDefinition.create(mesh, 128, 64);
	}

	public static final String[][] TITAN_PIECES = {
			{"hips/hips_pelvis", "0.243"},
			{"hips/hips_belt", "0.278"},
			{"hips/hips_hip_l", "0.286"},
			{"hips/hips_hip_r", "0.263"},
			{"body/body_abdomen", "0.325"},
			{"body/body_abs0", "0.390"},
			{"body/body_abs1", "0.347"},
			{"body/body_abs2", "0.419"},
			{"body/body_abs3", "0.353"},
			{"body/body_abs4", "0.355"},
			{"body/body_abs5", "0.375"},
			{"body/body_oblique_r", "0.362"},
			{"body/body_oblique_l", "0.346"},
			{"body/body_chest", "0.452"},
			{"body/body_pec_r", "0.495"},
			{"body/body_pec_l", "0.503"},
			{"body/body_pec_edge_r", "0.518"},
			{"body/body_pec_edge_l", "0.511"},
			{"body/body_back", "0.490"},
			{"body/body_trapeze", "0.560"},
			{"body/body_collar_r", "0.554"},
			{"body/body_collar_l", "0.548"},
			{"body/body_ridge0", "0.566"},
			{"body/body_ridge1", "0.561"},
			{"body/body_ridge2", "0.564"},
			{"body/body_lat_r", "0.454"},
			{"body/body_lat_l", "0.488"},
			{"body/head/head_neck", "0.712"},
			{"body/head/head_skull", "0.774"},
			{"body/head/head_brow", "0.812"},
			{"body/head/head_nose", "0.849"},
			{"body/head/head_cheek_r", "0.825"},
			{"body/head/head_cheek_l", "0.800"},
			{"body/head/head_jaw", "0.786"},
			{"body/head/head_eye_r", "0.860"},
			{"body/head/head_eye_l", "0.860"},
			{"body/head/head_crown_a", "0.867"},
			{"body/head/head_crown_b", "0.854"},
			{"body/right_arm/right_arm_shoulder", "0.599"},
			{"body/right_arm/right_arm_shoulder_top", "0.616"},
			{"body/right_arm/right_arm_shoulder_spike", "0.632"},
			{"body/right_arm/right_arm_upper", "0.625"},
			{"body/right_arm/right_arm_biceps", "0.661"},
			{"body/right_arm/right_arm_triceps", "0.641"},
			{"body/right_arm/right_arm_elbow", "0.663"},
			{"body/right_arm/right_forearm/right_forearm_core", "0.693"},
			{"body/right_arm/right_forearm/right_forearm_plate_f", "0.739"},
			{"body/right_arm/right_forearm/right_forearm_plate_b", "0.733"},
			{"body/right_arm/right_forearm/right_forearm_plate_o", "0.724"},
			{"body/right_arm/right_forearm/right_forearm_cuff", "0.747"},
			{"body/right_arm/right_forearm/right_hand/right_hand_palm", "0.803"},
			{"body/right_arm/right_forearm/right_hand/right_hand_back", "0.822"},
			{"body/right_arm/right_forearm/right_hand/right_finger0/right_finger0_base", "0.908"},
			{"body/right_arm/right_forearm/right_hand/right_finger0/right_finger0_mid", "0.891"},
			{"body/right_arm/right_forearm/right_hand/right_finger0/right_finger0_tip", "0.934"},
			{"body/right_arm/right_forearm/right_hand/right_finger0/right_finger0_knuckle", "0.942"},
			{"body/right_arm/right_forearm/right_hand/right_finger1/right_finger1_base", "0.871"},
			{"body/right_arm/right_forearm/right_hand/right_finger1/right_finger1_mid", "0.924"},
			{"body/right_arm/right_forearm/right_hand/right_finger1/right_finger1_tip", "0.908"},
			{"body/right_arm/right_forearm/right_hand/right_finger1/right_finger1_knuckle", "0.952"},
			{"body/right_arm/right_forearm/right_hand/right_finger2/right_finger2_base", "0.867"},
			{"body/right_arm/right_forearm/right_hand/right_finger2/right_finger2_mid", "0.905"},
			{"body/right_arm/right_forearm/right_hand/right_finger2/right_finger2_tip", "0.913"},
			{"body/right_arm/right_forearm/right_hand/right_finger2/right_finger2_knuckle", "0.933"},
			{"body/right_arm/right_forearm/right_hand/right_finger3/right_finger3_base", "0.918"},
			{"body/right_arm/right_forearm/right_hand/right_finger3/right_finger3_mid", "0.928"},
			{"body/right_arm/right_forearm/right_hand/right_finger3/right_finger3_tip", "0.918"},
			{"body/right_arm/right_forearm/right_hand/right_finger3/right_finger3_knuckle", "0.964"},
			{"body/right_arm/right_forearm/right_hand/right_thumb/right_thumb_base", "0.908"},
			{"body/right_arm/right_forearm/right_hand/right_thumb/right_thumb_tip", "0.956"},
			{"body/left_arm/left_arm_shoulder", "0.563"},
			{"body/left_arm/left_arm_shoulder_top", "0.604"},
			{"body/left_arm/left_arm_shoulder_spike", "0.647"},
			{"body/left_arm/left_arm_upper", "0.610"},
			{"body/left_arm/left_arm_biceps", "0.654"},
			{"body/left_arm/left_arm_triceps", "0.645"},
			{"body/left_arm/left_arm_elbow", "0.674"},
			{"body/left_arm/left_forearm/left_forearm_core", "0.718"},
			{"body/left_arm/left_forearm/left_forearm_plate_f", "0.725"},
			{"body/left_arm/left_forearm/left_forearm_plate_b", "0.727"},
			{"body/left_arm/left_forearm/left_forearm_plate_o", "0.746"},
			{"body/left_arm/left_forearm/left_forearm_cuff", "0.745"},
			{"body/left_arm/left_forearm/left_hand/left_hand_palm", "0.805"},
			{"body/left_arm/left_forearm/left_hand/left_hand_back", "0.847"},
			{"body/left_arm/left_forearm/left_hand/left_finger0/left_finger0_base", "0.909"},
			{"body/left_arm/left_forearm/left_hand/left_finger0/left_finger0_mid", "0.895"},
			{"body/left_arm/left_forearm/left_hand/left_finger0/left_finger0_tip", "0.911"},
			{"body/left_arm/left_forearm/left_hand/left_finger0/left_finger0_knuckle", "0.957"},
			{"body/left_arm/left_forearm/left_hand/left_finger1/left_finger1_base", "0.916"},
			{"body/left_arm/left_forearm/left_hand/left_finger1/left_finger1_mid", "0.892"},
			{"body/left_arm/left_forearm/left_hand/left_finger1/left_finger1_tip", "0.957"},
			{"body/left_arm/left_forearm/left_hand/left_finger1/left_finger1_knuckle", "0.964"},
			{"body/left_arm/left_forearm/left_hand/left_finger2/left_finger2_base", "0.896"},
			{"body/left_arm/left_forearm/left_hand/left_finger2/left_finger2_mid", "0.905"},
			{"body/left_arm/left_forearm/left_hand/left_finger2/left_finger2_tip", "0.906"},
			{"body/left_arm/left_forearm/left_hand/left_finger2/left_finger2_knuckle", "0.922"},
			{"body/left_arm/left_forearm/left_hand/left_finger3/left_finger3_base", "0.918"},
			{"body/left_arm/left_forearm/left_hand/left_finger3/left_finger3_mid", "0.894"},
			{"body/left_arm/left_forearm/left_hand/left_finger3/left_finger3_tip", "0.942"},
			{"body/left_arm/left_forearm/left_hand/left_finger3/left_finger3_knuckle", "0.933"},
			{"body/left_arm/left_forearm/left_hand/left_thumb/left_thumb_base", "0.933"},
			{"body/left_arm/left_forearm/left_hand/left_thumb/left_thumb_tip", "0.964"},
			{"right_leg/right_leg_hip_plate", "0.174"},
			{"right_leg/right_leg_thigh", "0.132"},
			{"right_leg/right_leg_quad", "0.134"},
			{"right_leg/right_leg_knee", "0.200"},
			{"right_leg/right_shin/right_shin_core", "0.031"},
			{"right_leg/right_shin/right_shin_plate", "0.070"},
			{"right_leg/right_shin/right_shin_calf", "0.091"},
			{"right_leg/right_shin/right_shin_foot", "0.010"},
			{"right_leg/right_shin/right_shin_toes", "0.029"},
			{"left_leg/left_leg_hip_plate", "0.152"},
			{"left_leg/left_leg_thigh", "0.109"},
			{"left_leg/left_leg_quad", "0.159"},
			{"left_leg/left_leg_knee", "0.163"},
			{"left_leg/left_shin/left_shin_core", "0.031"},
			{"left_leg/left_shin/left_shin_plate", "0.082"},
			{"left_leg/left_shin/left_shin_calf", "0.094"},
			{"left_leg/left_shin/left_shin_foot", "0.018"},
			{"left_leg/left_shin/left_shin_toes", "0.028"}
	};

	public static final String[][] ARM_PIECES = {
			{"arm/arm_core_low", "0.120"},
			{"arm/arm_core_high", "0.400"},
			{"arm/arm_ring0_f", "0.020"},
			{"arm/arm_ring0_b", "0.060"},
			{"arm/arm_ring0_o", "0.040"},
			{"arm/arm_ring0_i", "0.080"},
			{"arm/arm_ring1_f", "0.240"},
			{"arm/arm_ring1_b", "0.280"},
			{"arm/arm_ring1_o", "0.260"},
			{"arm/arm_ring1_i", "0.300"},
			{"arm/arm_ring2_f", "0.440"},
			{"arm/arm_ring2_b", "0.480"},
			{"arm/arm_ring2_o", "0.460"},
			{"arm/arm_ring2_i", "0.500"},
			{"arm/arm_shoulder", "0.620"},
			{"arm/arm_shoulder_spike", "0.700"},
			{"arm/arm_elbow", "0.300"},
			{"arm/fist/fist_wrist", "0.180"},
			{"arm/fist/fist_palm", "0.300"},
			{"arm/fist/fist_back", "0.360"},
			{"arm/fist/fist_finger0", "0.450"},
			{"arm/fist/fist_fold0", "0.500"},
			{"arm/fist/fist_knuckle0", "0.600"},
			{"arm/fist/fist_finger1", "0.520"},
			{"arm/fist/fist_fold1", "0.570"},
			{"arm/fist/fist_knuckle1", "0.670"},
			{"arm/fist/fist_finger2", "0.590"},
			{"arm/fist/fist_fold2", "0.640"},
			{"arm/fist/fist_knuckle2", "0.740"},
			{"arm/fist/fist_finger3", "0.660"},
			{"arm/fist/fist_fold3", "0.710"},
			{"arm/fist/fist_knuckle3", "0.810"},
			{"arm/fist/fist_thumb", "0.820"},
			{"arm/fist/fist_thumb_tip", "0.880"},
			{"arm/fist/fist_plate", "0.920"}
	};
}
