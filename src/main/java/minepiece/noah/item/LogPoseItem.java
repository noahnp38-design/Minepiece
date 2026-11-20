package minepiece.noah.item;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Log Pose : l'aiguille du globe pointe vers l'île enregistrée.
 * Clic droit sur une magnétite (lodestone) = enregistre ce lieu (le "champ magnétique" de l'île).
 * Sans île enregistrée, l'aiguille pointe vers le point d'apparition du monde.
 * La rotation de l'aiguille est gérée par le modèle (items/log_pose.json, propriété "compass" de Minecraft).
 */
public class LogPoseItem extends Item {
	public LogPoseItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.getBlockState(pos).is(Blocks.LODESTONE)) {
			return super.useOn(context);
		}
		if (!level.isClientSide()) {
			ItemStack stack = context.getItemInHand();
			stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos)), false));
			level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
			Player player = context.getPlayer();
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("§bLe Log Pose a enregistré le champ magnétique de cette île."), true);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
