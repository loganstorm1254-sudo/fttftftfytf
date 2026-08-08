package com.chunkboomerits.paper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/**
 * Fills holes / wall gaps with materials sampled from nearby blocks so patches look natural.
 */
public final class NaturalFiller {
	public enum Mode {
		HOLE,
		WALL
	}

	public record Result(int filled, List<UndoBlock> undo) {
	}

	public record UndoBlock(int x, int y, int z, BlockData previous) {
	}

	private static final BlockFace[] ALL6 = {
			BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
	};

	private NaturalFiller() {
	}

	public static Result fill(Mode mode, Block startAir, BlockFace wallNormal, int maxRadius, int maxBlocks) {
		if (startAir == null || !startAir.getType().isAir()) {
			return new Result(0, List.of());
		}
		World world = startAir.getWorld();
		int sx = startAir.getX();
		int sy = startAir.getY();
		int sz = startAir.getZ();
		Random random = ThreadLocalRandom.current();

		List<Block> airs = mode == Mode.WALL
				? collectWallAir(world, sx, sy, sz, wallNormal, maxRadius, maxBlocks)
				: collectHoleAir(world, sx, sy, sz, maxRadius, maxBlocks);
		if (airs.isEmpty()) {
			return new Result(0, List.of());
		}

		Map<Material, Integer> palette = samplePalette(world, airs);
		if (palette.isEmpty()) {
			palette.put(Material.STONE, 8);
			palette.put(Material.DIRT, 4);
			palette.put(Material.GRASS_BLOCK, 2);
		}

		List<UndoBlock> undo = new ArrayList<>(airs.size());
		int filled = 0;
		for (Block air : airs) {
			Material pick = pickNatural(world, air, palette, random);
			undo.add(new UndoBlock(air.getX(), air.getY(), air.getZ(), air.getBlockData().clone()));
			air.setType(pick, false);
			filled++;
		}

		// Surface pass — make tops look like real ground
		for (Block air : airs) {
			Block block = world.getBlockAt(air.getX(), air.getY(), air.getZ());
			Block above = block.getRelative(BlockFace.UP);
			if (!above.getType().isAir()) {
				continue;
			}
			Material type = block.getType();
			if (type == Material.DIRT || type == Material.STONE || type == Material.COARSE_DIRT
					|| type == Material.GRAVEL || type == Material.ANDESITE || type == Material.DIORITE
					|| type == Material.GRANITE || type == Material.DEEPSLATE) {
				Material surface = surfaceFor(palette);
				if (surface != null) {
					block.setType(surface, false);
				}
			}
		}

		return new Result(filled, undo);
	}

	public static void undo(World world, List<UndoBlock> undo) {
		if (undo == null) {
			return;
		}
		for (int i = undo.size() - 1; i >= 0; i--) {
			UndoBlock u = undo.get(i);
			world.getBlockAt(u.x(), u.y(), u.z()).setBlockData(u.previous(), false);
		}
	}

	private static List<Block> collectHoleAir(World world, int sx, int sy, int sz, int maxRadius, int maxBlocks) {
		List<Block> out = new ArrayList<>();
		Queue<long[]> q = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		long startKey = key(sx, sy, sz);
		q.add(new long[] {sx, sy, sz});
		seen.add(startKey);

		while (!q.isEmpty() && out.size() < maxBlocks) {
			long[] p = q.poll();
			int x = (int) p[0];
			int y = (int) p[1];
			int z = (int) p[2];
			if (distSq(x, y, z, sx, sy, sz) > maxRadius * maxRadius) {
				continue;
			}
			Block block = world.getBlockAt(x, y, z);
			if (!block.getType().isAir()) {
				continue;
			}
			out.add(block);
			for (BlockFace face : ALL6) {
				int nx = x + face.getModX();
				int ny = y + face.getModY();
				int nz = z + face.getModZ();
				long nk = key(nx, ny, nz);
				if (seen.add(nk)) {
					q.add(new long[] {nx, ny, nz});
				}
			}
		}
		return out;
	}

	private static List<Block> collectWallAir(World world, int sx, int sy, int sz, BlockFace normal,
			int maxRadius, int maxBlocks) {
		// Constrain flood mostly to the wall plane (perpendicular to the clicked face)
		BlockFace n = normal == null ? BlockFace.NORTH : normal;
		List<Block> out = new ArrayList<>();
		Queue<long[]> q = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		q.add(new long[] {sx, sy, sz});
		seen.add(key(sx, sy, sz));

		while (!q.isEmpty() && out.size() < maxBlocks) {
			long[] p = q.poll();
			int x = (int) p[0];
			int y = (int) p[1];
			int z = (int) p[2];
			if (Math.abs(x - sx) > maxRadius || Math.abs(y - sy) > maxRadius || Math.abs(z - sz) > maxRadius) {
				continue;
			}
			// Keep the fill thin along the normal (wall thickness)
			int along = Math.abs(n.getModX()) * Math.abs(x - sx)
					+ Math.abs(n.getModZ()) * Math.abs(z - sz)
					+ (n.getModY() != 0 ? Math.abs(y - sy) : 0);
			if (along > 2) {
				continue;
			}
			Block block = world.getBlockAt(x, y, z);
			if (!block.getType().isAir()) {
				continue;
			}
			out.add(block);

			// Prefer plane neighbors (slide along wall + up/down)
			for (BlockFace face : ALL6) {
				// Allow limited movement into the wall thickness
				boolean planeMove = (n.getModX() != 0 && face.getModX() == 0)
						|| (n.getModZ() != 0 && face.getModZ() == 0)
						|| (n.getModY() != 0 && face.getModY() == 0);
				boolean thinNormal = face == n || face == n.getOppositeFace();
				if (!planeMove && !thinNormal) {
					continue;
				}
				int nx = x + face.getModX();
				int ny = y + face.getModY();
				int nz = z + face.getModZ();
				long nk = key(nx, ny, nz);
				if (seen.add(nk)) {
					q.add(new long[] {nx, ny, nz});
				}
			}
		}
		return out;
	}

	private static Map<Material, Integer> samplePalette(World world, List<Block> airs) {
		Map<Material, Integer> counts = new EnumMap<>(Material.class);
		Set<Long> checked = new HashSet<>();
		for (Block air : airs) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -2; dy <= 2; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						int x = air.getX() + dx;
						int y = air.getY() + dy;
						int z = air.getZ() + dz;
						long k = key(x, y, z);
						if (!checked.add(k)) {
							continue;
						}
						Material mat = world.getBlockAt(x, y, z).getType();
						if (isNaturalFill(mat)) {
							counts.merge(mat, 1, Integer::sum);
						}
					}
				}
			}
		}
		return counts;
	}

	private static Material pickNatural(World world, Block air, Map<Material, Integer> palette, Random random) {
		// Local bias: prefer immediate solid neighbors
		Map<Material, Integer> local = new EnumMap<>(Material.class);
		for (BlockFace face : ALL6) {
			Material mat = air.getRelative(face).getType();
			if (isNaturalFill(mat)) {
				local.merge(mat, 3, Integer::sum);
			}
		}
		for (Map.Entry<Material, Integer> e : palette.entrySet()) {
			local.merge(e.getKey(), e.getValue(), Integer::sum);
		}

		// Prefer dirt under grass-like tops, stone deeper
		Block above = air.getRelative(BlockFace.UP);
		Block below = air.getRelative(BlockFace.DOWN);
		if (above.getType().isAir() && !below.getType().isAir()) {
			boost(local, Material.GRASS_BLOCK, 6);
			boost(local, Material.DIRT, 4);
			boost(local, Material.SAND, 2);
		} else if (!above.getType().isAir() && above.getType() == Material.GRASS_BLOCK) {
			boost(local, Material.DIRT, 10);
		} else if (air.getY() < world.getMinHeight() + 40) {
			boost(local, Material.DEEPSLATE, 5);
			boost(local, Material.STONE, 4);
		}

		return weighted(local, random);
	}

	private static Material surfaceFor(Map<Material, Integer> palette) {
		int grass = palette.getOrDefault(Material.GRASS_BLOCK, 0);
		int sand = palette.getOrDefault(Material.SAND, 0) + palette.getOrDefault(Material.RED_SAND, 0);
		int mycelium = palette.getOrDefault(Material.MYCELIUM, 0);
		int podzol = palette.getOrDefault(Material.PODZOL, 0);
		int dirt = palette.getOrDefault(Material.DIRT, 0);
		if (mycelium > grass && mycelium > sand) {
			return Material.MYCELIUM;
		}
		if (podzol > grass && podzol > sand) {
			return Material.PODZOL;
		}
		if (sand > grass + 2) {
			return palette.containsKey(Material.RED_SAND) && palette.get(Material.RED_SAND) >= palette.getOrDefault(Material.SAND, 0)
					? Material.RED_SAND : Material.SAND;
		}
		if (grass > 0 || dirt > 0) {
			return Material.GRASS_BLOCK;
		}
		return null;
	}

	private static void boost(Map<Material, Integer> map, Material mat, int amount) {
		map.merge(mat, amount, Integer::sum);
	}

	private static Material weighted(Map<Material, Integer> weights, Random random) {
		int total = 0;
		for (int w : weights.values()) {
			total += Math.max(0, w);
		}
		if (total <= 0) {
			return Material.STONE;
		}
		int roll = random.nextInt(total);
		int acc = 0;
		for (Map.Entry<Material, Integer> e : weights.entrySet()) {
			acc += Math.max(0, e.getValue());
			if (roll < acc) {
				return e.getKey();
			}
		}
		return Material.STONE;
	}

	static boolean isNaturalFill(Material mat) {
		if (mat == null || mat.isAir() || !mat.isBlock()) {
			return false;
		}
		return switch (mat) {
			case STONE, COBBLESTONE, MOSSY_COBBLESTONE, ANDESITE, DIORITE, GRANITE,
					DEEPSLATE, COBBLED_DEEPSLATE, TUFF, CALCITE,
					DIRT, COARSE_DIRT, ROOTED_DIRT, GRASS_BLOCK, PODZOL, MYCELIUM, DIRT_PATH,
					SAND, RED_SAND, GRAVEL, CLAY,
					NETHERRACK, SOUL_SAND, SOUL_SOIL, BASALT, BLACKSTONE, END_STONE,
					TERRACOTTA, MUD, MUDDY_MANGROVE_ROOTS, PACKED_MUD,
					SNOW_BLOCK, POWDER_SNOW,
					OAK_LOG, BIRCH_LOG, SPRUCE_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
					MANGROVE_LOG, CHERRY_LOG,
					STONE_BRICKS, MOSSY_STONE_BRICKS, CRACKED_STONE_BRICKS, DEEPSLATE_BRICKS,
					COBBLESTONE_WALL, MOSS_BLOCK -> true;
			default -> {
				String n = mat.name();
				yield n.endsWith("_TERRACOTTA") || n.endsWith("_CONCRETE")
						|| n.equals("SMOOTH_STONE") || n.equals("POLISHED_ANDESITE")
						|| n.equals("POLISHED_DIORITE") || n.equals("POLISHED_GRANITE")
						|| n.equals("POLISHED_DEEPSLATE") || n.equals("POLISHED_BLACKSTONE")
						|| n.equals("POLISHED_BASALT");
			}
		};
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	private static int distSq(int x, int y, int z, int sx, int sy, int sz) {
		int dx = x - sx;
		int dy = y - sy;
		int dz = z - sz;
		return dx * dx + dy * dy + dz * dz;
	}
}
