package minepiece.noah.network;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.ability.giraffe.GiraffeAbilities;
import minepiece.noah.data.CombatStyle;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.Faction;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.data.RaceEffects;
import minepiece.noah.data.Race;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {
	private ModNetworking() {}

	public static void init() {
		// Les payloads doivent être enregistrés des deux côtés (cette classe est dans "main").
		PayloadTypeRegistry.clientboundPlay().register(OpenSelectionPayload.TYPE, OpenSelectionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectionPayload.TYPE, SelectionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WadoShakePayload.TYPE, WadoShakePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GravityZonePayload.TYPE, GravityZonePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FruitCooldownPayload.TYPE, FruitCooldownPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FruitChestStartPayload.TYPE, FruitChestStartPayload.CODEC);
		minepiece.noah.chest.FruitChestSystem.init();
		PayloadTypeRegistry.clientboundPlay().register(CrewSyncPayload.TYPE, CrewSyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CrewActionPayload.TYPE, CrewActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CrewMessagePayload.TYPE, CrewMessagePayload.CODEC);
		minepiece.noah.crew.CrewManager.init();
		ServerPlayNetworking.registerGlobalReceiver(CrewActionPayload.TYPE,
				(payload, context) -> minepiece.noah.crew.CrewManager.handle(context.player(), payload));

		// À la connexion : si le joueur n'a pas encore de profil, on lui demande d'en choisir un.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (player.hasAttached(ModAttachments.PROFILE)) {
				RaceEffects.apply(player);
			} else {
				ServerPlayNetworking.send(player, new OpenSelectionPayload());
			}
		});

		// Touche d'attaque : attaque du fruit du démon.
		// Le serveur vérifie tout (objet en main, fruit, recharge...).
		ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.TYPE, (payload, context) -> {
			int slot = payload.slot();
			if (slot == GiraffeAbilities.SLOT_CYCLE && context.player().getAttached(ModAttachments.FRUIT) == DevilFruit.ISHI_ISHI_NO_MI) {
				// Ishi Ishi no Mi : V transforme en Titan de pierre / libère du titan (comme la 3e touche d'attaque)
				if (payload.pressed()) {
					FruitAbilities.use(context.player(), 2);
				}
			} else if (slot == GiraffeAbilities.SLOT_CYCLE || slot == GiraffeAbilities.SLOT_HUMAN) {
				// Touches V / B : changement de forme du Ushi Ushi no Mi (le serveur revérifie tout).
				if (payload.pressed()) {
					GiraffeAbilities.requestForm(context.player(), slot == GiraffeAbilities.SLOT_HUMAN);
				}
			} else if (slot >= DevilFruit.ABILITY_COUNT) {
				// Touches Z / X / C (slots 3..5) : attaques du Suke Suke no Mi.
				if (payload.pressed() && slot < 2 * DevilFruit.ABILITY_COUNT) {
					FruitAbilities.useAlt(context.player(), slot - DevilFruit.ABILITY_COUNT);
				}
			} else if (payload.pressed()) {
				FruitAbilities.use(context.player(), slot);
			} else {
				FruitAbilities.release(context.player(), slot);
			}
		});

		// Réception du choix : on valide TOUT côté serveur (ne jamais faire confiance au client).
		ServerPlayNetworking.registerGlobalReceiver(SelectionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.hasAttached(ModAttachments.PROFILE)) {
				return; // choix déjà fait, on ignore
			}

			Race race = Race.byId(payload.race());
			Faction faction = Faction.byId(payload.faction());
			CombatStyle style = CombatStyle.byId(payload.combatStyle());
			if (race == null || faction == null || style == null) {
				return; // paquet invalide
			}

			player.setAttached(ModAttachments.PROFILE, new PlayerProfile(race, faction, style));
			RaceEffects.apply(player);
			minepiece.noah.crew.CrewManager.sync(player);
			player.sendSystemMessage(Component.literal(
					"Bienvenue à Grand Line ! " + race.displayName() + " / "
							+ faction.displayName() + " / " + style.displayName()));
		});
	}
}
