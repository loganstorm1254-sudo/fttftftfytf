package com.chunkboomerits.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import com.chunkboomerits.item.ModItems;

/**
 * The big Chunk Boomerits ball. On impact, launches the struck chunk into the air.
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

		if (this.level() instanceof ServerLevel serverLevel) {
			ChunkLifter.liftChunk(serverLevel, this.blockPosition());

			serverLevel.sendParticles(
					ParticleTypes.EXPLOSION,
					this.getX(),
					this.getY(),
					this.getZ(),
					8,
					0.6,
					0.6,
					0.6,
					0.02
			);
			serverLevel.playSound(
					null,
					this.getX(),
					this.getY(),
					this.getZ(),
					SoundEvents.GENERIC_EXPLODE.value(),
					SoundSource.PLAYERS,
					0.9F,
					0.7F
			);
		}

		this.discard();
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return false;
	}
}
