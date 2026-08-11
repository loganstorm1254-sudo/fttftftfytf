package com.chunkboomerits.paper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Random teleport within a chosen block radius from the player.
 */
public final class RtpService {
	private static final int MAX_ATTEMPTS = 28;
	private static final Set<Material> UNSAFE = Set.of(
			Material.LAVA, Material.WATER, Material.KELP, Material.KELP_PLANT,
			Material.SEAGRASS, Material.TALL_SEAGRASS, Material.MAGMA_BLOCK,
			Material.CACTUS, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW,
			Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE
	);

	private final ChunkBoomeritsPlugin plugin;
	private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
	private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

	public RtpService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
	}

	public int cooldownSeconds() {
		return Math.max(0, plugin.getConfig().getInt("rtp.cooldown-seconds", 15));
	}

	public long cooldownRemainingMs(Player player) {
		Long until = cooldownUntil.get(player.getUniqueId());
		if (until == null) {
			return 0;
		}
		return Math.max(0, until - System.currentTimeMillis());
	}

	public boolean isBusy(Player player) {
		return busy.contains(player.getUniqueId());
	}

	/**
	 * Teleport {@code player} a random distance up to {@code radius} blocks away.
	 */
	public void teleport(Player player, int radius) {
		if (radius < 50) {
			player.sendMessage(Component.text("Distance too small.", NamedTextColor.RED));
			return;
		}
		if (isBusy(player)) {
			player.sendMessage(Component.text("Already searching for a spot…", NamedTextColor.YELLOW));
			return;
		}
		long remaining = cooldownRemainingMs(player);
		if (remaining > 0 && !player.isOp()) {
			player.sendMessage(Component.text(
					"RTP cooldown: " + ((remaining + 999) / 1000) + "s left.",
					NamedTextColor.RED
			));
			return;
		}

		World world = player.getWorld();
		if (world.getEnvironment() != World.Environment.NORMAL) {
			player.sendMessage(Component.text("RTP only works in the Overworld.", NamedTextColor.RED));
			return;
		}

		busy.add(player.getUniqueId());
		player.sendMessage(Component.text(
				"Searching up to " + radius + " blocks away…",
				NamedTextColor.AQUA
		));

		Location origin = player.getLocation().clone();
		tryFind(player, world, origin, radius, 0);
	}

	private void tryFind(Player player, World world, Location origin, int radius, int attempt) {
		if (!player.isOnline()) {
			busy.remove(player.getUniqueId());
			return;
		}
		if (attempt >= MAX_ATTEMPTS) {
			busy.remove(player.getUniqueId());
			player.sendMessage(Component.text("Couldn't find a safe spot. Try again.", NamedTextColor.RED));
			return;
		}

		ThreadLocalRandom rng = ThreadLocalRandom.current();
		// Prefer going most of the chosen distance away (60%–100% of radius)
		double dist = radius * (0.60 + rng.nextDouble() * 0.40);
		double angle = rng.nextDouble() * Math.PI * 2;
		int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
		int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

		world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
				Bukkit.getScheduler().runTask(plugin, () -> {
					if (!player.isOnline()) {
						busy.remove(player.getUniqueId());
						return;
					}
					Location safe = findSafe(world, x, z);
					if (safe == null) {
						tryFind(player, world, origin, radius, attempt + 1);
						return;
					}
					busy.remove(player.getUniqueId());
					cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds() * 1000L);
					safe.setYaw(player.getLocation().getYaw());
					safe.setPitch(player.getLocation().getPitch());
					player.teleportAsync(safe).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
						if (Boolean.TRUE.equals(ok)) {
							player.sendMessage(Component.text(
									"Teleported ~" + ((int) origin.distance(safe)) + " blocks away.",
									NamedTextColor.GREEN
							));
							player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.1f);
						} else {
							player.sendMessage(Component.text("Teleport failed. Try again.", NamedTextColor.RED));
						}
					}));
				})
		);
	}

	private static Location findSafe(World world, int x, int z) {
		Block highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
		int y = highest.getY();
		if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) {
			return null;
		}

		Block ground = world.getBlockAt(x, y, z);
		Block feet = world.getBlockAt(x, y + 1, z);
		Block head = world.getBlockAt(x, y + 2, z);

		if (!isSafeGround(ground) || !feet.getType().isAir() || !head.getType().isAir()) {
			// Scan a short column downward for a ledge
			for (int dy = 0; dy < 12; dy++) {
				int gy = y - dy;
				if (gy <= world.getMinHeight() + 1) {
					break;
				}
				ground = world.getBlockAt(x, gy, z);
				feet = world.getBlockAt(x, gy + 1, z);
				head = world.getBlockAt(x, gy + 2, z);
				if (isSafeGround(ground) && feet.getType().isAir() && head.getType().isAir()) {
					return new Location(world, x + 0.5, gy + 1.0, z + 0.5);
				}
			}
			return null;
		}
		return new Location(world, x + 0.5, y + 1.0, z + 0.5);
	}

	private static boolean isSafeGround(Block ground) {
		Material type = ground.getType();
		if (type.isAir() || !type.isSolid()) {
			return false;
		}
		if (UNSAFE.contains(type)) {
			return false;
		}
		if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) {
			return false;
		}
		String name = type.name();
		return !name.contains("LEAVE") && !name.equals("BEDROCK");
	}
}
