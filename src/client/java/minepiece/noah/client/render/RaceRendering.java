package minepiece.noah.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

/** Point d'entree du rendu des races (client). */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class RaceRendering {
	private RaceRendering() {}

	public static void init() {
		RaceModels.register();
		GiraffeModels.register();
		IceArmModel.register();
		minepiece.noah.client.haki.HakiArmModel.register();
		minepiece.noah.client.ishi.IshiModels.register();

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof AvatarRenderer avatarRenderer) {
				helper.register(new RaceLayer(avatarRenderer, context));
				helper.register(new GiraffeLayer(avatarRenderer, context));
				helper.register(new IceArmLayer(avatarRenderer, context));
				helper.register(new minepiece.noah.client.haki.HakiLayer(avatarRenderer, context));
				helper.register(new minepiece.noah.client.ishi.IshiLayer(avatarRenderer, context));
			}
		});
	}
}
