package minepiece.noah.item;

import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Un fruit du démon : clic droit MAINTENU pour le manger (animation de repas, voir ModItems),
 * le pouvoir n'est donné qu'à la fin de la consommation.
 */
public class DevilFruitItem extends Item {
	private final DevilFruit fruit;

	public DevilFruitItem(Properties properties, DevilFruit fruit) {
		super(properties);
		this.fruit = fruit;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// On refuse AVANT de commencer à manger si le joueur a déjà un fruit (vérifié des deux côtés :
		// l'attachement est synchronisé vers le joueur).
		if (player.hasAttached(ModAttachments.FRUIT)) {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				sp.sendSystemMessage(Component.literal("Ton corps rejette ce fruit : tu as déjà mangé un fruit du démon !"), true);
			}
			return InteractionResult.FAIL;
		}
		// Le composant "consommable" (ModItems) lance l'animation de repas.
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer sp)) {
			return super.finishUsingItem(stack, level, entity);
		}
		// Revérifié ici : le joueur a pu recevoir un fruit pendant qu'il mangeait (commande, etc.).
		if (sp.hasAttached(ModAttachments.FRUIT)) {
			sp.sendSystemMessage(Component.literal("Ton corps rejette ce fruit : tu as déjà mangé un fruit du démon !"), true);
			return stack;
		}

		sp.setAttached(ModAttachments.FRUIT, fruit);
		sp.playSound(SoundEvents.PLAYER_BURP, 1.0F, 0.8F);
		sp.sendSystemMessage(Component.literal("Tu as mangé le " + fruit.displayName()
				+ " ! Attaques : Z / X / C (modifiable dans les contrôles)."));
		// super retire le fruit de l'inventaire (sauf en créatif).
		return super.finishUsingItem(stack, level, entity);
	}
}
