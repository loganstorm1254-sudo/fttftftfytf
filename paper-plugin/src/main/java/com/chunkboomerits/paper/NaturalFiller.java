package com.chunkboomerits.paper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
 * Fills only the cavity of a hole / wall gap — never open air or sky.
 * Hole mode finds the surrounding rim height and fills air below it.
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

		Map<Material, Integer> palette = samplePalette(world, airs, sx, sy, sz, maxRadius);
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

		// Top of the filled hole → grass/sand/etc. matching surroundings
		for (Block air : airs) {
			Block block = world.getBlockAt(air.getX(), air.getY(), air.getZ());
			Block above = block.getRelative(BlockFace.UP);
			if (!above.getType().isAir()) {
				continue;
			}
			Material type = block.getType();
			if (type == Material.DIRT || type == Material.STONE || type == Material.COARSE_DIRT
					|| type == Material.GRAVEL || type == Material.ANDESITE || type == Material.DIORITE
					|| type == Material.GRANITE || type == Material.DEEPSLATE || type == Material.COBBLESTONE) {
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

	/**
	 * Only air inside the pit at/below the surrounding ground rim.
	 * Open sky above the rim is never filled (that was building the pyramid).
	 */
	private static List<Block> collectHoleAir(World world, int sx, int sy, int sz, int maxRadius, int maxBlocks) {
		int rimY = estimateRimY(world, sx, sy, sz, maxRadius);
		// Outside the pit, ground is solid at rimY — so air with y <= rimY is the cavity only.
		int ceiling = rimY;
		int floor = Math.max(world.getMinHeight(), Math.min(sy, rimY) - Math.min(40, maxRadius * 3));

		// Click must be inside the hole (at or below rim)
		if (sy > ceiling) {
			return List.of();
		}

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

			int hDistSq = (x - sx) * (x - sx) + (z - sz) * (z - sz);
			if (hDistSq > maxRadius * maxRadius) {
				continue;
			}
			if (y < floor || y > ceiling) {
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
				if (ny < floor || ny > ceiling) {
					continue;
				}
				int nh = (nx - sx) * (nx - sx) + (nz - sz) * (nz - sz);
				if (nh > maxRadius * maxRadius) {
					continue;
				}
				long nk = key(nx, ny, nz);
				if (seen.add(nk)) {
					q.add(new long[] {nx, ny, nz});
				}
			}
		}
		return out;
	}

	/**
	 * Rim = typical ground height around the hole (median of ring samples), never the pit floor.
	 */
	private static int estimateRimY(World world, int sx, int sy, int sz, int maxRadius) {
		List<Integer> heights = new ArrayList<>();
		int scanTop = Math.min(world.getMaxHeight() - 1, sy + 32);
		int scanBot = Math.max(world.getMinHeight(), sy - 8);

		for (int r = Math.max(2, maxRadius / 3); r <= maxRadius; r++) {
			for (int i = 0; i < 16; i++) {
				double ang = (Math.PI * 2 * i) / 16.0;
				int x = sx + (int) Math.round(Math.cos(ang) * r);
				int z = sz + (int) Math.round(Math.sin(ang) * r);
				int surface = topSolidY(world, x, z, scanTop, scanBot);
				if (surface != Integer.MIN_VALUE && surface >= sy - 1) {
					heights.add(surface);
				}
			}
		}

		// Also sample a square ring
		for (int dx = -maxRadius; dx <= maxRadius; dx++) {
			for (int dz = -maxRadius; dz <= maxRadius; dz++) {
				int adx = Math.abs(dx);
				int adz = Math.abs(dz);
				boolean onRing = (adx == maxRadius || adz == maxRadius)
						|| (adx == maxRadius - 1 && adz >= maxRadius / 2)
						|| (adz == maxRadius - 1 && adx >= maxRadius / 2);
				if (!onRing) {
					continue;
				}
				int surface = topSolidY(world, sx + dx, sz + dz, scanTop, scanBot);
				if (surface != Integer.MIN_VALUE && surface >= sy - 1) {
					heights.add(surface);
				}
			}
		}

		if (heights.isEmpty()) {
			// Fallback: solid neighbors above/beside the click
			for (BlockFace face : HORIZONTAL) {
				Block n = world.getBlockAt(sx + face.getModX(), sy, sz + face.getModZ());
				if (!n.getType().isAir()) {
					heights.add(n.getY());
				}
				Block up = world.getBlockAt(sx + face.getModX(), sy + 1, sz + face.getModZ());
				if (!up.getType().isAir()) {
					heights.add(up.getY());
				}
			}
		}

		if (heights.isEmpty()) {
			return sy;
		}

		Collections.sort(heights);
		// Median of surrounding ground — stable rim height
		int median = heights.get(heights.size() / 2);
		// Don't use a rim far above the click (avoids filling weird tall areas)
		return Math.min(median, sy + Math.max(2, maxRadius / 2));
	}

	/** Highest solid block in column, scanning down from top. */
	private static int topSolidY(World world, int x, int z, int fromY, int toY) {
		for (int y = fromY; y >= toY; y--) {
			Material mat = world.getBlockAt(x, y, z).getType();
			if (!mat.isAir() && mat.isSolid()) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static List<Block> collectWallAir(World world, int sx, int sy, int sz, BlockFace normal,
			int maxRadius, int maxBlocks) {
		BlockFace n = normal == null ? BlockFace.NORTH : normal;
		if (n == BlockFace.UP || n == BlockFace.DOWN) {
			n = BlockFace.NORTH;
		}

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

			int along = Math.abs(n.getModX()) * Math.abs(x - sx)
					+ Math.abs(n.getModZ()) * Math.abs(z - sz);
			if (along > 1) {
				continue;
			}

			Block block = world.getBlockAt(x, y, z);
			if (!block.getType().isAir()) {
				continue;
			}

			// Must be a gap in a wall: solid on at least one side in the wall plane neighborhood
			if (countSolidNeighbors(world, x, y, z) < 2) {
				continue;
			}

			out.add(block);

			for (BlockFace face : ALL6) {
				boolean planeMove = (n.getModX() != 0 && face.getModX() == 0)
						|| (n.getModZ() != 0 && face.getModZ() == 0);
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

	private static int countSolidNeighbors(World world, int x, int y, int z) {
		int c = 0;
		for (BlockFace face : ALL6) {
			Material mat = world.getBlockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ()).getType();
			if (!mat.isAir() && mat.isSolid()) {
				c++;
			}
		}
		return c;
	}

	private static Map<Material, Integer> samplePalette(World world, List<Block> airs,
			int sx, int sy, int sz, int maxRadius) {
		Map<Material, Integer> counts = new EnumMap<>(Material.class);
		// Prefer sampling the rim / walls around the hole, not distant open terrain
		for (int dx = -maxRadius; dx <= maxRadius; dx++) {
			for (int dy = -2; dy <= 4; dy++) {
				for (int dz = -maxRadius; dz <= maxRadius; dz++) {
					if (dx * dx + dz * dz > maxRadius * maxRadius) {
						continue;
					}
					Material mat = world.getBlockAt(sx + dx, sy + dy, sz + dz).getType();
					if (isNaturalFill(mat)) {
						counts.merge(mat, 1, Integer::sum);
					}
				}
			}
		}
		for (Block air : airs) {
			for (BlockFace face : ALL6) {
				Material mat = air.getRelative(face).getType();
				if (isNaturalFill(mat)) {
					counts.merge(mat, 2, Integer::sum);
				}
			}
		}
		return counts;
	}

	private static Material pickNatural(World world, Block air, Map<Material, Integer> palette, Random random) {
		Map<Material, Integer> local = new EnumMap<>(Material.class);
		for (BlockFace face : ALL6) {
			Material mat = air.getRelative(face).getType();
			if (isNaturalFill(mat)) {
				local.merge(mat, 4, Integer::sum);
			}
		}
		for (Map.Entry<Material, Integer> e : palette.entrySet()) {
			local.merge(e.getKey(), e.getValue(), Integer::sum);
		}

		Block above = air.getRelative(BlockFace.UP);
		Block below = air.getRelative(BlockFace.DOWN);
		if (above.getType().isAir() && !below.getType().isAir()) {
			boost(local, Material.GRASS_BLOCK, 8);
			boost(local, Material.DIRT, 5);
			boost(local, Material.SAND, 2);
		} else if (!above.getType().isAir() && above.getType() == Material.GRASS_BLOCK) {
			boost(local, Material.DIRT, 12);
		} else if (air.getY() < world.getMinHeight() + 40) {
			boost(local, Material.DEEPSLATE, 5);
			boost(local, Material.STONE, 4);
		} else {
			boost(local, Material.STONE, 2);
			boost(local, Material.DIRT, 2);
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
}
