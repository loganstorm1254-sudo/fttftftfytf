package com.chunkboomerits.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import com.chunkboomerits.item.ChunkBoomeritsItem;
import com.chunkboomerits.item.ModItems;

/**
 * The big Chunk Boomerits ball. On impact, deletes the struck chunk (OP throwers only).
 */
public class ChunkBoomeritsEntity extends ThrowableItemProjectile {
	public ChunkBoomeritsEntity(EntityType<? extends ChunkBoomeritsEntity> type, Level level) {
		super(type, level);
	}

	public ChunkBoomeritsEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.CHUNK_BOOMERITS, owner, level, stack);
	}

	public ChunkBoomeritsEntity(Level level, double x, double y, double z, ItemStack stack) {
		super(ModEntities.CHUNK_BOOMERITS, x, y, z, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.CHUNK_BOOMERITS;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);

		if (this.level().isClientSide()) {
			return;
		}

		Entity owner = this.getOwner();
		if (owner instanceof Player player && ChunkBoomeritsItem.isOperator(player)
				&& this.level() instanceof ServerLevel serverLevel) {
			ChunkLifter.deleteChunk(serverLevel, this.blockPosition());
		}

		this.discard();
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return false;
	}
}
