package minepiece.noah;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.ability.RacePowers;
import minepiece.noah.command.ModCommands;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.RaceEffects;
import minepiece.noah.effect.ModEffects;
import minepiece.noah.entity.ModEntities;
import minepiece.noah.item.ModCreativeTabs;
import minepiece.noah.item.ModItems;
import minepiece.noah.network.ModNetworking;
import minepiece.noah.sound.ModSounds;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinePiece implements ModInitializer {
	public static final String MOD_ID = "minepiece";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModAttachments.init();
		ModEffects.init();
		minepiece.noah.particle.ModParticles.init();
		minepiece.noah.block.ModBlocks.init();
		ModEntities.init();
		ModItems.init();
		ModCreativeTabs.init();
		ModSounds.init();
		ModNetworking.init();
		FruitAbilities.init();
		RaceEffects.init();
		RacePowers.init();
		ModCommands.init();
		minepiece.noah.world.ModWorldgen.init();
		minepiece.noah.haki.HakiManager.init();
		LOGGER.info("MinePiece charge !");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
