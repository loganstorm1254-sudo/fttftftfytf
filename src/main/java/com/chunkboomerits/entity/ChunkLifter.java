package com.chunkboomerits.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

import com.chunkboomerits.ChunkBoomeritsMod;

/**
 * Instantly deletes a chunk's blocks (no floating).
 */
public final class ChunkLifter {
	private ChunkLifter() {
	}

	public static void deleteChunk(ServerLevel level, BlockPos hitPos) {
		int chunkX = hitPos.getX() >> 4;
		int chunkZ = hitPos.getZ() >> 4;
		LevelChunk chunk = level.getChunk(chunkX, chunkZ);

		int minY = level.getMinY();
		int maxY = level.getMaxY();
		int originX = chunkX << 4;
		int originZ = chunkZ << 4;

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean[] touchedSections = new boolean[chunk.getSectionsCount()];
		int cleared = 0;

		for (int y = minY; y <= maxY; y++) {
			int sectionIndex = chunk.getSectionIndex(y);
			LevelChunkSection section = chunk.getSection(sectionIndex);
			if (section.hasOnlyAir()) {
				continue;
			}

			int sy = SectionPos.sectionRelative(y);
			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					BlockState state = section.getBlockState(lx, sy, lz);
					if (state.isAir()) {
						continue;
					}

					cursor.set(originX + lx, y, originZ + lz);
					if (!shouldDelete(state, level, cursor)) {
						continue;
					}

					BlockEntity blockEntity = chunk.getBlockEntities().get(cursor);
					if (blockEntity != null) {
						chunk.removeBlockEntity(cursor.immutable());
					}

					section.setBlockState(lx, sy, lz, Blocks.AIR.defaultBlockState(), false);
					touchedSections[sectionIndex] = true;
					cleared++;
				}
			}
		}

		if (cleared == 0) {
			return;
		}

		for (int sectionIndex = 0; sectionIndex < touchedSections.length; sectionIndex++) {
			if (touchedSections[sectionIndex]) {
				chunk.getSection(sectionIndex).recalcBlockCounts();
			}
		}

		chunk.markUnsaved();
		resendChunk(level, chunk);

		level.playSound(null, hitPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 0.85F);
		level.sendParticles(
				ParticleTypes.EXPLOSION,
				hitPos.getX() + 0.5,
				hitPos.getY() + 1.0,
				hitPos.getZ() + 0.5,
				6,
				1.2, 0.8, 1.2,
				0.02
		);
		level.sendParticles(
				ParticleTypes.POOF,
				originX + 8.0,
				hitPos.getY() + 1.0,
				originZ + 8.0,
				30,
				4.0, 2.0, 4.0,
				0.02
		);

		ChunkBoomeritsMod.LOGGER.info("Chunk Boomerits deleted {} blocks from chunk {}, {}", cleared, chunkX, chunkZ);
	}

	private static void resendChunk(ServerLevel level, LevelChunk chunk) {
		ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
				chunk,
				level.getLightEngine(),
				null,
				null
		);
		for (ServerPlayer player : PlayerLookup.tracking(level, chunk.getPos())) {
			player.connection.send(packet);
		}
	}

	private static boolean shouldDelete(BlockState state, ServerLevel level, BlockPos pos) {
		if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.STRUCTURE_VOID)
				|| state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.COMMAND_BLOCK)
				|| state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)
				|| state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)
				|| state.is(Blocks.END_GATEWAY)) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0F;
	}
}
