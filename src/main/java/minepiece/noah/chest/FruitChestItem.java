package minepiece.noah.chest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Le Coffre à Fruits : clic droit avec le coffre en main (la logique est entièrement côté serveur). */
public class FruitChestItem extends Item {
	public FruitChestItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			FruitChestSystem.tryOpen(serverPlayer, hand);
		}
		return InteractionResult.CONSUME;
	}
}
