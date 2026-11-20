package minepiece.noah.client.render;

import minepiece.noah.ability.giraffe.GiraffeAnim;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Modèle animé du Ushi Ushi no Mi. Une seule classe pour les deux formes : "hybrid" = accessoires qui copient la pose du joueur
 * (on n'en modifie que les ENFANTS : cou, tête, taches, queue) ; sinon = girafe complète avec ses propres pattes.
 * Tout est calculé à partir de State (aucune donnée du joueur lue ici) : le rendu est identique pour tous les clients.
 */
public class GiraffeModel extends Model<GiraffeModel.State> {
	/**
	 * @param w         poids visuel de cette forme (0..1)
	 * @param animTime  temps (ticks) dans l'attaque en cours, -1 si aucune
	 * @param anim      attaque en cours
	 * @param transition avancement de la transformation (0..1) ; < 1 = en cours
	 * @param scale     taille du joueur (SCALE) : sert à convertir des blocs en pixels de modèle
	 * @param walkPos   position de marche, walkSpeed : vitesse de marche (pattes)
	 * @param headYaw   rotation de la tête par rapport au corps (degrés), headPitch : inclinaison (degrés)
	 */
	public record State(float w, float animTime, int anim, float transition, float scale, float age,
			float walkPos, float walkSpeed, float headYaw, float headPitch) {}

	private final boolean hybrid;
	private final ModelPart body;
	private final ModelPart neckPivot;
	private final ModelPart neck;
	private final ModelPart headPivot;
	private final ModelPart tailPivot;
	private final ModelPart legFr, legFl, legBr, legBl;
	private final ModelPart[] spots;

	public GiraffeModel(ModelPart root, boolean hybrid) {
		super(root, id -> RenderTypes.armorCutoutNoCull(id));
		this.hybrid = hybrid;
		this.body = root.getChild("body");
		ModelPart neckHolder = hybrid ? root.getChild("head") : root;
		this.neckPivot = neckHolder.getChild("neck_pivot");
		this.neck = neckPivot.getChild("neck");
		this.headPivot = neckPivot.getChild("head_pivot");
		this.tailPivot = body.hasChild("tail_pivot") ? body.getChild("tail_pivot") : root.getChild("tail_pivot");
		if (hybrid) {
			this.legFr = this.legFl = this.legBr = this.legBl = null;
			this.spots = new ModelPart[] {
					body.getChild("spots"),
					root.getChild("right_arm").getChild("spots"), root.getChild("left_arm").getChild("spots"),
					root.getChild("right_leg").getChild("spots"), root.getChild("left_leg").getChild("spots"),
					root.getChild("right_leg").getChild("hoof"), root.getChild("left_leg").getChild("hoof"),
					tailPivot};
		} else {
			this.legFr = root.getChild("leg_fr");
			this.legFl = root.getChild("leg_fl");
			this.legBr = root.getChild("leg_br");
			this.legBl = root.getChild("leg_bl");
			this.spots = new ModelPart[0];
		}
	}

	private static float smooth(float a, float b, float x) {
		float t = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
		return t * t * (3.0F - 2.0F * t);
	}

	@Override
	public void setupAnim(State s) {
		float w = s.w();
		float t = s.animTime();
		boolean busy = s.transition() < 1.0F;

		// ---- Longueur et inclinaison du cou selon l'attaque (Bigan : coup de museau ; Kirin : cou à l'horizontale).
		double reachBlocks = 0.0;
		if (s.anim() == GiraffeAnim.ANIM_BIGAN) {
			reachBlocks = GiraffeAnim.biganReach(t);
		} else if (s.anim() == GiraffeAnim.ANIM_KIRIN) {
			reachBlocks = GiraffeAnim.kirinReach(t);
		}
		float reachPx = (float) (reachBlocks * 16.0 / Math.max(0.5F, s.scale()));
		float forward = smooth(0.0F, 1.2F, (float) reachBlocks);          // 0 = cou vertical, 1 = cou à l'horizontale
		float baseNeck = hybrid ? 12.0F : 24.0F;                           // longueur au repos (pixels)
		float grow = hybrid ? w : (0.45F + 0.55F * w);
		float neckPx = baseNeck * grow + reachPx;

		float lean = hybrid ? 0.12F : 0.30F;
		if (busy && hybrid) {
			lean += 0.55F * (float) Math.sin(s.transition() * Math.PI);       // "se penche" pendant la transformation
		}
		if (s.anim() == GiraffeAnim.ANIM_RANKYAKU && t >= 0.0F) {
			lean -= 0.25F * (float) Math.sin(Math.min(1.0F, t / GiraffeAnim.RANKYAKU_TICKS) * Math.PI);   // recule le buste au coup de pied
		}
		float pitchNeck = lean * (1.0F - forward) + (float) (Math.PI / 2.0) * forward;

		neckPivot.xRot = pitchNeck;
		neck.yScale = Math.max(0.02F, neckPx / GiraffeModels.NECK_UNIT);
		headPivot.y = -neckPx;
		float look = hybrid ? 0.0F : 1.0F;                                  // hybride : la tête du joueur gère déjà le regard
		headPivot.xRot = 0.30F - pitchNeck + look * (float) Math.toRadians(s.headPitch()) * (1.0F - forward);
		headPivot.yRot = look * (float) Math.toRadians(s.headYaw()) * (1.0F - forward);
		float headScale = hybrid ? Math.max(0.05F, smooth(0.15F, 0.7F, w)) : 1.0F;
		headPivot.xScale = headPivot.yScale = headPivot.zScale = headScale;

		// ---- Queue : balancement léger.
		tailPivot.zRot = (float) Math.sin(s.age() * 0.15F) * 0.12F;

		if (hybrid) {
			// Les taches et les membres gonflés apparaissent à mi-transformation (échelle -> 1).
			float pop = smooth(0.2F, 0.8F, w);
			for (ModelPart p : spots) {
				p.visible = pop > 0.02F;
				if (p != tailPivot) {
					p.xScale = p.yScale = p.zScale = 0.85F + 0.15F * pop;
				} else {
					p.xScale = p.yScale = p.zScale = pop;
				}
			}
			return;
		}

		// ---- Girafe complète : le corps s'allonge, les pattes aussi (transformation), puis marche en diagonale.
		float g = w;
		float legScale = 0.55F + 0.45F * g;
		float drop = (1.0F - legScale) * GiraffeModels.LEG_LEN;               // le corps descend quand les pattes sont courtes
		body.zScale = 0.45F + 0.55F * g;
		body.y = GiraffeModels.BODY_Y + drop;
		neckPivot.y = 4.0F + drop;
		tailPivot.y = 4.0F + drop;
		float swing = (float) Math.cos(s.walkPos() * 0.6662F) * 1.1F * s.walkSpeed();
		float swing2 = (float) Math.cos(s.walkPos() * 0.6662F + Math.PI) * 1.1F * s.walkSpeed();
		ModelPart[] legs = {legFr, legFl, legBr, legBl};
		float[] rot = {swing, swing2, swing2, swing};
		for (int i = 0; i < 4; i++) {
			legs[i].yScale = legScale;
			legs[i].y = GiraffeModels.LEG_Y + drop;
			legs[i].xRot = rot[i];
		}
		// Rankyaku à quatre pattes : ruade de la patte avant droite.
		if (s.anim() == GiraffeAnim.ANIM_RANKYAKU && t >= 0.0F) {
			legFr.xRot = -2.0F * (float) Math.sin(Math.min(1.0F, t / GiraffeAnim.RANKYAKU_TICKS) * Math.PI);
		}
		// Kirin : pattes écartées pendant la rotation.
		if (s.anim() == GiraffeAnim.ANIM_KIRIN && t >= 0.0F) {
			legFl.zRot = 0.4F;
			legFr.zRot = -0.4F;
			legBl.zRot = 0.4F;
			legBr.zRot = -0.4F;
		} else {
			legFl.zRot = legFr.zRot = legBl.zRot = legBr.zRot = 0.0F;
		}
	}
}
