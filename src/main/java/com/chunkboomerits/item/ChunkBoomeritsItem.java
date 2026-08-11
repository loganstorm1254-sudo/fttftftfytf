package com.chunkboomerits.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.chunkboomerits.entity.ChunkBoomeritsEntity;

/**
 * OP-only throwable ball. Deletes a chunk on impact.
 * Obtain with {@code /chunkboomerits}.
 */
public class ChunkBoomeritsItem extends Item {
	public static final float THROW_POWER = 1.35F;

	public ChunkBoomeritsItem(Properties properties) {
		super(properties);
	}

	public static boolean isOperator(Player player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!isOperator(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.literal("Chunk Boomerits is OP-only. Use /chunkboomerits"), true);
			}
			return InteractionResult.FAIL;
		}

		level.playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.SNOWBALL_THROW,
				SoundSource.NEUTRAL,
				0.7F,
				0.35F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
		);

		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(
					ChunkBoomeritsEntity::new,
					serverLevel,
					stack,
					player,
					0.0F,
					THROW_POWER,
					0.5F
			);
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
