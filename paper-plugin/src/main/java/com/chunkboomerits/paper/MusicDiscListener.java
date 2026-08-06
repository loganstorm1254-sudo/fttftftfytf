package com.chunkboomerits.paper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Plays Despacito from the custom disc in a jukebox (needs the resource pack).
 */
public final class MusicDiscListener implements Listener {
	private final ChunkBoomeritsPlugin plugin;
	private final Map<String, Playing> playing = new HashMap<>();

	public MusicDiscListener(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onJukebox(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		if (event.getClickedBlock() == null) {
			return;
		}
		Block block = event.getClickedBlock();
		if (block.getType() != Material.JUKEBOX) {
			return;
		}
		if (!(block.getState() instanceof Jukebox jukebox)) {
			return;
		}

		Player player = event.getPlayer();
		ItemStack hand = player.getInventory().getItemInMainHand();
		String key = locKey(block.getLocation());

		// Eject / stop if already playing our disc
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK && jukebox.isPlaying() && playing.containsKey(key)) {
			event.setCancelled(true);
			stopPlaying(key, true);
			return;
		}

		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		if (!OpItems.isDespacitoDisc(hand)) {
			return;
		}
		if (jukebox.getRecord() != null && jukebox.getRecord().getType() != Material.AIR) {
			return;
		}

		event.setCancelled(true);

		ItemStack disc = hand.clone();
		disc.setAmount(1);
		if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
			hand.setAmount(hand.getAmount() - 1);
		}

		jukebox.setRecord(disc);
		jukebox.update();

		startPlaying(block.getLocation(), player);
		player.sendMessage(Component.text("Now playing: Luis Fonsi - Despacito ft. Daddy Yankee", NamedTextColor.AQUA));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (event.getBlock().getType() != Material.JUKEBOX) {
			return;
		}
		stopPlaying(locKey(event.getBlock().getLocation()), false);
	}

	private void startPlaying(Location loc, Player starter) {
		String key = locKey(loc);
		stopPlaying(key, false);

		Location soundAt = loc.clone().add(0.5, 0.5, 0.5);
		for (Player nearby : loc.getWorld().getPlayers()) {
			if (nearby.getLocation().distanceSquared(soundAt) <= 64 * 64) {
				nearby.playSound(soundAt, OpItems.DESPACITO_SOUND, SoundCategory.RECORDS, 4.0f, 1.0f);
			}
		}

		BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> stopPlaying(key, false), OpItems.DESPACITO_LENGTH_TICKS);
		playing.put(key, new Playing(soundAt, task, starter.getUniqueId()));
	}

	private void stopPlaying(String key, boolean eject) {
		Playing current = playing.remove(key);
		if (current == null) {
			return;
		}
		current.task.cancel();

		for (Player nearby : current.location.getWorld().getPlayers()) {
			nearby.stopSound(OpItems.DESPACITO_SOUND, SoundCategory.RECORDS);
		}

		if (eject) {
			Block block = current.location.getBlock();
			if (block.getState() instanceof Jukebox jukebox) {
				ItemStack record = jukebox.getRecord();
				jukebox.setRecord(null);
				jukebox.update();
				if (record != null && record.getType() != Material.AIR) {
					block.getWorld().dropItemNaturally(current.location, record);
				}
			}
		}
	}

	private static String locKey(Location loc) {
		return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
	}

	private record Playing(Location location, BukkitTask task, UUID starter) {
	}
}
