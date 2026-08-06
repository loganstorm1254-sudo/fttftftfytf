package com.chunkboomerits.entity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.chunkboomerits.ChunkBoomeritsMod;

/**
 * Pulls a whole chunk out of the world as rising falling-blocks, then eats them at +100.
 */
public final class ChunkLifter {
	private static final double RISE_SPEED = 0.42;
	private static final int MAX_BLOCKS = 12_288;

	/** entity UUID -> Y at which the block gets eaten */
	private static final Map<UUID, Double> EAT_AT_Y = new ConcurrentHashMap<>();

	private static boolean registered;

	private ChunkLifter() {
	}

	public static void ensureRegistered() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_WORLD_TICK.register(ChunkLifter::tickWorld);
	}

	public static void liftChunk(ServerLevel level, BlockPos hitPos) {
		ensureRegistered();

		int chunkX = hitPos.getX() >> 4;
		int chunkZ = hitPos.getZ() >> 4;
		LevelChunk chunk = level.getChunk(chunkX, chunkZ);

		int minY = level.getMinY();
		int maxY = level.getMaxY();
		int originX = chunkX << 4;
		int originZ = chunkZ << 4;

		int lifted = 0;

		for (int y = minY; y <= maxY && lifted < MAX_BLOCKS; y++) {
			for (int lx = 0; lx < 16 && lifted < MAX_BLOCKS; lx++) {
				for (int lz = 0; lz < 16 && lifted < MAX_BLOCKS; lz++) {
					BlockPos pos = new BlockPos(originX + lx, y, originZ + lz);
					BlockState state = chunk.getBlockState(pos);

					if (state.isAir() || !shouldLift(state, level, pos)) {
						continue;
					}

					BlockEntity blockEntity = level.getBlockEntity(pos);
					if (blockEntity != null) {
						level.removeBlockEntity(pos);
					}

					// FallingBlockEntity.fall clears the block and spawns the entity.
					FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
					falling.setNoGravity(true);
					falling.setDeltaMovement(0.0, RISE_SPEED, 0.0);
					falling.dropItem = false;
					falling.disableDrop();
					falling.setHurtsEntities(0.0F, 0);

					double eatY = pos.getY() + ChunkBoomeritsMod.EAT_HEIGHT;
					EAT_AT_Y.put(falling.getUUID(), eatY);
					lifted++;
				}
			}
		}

		if (lifted > 0) {
			level.playSound(
					null,
					hitPos,
					SoundEvents.ENDER_DRAGON_FLAP,
					SoundSource.PLAYERS,
					1.2F,
					0.6F
			);
			ChunkBoomeritsMod.LOGGER.info("Chunk Boomerits lifted {} blocks from chunk {}, {}", lifted, chunkX, chunkZ);
		}
	}

	private static boolean shouldLift(BlockState state, ServerLevel level, BlockPos pos) {
		if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.STRUCTURE_VOID)
				|| state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.COMMAND_BLOCK)
				|| state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)
				|| state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)
				|| state.is(Blocks.END_GATEWAY)) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0F;
	}

	private static void tickWorld(ServerLevel level) {
		if (EAT_AT_Y.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Double>> iterator = EAT_AT_Y.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Double> entry = iterator.next();
			Entity entity = level.getEntity(entry.getKey());

			if (entity == null || entity.isRemoved()) {
				iterator.remove();
				continue;
			}

			entity.setNoGravity(true);
			entity.setDeltaMovement(0.0, RISE_SPEED, 0.0);

			if (entity.getY() >= entry.getValue()) {
				eat(level, entity);
				iterator.remove();
			}
		}
	}

	private static void eat(ServerLevel level, Entity entity) {
		BlockState particleState = Blocks.STONE.defaultBlockState();
		if (entity instanceof FallingBlockEntity falling) {
			particleState = falling.getBlockState();
		}

		level.sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, particleState),
				entity.getX(),
				entity.getY(),
				entity.getZ(),
				18,
				0.35,
				0.35,
				0.35,
				0.12
		);
		level.sendParticles(
				ParticleTypes.SMOKE,
				entity.getX(),
				entity.getY(),
				entity.getZ(),
				6,
				0.2,
				0.2,
				0.2,
				0.02
		);

		if (level.random.nextInt(40) == 0) {
			level.playSound(
					null,
					entity.getX(),
					entity.getY(),
					entity.getZ(),
					SoundEvents.GENERIC_EAT.value(),
					SoundSource.PLAYERS,
					0.8F,
					0.7F + level.random.nextFloat() * 0.4F
			);
		}

		entity.discard();
	}
}
