package minepiece.noah.chest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.data.DevilFruit;
import minepiece.noah.item.ModItems;
import minepiece.noah.network.FruitChestStartPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Logique serveur du Coffre à Fruits.
 *
 * Tout est décidé ici : vérification de la clé, tirage du fruit (AVANT l'animation), consommation du coffre et de la
 * clé, puis attribution du fruit une seule fois. Le client ne reçoit que de quoi dessiner l'animation.
 */
public final class FruitChestSystem {
	/** Le fruit est donné ce nombre de ticks après l'ouverture (l'animation dure 7 s = 140 ticks côté client). */
	public static final int GRANT_DELAY_TICKS = 155;

	private record Session(DevilFruit fruit, int grantTick) {}

	/** Une ouverture en cours par joueur. Accédé uniquement depuis le thread serveur. */
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private FruitChestSystem() {}

	public static void init() {
		FruitChestConfig.load();

		ServerTickEvents.END_SERVER_TICK.register(FruitChestSystem::tick);

		// Déconnexion pendant l'animation : le fruit est tout de même donné (rien n'est perdu, rien n'est dupliqué).
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Session session = SESSIONS.remove(handler.getPlayer().getUUID());
			if (session != null) {
				grant(handler.getPlayer(), session.fruit());
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Map.Entry<UUID, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					grant(p, e.getValue().fruit());
				}
			}
			SESSIONS.clear();
		});
	}

	// ------------------------------------------------------------------ ouverture

	public static void tryOpen(ServerPlayer player, InteractionHand hand) {
		ItemStack chest = player.getItemInHand(hand);
		if (chest.isEmpty() || chest.getItem() != ModItems.FRUIT_CHEST) {
			return;
		}
		UUID id = player.getUUID();
		if (SESSIONS.containsKey(id)) {
			message(player, "§eUn coffre est déjà en cours d'ouverture.");
			return;
		}

		Inventory inv = player.getInventory();
		int keySlot = findKey(inv);
		if (keySlot < 0) {
			message(player, "§cIl vous faut une Clé du Coffre à Fruits pour ouvrir ce coffre.");
			player.level().playSound(null, player.blockPosition(), SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.8F, 1.0F);
			return;
		}

		// 1) le serveur tire le gagnant, avant toute animation
		int[] weights = FruitChestConfig.weights();
		DevilFruit[] all = DevilFruit.values();
		int winner = pick(weights, player.getRandom().nextLong());
		DevilFruit fruit = all[winner];
		long seed = player.getRandom().nextLong();

		// 2) l'ouverture est validée : on consomme le coffre et la clé (même thread, rien ne peut s'intercaler)
		chest.shrink(1);
		inv.getItem(keySlot).shrink(1);

		MinecraftServer server = player.level().getServer();
		int now = server == null ? 0 : server.getTickCount();
		SESSIONS.put(id, new Session(fruit, now + GRANT_DELAY_TICKS));

		List<Integer> w = new ArrayList<>(weights.length);
		for (int x : weights) {
			w.add(x);
		}
		ServerPlayNetworking.send(player, new FruitChestStartPayload(winner, seed, w));
		player.level().playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	private static int findKey(Inventory inv) {
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.getItem() == ModItems.FRUIT_KEY) {
				return i;
			}
		}
		return -1;
	}

	/** Tirage pondéré. Si tous les poids sont à 0, tirage équitable. */
	static int pick(int[] weights, long randomBits) {
		long total = 0;
		for (int w : weights) {
			total += Math.max(0, w);
		}
		if (total <= 0) {
			return (int) Long.remainderUnsigned(randomBits, weights.length);
		}
		long r = Long.remainderUnsigned(randomBits, total);
		for (int i = 0; i < weights.length; i++) {
			int w = Math.max(0, weights[i]);
			if (r < w) {
				return i;
			}
			r -= w;
		}
		return weights.length - 1;
	}

	// ------------------------------------------------------------------ fin d'animation

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> e = it.next();
			if (now < e.getValue().grantTick()) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove(); // déjà traité à la déconnexion
			} else if (player.isAlive()) {
				it.remove(); // retiré AVANT l'attribution : impossible de donner deux fois
				grant(player, e.getValue().fruit());
			}
			// joueur mort : on attend sa réapparition, le fruit n'est pas perdu
		}
	}

	private static void grant(ServerPlayer player, DevilFruit fruit) {
		ItemStack stack = new ItemStack(ModItems.of(fruit));
		Component name = stack.getHoverName();
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			// inventaire plein : le fruit tombe aux pieds du joueur, jamais perdu
			if (player.level() instanceof ServerLevel level) {
				level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
						player.getX(), player.getY() + 0.5, player.getZ(), stack));
			}
			message(player, "§eInventaire plein : le fruit est tombé à vos pieds.");
		}
		player.sendSystemMessage(Component.literal("§6Coffre à Fruits : §fvous avez obtenu §e").append(name));
	}

	private static void message(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal(text), true);
	}
}
