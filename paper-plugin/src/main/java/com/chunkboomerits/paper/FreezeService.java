package com.chunkboomerits.paper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Freeze players in place and show a "Frozen" hologram above their head.
 */
public final class FreezeService {
	private final ChunkBoomeritsPlugin plugin;
	private final Set<UUID> frozen = new HashSet<>();
	private final java.util.Map<UUID, TextDisplay> holograms = new java.util.HashMap<>();
	private final java.util.Map<UUID, Float> savedWalk = new java.util.HashMap<>();
	private final java.util.Map<UUID, Float> savedFly = new java.util.HashMap<>();
	private int followTask = -1;

	public FreezeService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
	}

	public void start() {
		followTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickHolograms, 1L, 2L);
	}

	public void shutdown() {
		if (followTask != -1) {
			Bukkit.getScheduler().cancelTask(followTask);
			followTask = -1;
		}
		unfreezeAll();
	}

	public boolean isFrozen(UUID uuid) {
		return frozen.contains(uuid);
	}

	public boolean isFrozen(Player player) {
		return player != null && frozen.contains(player.getUniqueId());
	}

	public int frozenCount() {
		return frozen.size();
	}

	/** @return true if newly frozen */
	public boolean freeze(Player player) {
		if (player == null || !player.isOnline()) {
			return false;
		}
		UUID id = player.getUniqueId();
		if (frozen.contains(id)) {
			refreshHologram(player);
			return false;
		}
		frozen.add(id);
		savedWalk.put(id, player.getWalkSpeed());
		savedFly.put(id, player.getFlySpeed());
		player.setWalkSpeed(0f);
		player.setFlySpeed(0f);
		spawnHologram(player);
		player.sendMessage(Component.text("You have been frozen.", NamedTextColor.AQUA));
		return true;
	}

	/** @return true if was frozen and is now unfrozen */
	public boolean unfreeze(Player player) {
		return unfreeze(player, true);
	}

	private boolean unfreeze(Player player, boolean message) {
		if (player == null) {
			return false;
		}
		UUID id = player.getUniqueId();
		if (!frozen.remove(id)) {
			removeHologram(id);
			return false;
		}
		Float walk = savedWalk.remove(id);
		Float fly = savedFly.remove(id);
		if (player.isOnline()) {
			player.setWalkSpeed(walk != null ? walk : 0.2f);
			player.setFlySpeed(fly != null ? fly : 0.1f);
			if (message) {
				player.sendMessage(Component.text("You have been unfrozen.", NamedTextColor.GREEN));
			}
		}
		removeHologram(id);
		return true;
	}

	/** Freeze every online player except {@code except} (may be null). */
	public int freezeAll(Player except) {
		int n = 0;
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (except != null && player.getUniqueId().equals(except.getUniqueId())) {
				continue;
			}
			if (freeze(player)) {
				n++;
			}
		}
		return n;
	}

	public int unfreezeAll() {
		int n = 0;
		for (UUID id : new HashSet<>(frozen)) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				if (unfreeze(player, true)) {
					n++;
				}
			} else {
				removeHologram(id);
				frozen.remove(id);
				savedWalk.remove(id);
				savedFly.remove(id);
			}
		}
		return n;
	}

	public void handleQuit(Player player) {
		removeHologram(player.getUniqueId());
		savedWalk.remove(player.getUniqueId());
		savedFly.remove(player.getUniqueId());
		// Keep UUID in frozen so rejoin mid-session stays frozen
	}

	public void handleJoin(Player player) {
		if (!frozen.contains(player.getUniqueId())) {
			return;
		}
		savedWalk.put(player.getUniqueId(), player.getWalkSpeed());
		savedFly.put(player.getUniqueId(), player.getFlySpeed());
		player.setWalkSpeed(0f);
		player.setFlySpeed(0f);
		spawnHologram(player);
		player.sendMessage(Component.text("You are still frozen.", NamedTextColor.AQUA));
	}

	private void spawnHologram(Player player) {
		removeHologram(player.getUniqueId());
		Location loc = hologramLocation(player);
		TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class, d -> {
			d.text(Component.text("Frozen", NamedTextColor.AQUA, TextDecoration.BOLD));
			d.setBillboard(Display.Billboard.CENTER);
			d.setSeeThrough(true);
			d.setShadowed(true);
			d.setDefaultBackground(false);
			d.setBackgroundColor(Color.fromARGB(120, 10, 30, 60));
			d.setAlignment(TextDisplay.TextAlignment.CENTER);
			d.setViewRange(48f);
			d.setPersistent(false);
			d.setGravity(false);
			d.setInvulnerable(true);
			d.setTransformation(new Transformation(
					new Vector3f(0f, 0.15f, 0f),
					new AxisAngle4f(0f, 0f, 0f, 1f),
					new Vector3f(1.25f, 1.25f, 1.25f),
					new AxisAngle4f(0f, 0f, 0f, 1f)
			));
		});
		holograms.put(player.getUniqueId(), display);
	}

	private void refreshHologram(Player player) {
		TextDisplay display = holograms.get(player.getUniqueId());
		if (display == null || display.isDead() || !display.isValid()) {
			spawnHologram(player);
		}
	}

	private void removeHologram(UUID id) {
		TextDisplay display = holograms.remove(id);
		if (display != null && display.isValid()) {
			display.remove();
		}
	}

	private void tickHolograms() {
		for (UUID id : new HashSet<>(frozen)) {
			Player player = Bukkit.getPlayer(id);
			if (player == null || !player.isOnline()) {
				removeHologram(id);
				continue;
			}
			TextDisplay display = holograms.get(id);
			if (display == null || display.isDead() || !display.isValid()) {
				spawnHologram(player);
				continue;
			}
			Location dest = hologramLocation(player);
			if (display.getWorld() != dest.getWorld()) {
				spawnHologram(player);
				continue;
			}
			display.teleport(dest);
		}
	}

	private static Location hologramLocation(Player player) {
		return player.getLocation().clone().add(0.0, 2.25, 0.0);
	}
}
