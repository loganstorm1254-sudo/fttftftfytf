package com.chunkboomerits.paper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Instantly clears a chunk's breakable blocks (keeps bedrock / barriers / portals).
 */
public final class ChunkDeleter {
	private ChunkDeleter() {
	}

	public static void deleteChunk(World world, int chunkX, int chunkZ, Location fxAt) {
		int minY = world.getMinHeight();
		int maxY = world.getMaxHeight();
		int originX = chunkX << 4;
		int originZ = chunkZ << 4;
		int cleared = 0;

		for (int y = minY; y < maxY; y++) {
			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					Block block = world.getBlockAt(originX + lx, y, originZ + lz);
					if (!shouldDelete(block)) {
						continue;
					}
					block.setType(Material.AIR, false);
					cleared++;
				}
			}
		}

		if (cleared == 0) {
			return;
		}

		Location center = fxAt == null
				? new Location(world, originX + 8.0, Math.max(minY + 64, 64), originZ + 8.0)
				: fxAt.clone().add(0.5, 1.0, 0.5);

		world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.85f);
		world.spawnParticle(Particle.EXPLOSION, center, 6, 1.2, 0.8, 1.2, 0.02);
		world.spawnParticle(Particle.CLOUD, center, 30, 4.0, 2.0, 4.0, 0.02);

		ChunkBoomeritsPlugin.get().getLogger().info(
				"Deleted " + cleared + " blocks from chunk " + chunkX + ", " + chunkZ
		);
	}

	private static boolean shouldDelete(Block block) {
		Material type = block.getType();
		if (type.isAir()) {
			return false;
		}
		return switch (type) {
			case BEDROCK, BARRIER, STRUCTURE_VOID, STRUCTURE_BLOCK,
					COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
					END_PORTAL, END_PORTAL_FRAME, END_GATEWAY -> false;
			default -> type.getHardness() >= 0.0f;
		};
	}
}
