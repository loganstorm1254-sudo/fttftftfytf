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
 * Conservative gap filler — only fills air that is a real pocket/gap in existing terrain.
 * Never expands into open sky or along cliff faces (no mountains / fake cliffs).
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
	private static final BlockFace[] HORIZONTAL = {
			BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
	};

	private NaturalFiller() {
	}

	public static Result fill(Mode mode, Block startAir, BlockFace wallNormal, int maxRadius, int maxBlocks) {
		if (startAir == null || !startAir.getType().isAir()) {
			return new Result(0, List.of());
		}
		// Must already look like a gap at the click, or we refuse (stops cliff/mountain builds)
		if (!isFillableGap(startAir.getWorld(), startAir.getX(), startAir.getY(), startAir.getZ(), mode, wallNormal)) {
			return new Result(0, List.of());
		}

		World world = startAir.getWorld();
		int sx = startAir.getX();
		int sy = startAir.getY();
		int sz = startAir.getZ();
		Random random = ThreadLocalRandom.current();

		List<Block> airs = collectGaps(world, sx, sy, sz, mode, wallNormal, maxRadius, maxBlocks);
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
			Material pick = pickNatural(world, air, palette, random, mode);
			undo.add(new UndoBlock(air.getX(), air.getY(), air.getZ(), air.getBlockData().clone()));
			air.setType(pick, false);
			filled++;
		}

		// Only retouch tops that are still exposed and were part of this tiny fill
		for (Block air : airs) {
			Block block = world.getBlockAt(air.getX(), air.getY(), air.getZ());
			Block above = block.getRelative(BlockFace.UP);
			if (!above.getType().isAir()) {
				continue;
			}
			// Need solid beside the top so we don't grow a mound
			if (countSolidHorizontal(world, block.getX(), block.getY(), block.getZ()) < 2) {
				continue;
			}
			Material type = block.getType();
			if (type == Material.DIRT || type == Material.STONE || type == Material.COARSE_DIRT
					|| type == Material.GRAVEL || type == Material.COBBLESTONE) {
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

	private static List<Block> collectGaps(World world, int sx, int sy, int sz, Mode mode,
			BlockFace wallNormal, int maxRadius, int maxBlocks) {
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

			Block block = world.getBlockAt(x, y, z);
			if (!block.getType().isAir()) {
				continue;
			}
			if (!isFillableGap(world, x, y, z, mode, wallNormal)) {
				continue;
			}

			out.add(block);

			// Expand only into other gap air — never into open space
			for (BlockFace face : ALL6) {
				int nx = x + face.getModX();
				int ny = y + face.getModY();
				int nz = z + face.getModZ();
				if (Math.abs(nx - sx) > maxRadius || Math.abs(ny - sy) > maxRadius || Math.abs(nz - sz) > maxRadius) {
					continue;
				}
				long nk = key(nx, ny, nz);
				if (!seen.add(nk)) {
					continue;
				}
				if (!world.getBlockAt(nx, ny, nz).getType().isAir()) {
					continue;
				}
				if (!isFillableGap(world, nx, ny, nz, mode, wallNormal)) {
					continue;
				}
				q.add(new long[] {nx, ny, nz});
			}
		}
		return out;
	}

	/**
	 * HOLE: air pocket with enough solid around it (crevice / dug hole / cave bite).
	 * WALL: missing block in a wall — solids on opposite sides in the wall plane.
	 */
	private static boolean isFillableGap(World world, int x, int y, int z, Mode mode, BlockFace wallNormal) {
		if (mode == Mode.WALL) {
			return isWallGap(world, x, y, z, wallNormal);
		}
		return isHoleGap(world, x, y, z);
	}

	private static boolean isHoleGap(World world, int x, int y, int z) {
		int solid = countSolidNeighbors(world, x, y, z);
		// Open air / cliff face / sky → 0–2 solids. Real pockets → 3+.
		if (solid < 3) {
			return false;
		}
		// Reject "against a cliff looking at open world" (solid only on one side cluster)
		int horiz = countSolidHorizontal(world, x, y, z);
		boolean covered = isSolid(world, x, y + 1, z);
		boolean floored = isSolid(world, x, y - 1, z);
		// Must be a pocket: covered OR (floored and hugged by walls)
		if (covered) {
			return solid >= 3;
		}
		if (floored && horiz >= 2) {
			return true;
		}
		// Deep corner in a dig: 3+ solids including walls
		return horiz >= 3;
	}

	private static boolean isWallGap(World world, int x, int y, int z, BlockFace wallNormal) {
		// Prefer opposite solids along the wall's plane (classic missing brick)
		if (wallNormal == BlockFace.EAST || wallNormal == BlockFace.WEST) {
			// Wall faces E/W → wall runs N/S, gap closed by N+S and/or U+D
			if (isSolid(world, x, y, z - 1) && isSolid(world, x, y, z + 1)) {
				return true;
			}
		} else if (wallNormal == BlockFace.NORTH || wallNormal == BlockFace.SOUTH) {
			if (isSolid(world, x - 1, y, z) && isSolid(world, x + 1, y, z)) {
				return true;
			}
		}

		// Any opposite pair (works when face is unknown)
		if (isSolid(world, x - 1, y, z) && isSolid(world, x + 1, y, z)) {
			return true;
		}
		if (isSolid(world, x, y, z - 1) && isSolid(world, x, y, z + 1)) {
			return true;
		}
		if (isSolid(world, x, y - 1, z) && isSolid(world, x, y + 1, z)) {
			// Vertical hole in wall — still need a back/side so we don't fill open air pillars
			return countSolidHorizontal(world, x, y, z) >= 1;
		}

		// Tight crack: 4+ solids, and not a free cliff ledge (must not be open on 3+ horizontal sides)
		int solid = countSolidNeighbors(world, x, y, z);
		int horiz = countSolidHorizontal(world, x, y, z);
		return solid >= 4 && horiz >= 2;
	}

	private static boolean isSolid(World world, int x, int y, int z) {
		if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
			return false;
		}
		Material mat = world.getBlockAt(x, y, z).getType();
		return !mat.isAir() && mat.isSolid();
	}

	private static int countSolidNeighbors(World world, int x, int y, int z) {
		int c = 0;
		for (BlockFace face : ALL6) {
			if (isSolid(world, x + face.getModX(), y + face.getModY(), z + face.getModZ())) {
				c++;
			}
		}
		return c;
	}

	private static int countSolidHorizontal(World world, int x, int y, int z) {
		int c = 0;
		for (BlockFace face : HORIZONTAL) {
			if (isSolid(world, x + face.getModX(), y + face.getModY(), z + face.getModZ())) {
				c++;
			}
		}
		return c;
	}

	private static Map<Material, Integer> samplePalette(World world, List<Block> airs) {
		Map<Material, Integer> counts = new EnumMap<>(Material.class);
		for (Block air : airs) {
			for (BlockFace face : ALL6) {
				Material mat = air.getRelative(face).getType();
				if (isNaturalFill(mat)) {
					counts.merge(mat, 3, Integer::sum);
				}
			}
			// Slightly wider sample from immediate rim only
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						Material mat = world.getBlockAt(air.getX() + dx, air.getY() + dy, air.getZ() + dz).getType();
						if (isNaturalFill(mat)) {
							counts.merge(mat, 1, Integer::sum);
						}
					}
				}
			}
		}
		return counts;
	}

	private static Material pickNatural(World world, Block air, Map<Material, Integer> palette,
			Random random, Mode mode) {
		Map<Material, Integer> local = new EnumMap<>(Material.class);
		for (BlockFace face : ALL6) {
			Material mat = air.getRelative(face).getType();
			if (isNaturalFill(mat)) {
				local.merge(mat, 6, Integer::sum);
			}
		}
		for (Map.Entry<Material, Integer> e : palette.entrySet()) {
			local.merge(e.getKey(), e.getValue(), Integer::sum);
		}

		// Match the dominant neighbor — keeps walls looking like the wall, not random dirt mountains
		Material dominant = dominantNeighbor(world, air);
		if (dominant != null) {
			boost(local, dominant, 20);
		}

		if (mode == Mode.HOLE) {
			Block above = air.getRelative(BlockFace.UP);
			Block below = air.getRelative(BlockFace.DOWN);
			if (above.getType().isAir() && !below.getType().isAir()) {
				boost(local, Material.GRASS_BLOCK, 4);
				boost(local, Material.DIRT, 4);
			} else if (!above.getType().isAir() && above.getType() == Material.GRASS_BLOCK) {
				boost(local, Material.DIRT, 10);
			}
		}

		return weighted(local, random);
	}

	private static Material dominantNeighbor(World world, Block air) {
		Map<Material, Integer> counts = new EnumMap<>(Material.class);
		for (BlockFace face : ALL6) {
			Material mat = air.getRelative(face).getType();
			if (isNaturalFill(mat)) {
				counts.merge(mat, 1, Integer::sum);
			}
		}
		Material best = null;
		int bestN = 0;
		for (Map.Entry<Material, Integer> e : counts.entrySet()) {
			if (e.getValue() > bestN) {
				bestN = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	private static Material surfaceFor(Map<Material, Integer> palette) {
		int grass = palette.getOrDefault(Material.GRASS_BLOCK, 0);
		int sand = palette.getOrDefault(Material.SAND, 0) + palette.getOrDefault(Material.RED_SAND, 0);
		int dirt = palette.getOrDefault(Material.DIRT, 0);
		if (sand > grass + 2) {
			return palette.getOrDefault(Material.RED_SAND, 0) >= palette.getOrDefault(Material.SAND, 0)
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
					SNOW_BLOCK,
					STONE_BRICKS, MOSSY_STONE_BRICKS, CRACKED_STONE_BRICKS, DEEPSLATE_BRICKS,
					MOSS_BLOCK -> true;
			default -> {
				String n = mat.name();
				yield n.endsWith("_TERRACOTTA")
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
}
