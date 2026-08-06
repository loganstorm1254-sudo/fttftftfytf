package com.chunkboomerits.entity;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

import com.chunkboomerits.ChunkBoomeritsMod;
import com.chunkboomerits.network.AscendingChunkPayload;

/**
 * Clears a chunk quickly and spawns a single rising mesh entity (lag-free).
 */
public final class ChunkLifter {
	/** Cap visible shell blocks so huge caves/worlds stay smooth. */
	private static final int MAX_MESH_BLOCKS = 6_000;

	private ChunkLifter() {
	}

	public static void ensureRegistered() {
		// Networking + entity registration happens in ModNetworking / ModEntities.
	}

	public static void liftChunk(ServerLevel level, BlockPos hitPos) {
		int chunkX = hitPos.getX() >> 4;
		int chunkZ = hitPos.getZ() >> 4;
		LevelChunk chunk = level.getChunk(chunkX, chunkZ);

		int minY = level.getMinY();
		int maxY = level.getMaxY();
		int originX = chunkX << 4;
		int originZ = chunkZ << 4;

		IntArrayList meshPacked = new IntArrayList();
		IntArrayList meshStates = new IntArrayList();
		IntArrayList clearPacked = new IntArrayList();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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
					if (!shouldLift(state, level, cursor)) {
						continue;
					}

					clearPacked.add(AscendingChunkPayload.pack(lx, y - minY, lz));

					if (meshPacked.size() < MAX_MESH_BLOCKS && isExposed(chunk, originX, originZ, minY, maxY, lx, y, lz)) {
						meshPacked.add(AscendingChunkPayload.pack(lx, y - minY, lz));
						meshStates.add(Block.getId(state));
					}
				}
			}
		}

		if (clearPacked.isEmpty()) {
			return;
		}

		clearBlocks(level, chunk, originX, originZ, minY, clearPacked);
		resendChunk(level, chunk);

		AscendingChunkEntity ascending = new AscendingChunkEntity(
				level,
				originX,
				minY,
				originZ,
				meshPacked.toIntArray(),
				meshStates.toIntArray()
		);
		level.addFreshEntity(ascending);

		level.playSound(null, hitPos, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.sendParticles(
				ParticleTypes.POOF,
				hitPos.getX() + 0.5,
				hitPos.getY() + 1.0,
				hitPos.getZ() + 0.5,
				20,
				1.5, 1.0, 1.5,
				0.02
		);

		ChunkBoomeritsMod.LOGGER.info(
				"Chunk Boomerits lifted chunk {}, {} ({} cleared, {} mesh)",
				chunkX,
				chunkZ,
				clearPacked.size(),
				meshPacked.size()
		);
	}

	private static void clearBlocks(
			ServerLevel level,
			LevelChunk chunk,
			int originX,
			int originZ,
			int minY,
			IntArrayList clearPacked
	) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean[] touchedSections = new boolean[chunk.getSectionsCount()];

		for (int i = 0; i < clearPacked.size(); i++) {
			int packed = clearPacked.getInt(i);
			int lx = AscendingChunkPayload.unpackX(packed);
			int ly = AscendingChunkPayload.unpackY(packed);
			int lz = AscendingChunkPayload.unpackZ(packed);
			int y = minY + ly;

			pos.set(originX + lx, y, originZ + lz);

			BlockEntity blockEntity = chunk.getBlockEntities().get(pos);
			if (blockEntity != null) {
				chunk.removeBlockEntity(pos);
			}

			int sectionIndex = chunk.getSectionIndex(y);
			LevelChunkSection section = chunk.getSection(sectionIndex);
			int sy = SectionPos.sectionRelative(y);
			section.setBlockState(lx, sy, lz, Blocks.AIR.defaultBlockState(), false);
			touchedSections[sectionIndex] = true;
		}

		for (int sectionIndex = 0; sectionIndex < touchedSections.length; sectionIndex++) {
			if (touchedSections[sectionIndex]) {
				chunk.getSection(sectionIndex).recalcBlockCounts();
			}
		}

		chunk.markUnsaved();
		level.getChunkSource().blockChanged(pos.set(originX, minY, originZ));
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

	private static boolean isExposed(LevelChunk chunk, int originX, int originZ, int minY, int maxY, int lx, int y, int lz) {
		return !occludes(chunk, originX, originZ, minY, maxY, lx + 1, y, lz)
				|| !occludes(chunk, originX, originZ, minY, maxY, lx - 1, y, lz)
				|| !occludes(chunk, originX, originZ, minY, maxY, lx, y + 1, lz)
				|| !occludes(chunk, originX, originZ, minY, maxY, lx, y - 1, lz)
				|| !occludes(chunk, originX, originZ, minY, maxY, lx, y, lz + 1)
				|| !occludes(chunk, originX, originZ, minY, maxY, lx, y, lz - 1);
	}

	private static boolean occludes(LevelChunk chunk, int originX, int originZ, int minY, int maxY, int lx, int y, int lz) {
		if (y < minY || y > maxY || lx < 0 || lx > 15 || lz < 0 || lz > 15) {
			return false;
		}
		BlockState state = chunk.getBlockState(new BlockPos(originX + lx, y, originZ + lz));
		return state.canOcclude();
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
}
