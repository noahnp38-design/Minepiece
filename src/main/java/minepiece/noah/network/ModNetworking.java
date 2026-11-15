package minepiece.noah.network;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.data.CombatStyle;
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

		// À la connexion : si le joueur n'a pas encore de profil, on lui demande d'en choisir un.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (player.hasAttached(ModAttachments.PROFILE)) {
				RaceEffects.apply(player);
			} else {
				ServerPlayNetworking.send(player, new OpenSelectionPayload());
			}
		});

		// Attaque de fruit du démon : le serveur vérifie le fruit, la recharge, etc.
		ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.TYPE, (payload, context) -> {
			if (payload.pressed()) {
				FruitAbilities.use(context.player(), payload.slot());
			} else {
				FruitAbilities.release(context.player(), payload.slot());
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
			player.sendSystemMessage(Component.literal(
					"Bienvenue à Grand Line ! " + race.displayName() + " / "
							+ faction.displayName() + " / " + style.displayName()));
		});
	}
}
