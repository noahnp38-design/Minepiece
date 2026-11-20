package minepiece.noah.client;

import minepiece.noah.client.render.RaceRendering;
import minepiece.noah.client.title.TitleBackground;
import minepiece.noah.network.OpenSelectionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MinePieceClient implements ClientModInitializer {
	/** Mis à true à la réception du paquet ; l'écran s'ouvre dès que plus aucun autre écran n'est affiché. */
	private static boolean pendingOpen = false;

	@Override
	public void onInitializeClient() {
		RaceRendering.init();
		net.minecraft.client.renderer.entity.EntityRenderers.register(minepiece.noah.entity.ModEntities.MARINE,
				minepiece.noah.client.render.MarineRenderer::new);
		// Particules du Hie Hie no Mi : textures du mod, comportements vanilla (flocon, étincelle, nuage).
		var particles = net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance();
		particles.register(minepiece.noah.particle.ModParticles.ICE_SHARD, net.minecraft.client.particle.SnowflakeParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.ICE_SPARKLE, net.minecraft.client.particle.EndRodParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.FROST_MIST, net.minecraft.client.particle.PlayerCloudParticle.Provider::new);
		// Particules du Zushi Zushi no Mi : textures violettes du mod ; comportements vanilla réutilisés (étincelle qui flotte,
		// aspiration "portail" vers un point, nuage, éclat qui tombe). Le réglage Particules du joueur s'applique.
		particles.register(minepiece.noah.particle.ModParticles.GRAVITY_SPARK, net.minecraft.client.particle.EndRodParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.GRAVITY_PULL, net.minecraft.client.particle.PortalParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.GRAVITY_DUST, net.minecraft.client.particle.PlayerCloudParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.ROCK_CHIP, net.minecraft.client.particle.SnowflakeParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.GRAVITY_FLASH, net.minecraft.client.particle.EndRodParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.GRAVITY_MOTE, minepiece.noah.client.zushi.GravityMoteParticle.Provider::new);
		minepiece.noah.client.zushi.ClientGravityZones.init();
		// Goro Goro no Mi : textures électriques du mod, comportements vanilla (étincelle lumineuse, nuage).
		particles.register(minepiece.noah.particle.ModParticles.ELECTRIC_SPARK, minepiece.noah.client.goro.ElectricParticle.Spark::new);
		particles.register(minepiece.noah.particle.ModParticles.ELECTRIC_BLUE, minepiece.noah.client.goro.ElectricParticle.Blue::new);
		particles.register(minepiece.noah.particle.ModParticles.ELECTRIC_ARC, minepiece.noah.client.goro.ElectricParticle.Arc::new);
		particles.register(minepiece.noah.particle.ModParticles.ELECTRIC_BRANCH, minepiece.noah.client.goro.ElectricParticle.Branch::new);
		particles.register(minepiece.noah.particle.ModParticles.ELECTRIC_CHARGE, minepiece.noah.client.goro.ElectricParticle.Charge::new);
		// Haki
		particles.register(minepiece.noah.particle.ModParticles.HAKI_BOLT, minepiece.noah.client.haki.HakiParticle.Bolt::new);
		particles.register(minepiece.noah.particle.ModParticles.HAKI_ARC, minepiece.noah.client.haki.HakiParticle.Arc::new);
		particles.register(minepiece.noah.particle.ModParticles.HAKI_DISCHARGE, minepiece.noah.client.haki.HakiParticle.Discharge::new);
		particles.register(minepiece.noah.particle.ModParticles.HAKI_AURA, minepiece.noah.client.haki.HakiParticle.Aura::new);
		particles.register(minepiece.noah.particle.ModParticles.HAKI_CLASH, minepiece.noah.client.haki.HakiParticle.Clash::new);
		particles.register(minepiece.noah.particle.ModParticles.HAKI_PRESSURE, minepiece.noah.client.haki.HakiParticle.Pressure::new);
		particles.register(minepiece.noah.particle.ModParticles.HAKI_DARK, minepiece.noah.client.haki.HakiParticle.Dark::new);
		particles.register(minepiece.noah.particle.ModParticles.HAKI_GHOST, minepiece.noah.client.haki.HakiParticle.Ghost::new);
		particles.register(minepiece.noah.particle.ModParticles.STORM_SMOKE, net.minecraft.client.particle.PlayerCloudParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.STORM_DUST, net.minecraft.client.particle.PlayerCloudParticle.Provider::new);
		// Ishi Ishi no Mi : poussière grise, fragments et éclats de pierre, pierre aspirée vers le bras
		particles.register(minepiece.noah.particle.ModParticles.STONE_DUST, net.minecraft.client.particle.PlayerCloudParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.STONE_FRAGMENT, net.minecraft.client.particle.SnowflakeParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.STONE_SHARD, net.minecraft.client.particle.SnowflakeParticle.Provider::new);
		particles.register(minepiece.noah.particle.ModParticles.STONE_PULL, net.minecraft.client.particle.PortalParticle.Provider::new);
		TitleBackground.init();
		AbilityKeys.init();
		ProfileKey.init();
		RaceMovement.init();
		WadoShake.init();
		FruitHud.init();
		minepiece.noah.client.haki.HakiKeys.init();
		minepiece.noah.client.haki.HakiHud.init();
		minepiece.noah.client.ishi.IshiHud.init();
		minepiece.noah.client.haki.HakiClientFx.init();
		minepiece.noah.client.crew.ClientCrew.init();

		ClientPlayNetworking.registerGlobalReceiver(OpenSelectionPayload.TYPE,
				(payload, context) -> pendingOpen = true);

		// Coffre à Fruits : le serveur a déjà tiré le fruit, on ouvre l'écran de la bande qui défile.
		ClientPlayNetworking.registerGlobalReceiver(minepiece.noah.network.FruitChestStartPayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(
						new minepiece.noah.client.chest.FruitChestScreen(payload.winner(), payload.seed(), payload.weights())));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingOpen && client.player != null && client.gui.screen() == null) {
				pendingOpen = false;
				client.gui.setScreen(new SelectionScreen());
			}
		});
	}
}
