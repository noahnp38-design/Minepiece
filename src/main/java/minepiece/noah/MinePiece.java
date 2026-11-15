package minepiece.noah;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.RaceEffects;
import minepiece.noah.network.ModNetworking;
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
		ModNetworking.init();
		RaceEffects.init();
		LOGGER.info("MinePiece charge !");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
