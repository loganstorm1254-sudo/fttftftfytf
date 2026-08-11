package com.chunkboomerits.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.papermc.paper.datacomponent.DataComponentTypes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
 * Plays custom discs in a jukebox (needs the resource pack).
 * Silences the vanilla base-disc song so it does not stack with the custom track.
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
		CustomDisc handDisc = CustomDisc.fromItem(hand);
		CustomDisc insideDisc = CustomDisc.fromItem(jukebox.getRecord());

		// Eject / stop if our disc is in the jukebox (or we are tracking playback)
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK
				&& (playing.containsKey(key) || insideDisc != null)
				&& handDisc == null) {
			event.setCancelled(true);
			ejectDisc(block, key);
			return;
		}

		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		if (handDisc == null) {
			return;
		}
		if (jukebox.hasRecord()) {
			return;
		}

		event.setCancelled(true);

		ItemStack disc = hand.clone();
		disc.setAmount(1);
		disc.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);

		if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
			hand.setAmount(hand.getAmount() - 1);
		}

		jukebox.setRecord(disc);
		jukebox.update();
		if (jukebox.isPlaying()) {
			jukebox.stopPlaying();
		}

		Location soundAt = block.getLocation().clone().add(0.5, 0.5, 0.5);
		silenceVanilla(soundAt, handDisc.vanillaSound());

		CustomDisc toPlay = handDisc;
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!(block.getState() instanceof Jukebox jb)) {
				return;
			}
			if (jb.isPlaying()) {
				jb.stopPlaying();
			}
			silenceVanilla(soundAt, toPlay.vanillaSound());
			startPlaying(block.getLocation(), player, toPlay);
		});

		player.sendMessage(Component.text("Now playing: " + toPlay.nowPlaying(), NamedTextColor.AQUA));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (event.getBlock().getType() != Material.JUKEBOX) {
			return;
		}
		String key = locKey(event.getBlock().getLocation());
		Playing current = playing.remove(key);
		if (current != null) {
			current.task.cancel();
			silenceCustom(current.location, current.disc);
		}
	}

	private void startPlaying(Location loc, Player starter, CustomDisc disc) {
		String key = locKey(loc);
		stopSoundOnly(key);

		Location soundAt = loc.clone().add(0.5, 0.5, 0.5);
		silenceVanilla(soundAt, disc.vanillaSound());

		for (Player nearby : loc.getWorld().getPlayers()) {
			if (nearby.getLocation().distanceSquared(soundAt) <= 64 * 64) {
				nearby.playSound(soundAt, disc.soundKey(), SoundCategory.RECORDS, 4.0f, 1.0f);
			}
		}

		BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> stopSoundOnly(key), disc.lengthTicks());
		playing.put(key, new Playing(soundAt, task, starter.getUniqueId(), disc));
	}

	private void ejectDisc(Block block, String key) {
		stopSoundOnly(key);

		if (!(block.getState() instanceof Jukebox jukebox)) {
			return;
		}
		ItemStack record = jukebox.getRecord();
		jukebox.stopPlaying();
		jukebox.setRecord(null);
		jukebox.update();
		if (record != null && record.getType() != Material.AIR) {
			block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 1.0, 0.5), record);
		}
	}

	private void stopSoundOnly(String key) {
		Playing current = playing.remove(key);
		if (current == null) {
			return;
		}
		current.task.cancel();
		silenceCustom(current.location, current.disc);
		silenceVanilla(current.location, current.disc.vanillaSound());
	}

	private static void silenceCustom(Location soundAt, CustomDisc disc) {
		for (Player nearby : soundAt.getWorld().getPlayers()) {
			nearby.stopSound(disc.soundKey(), SoundCategory.RECORDS);
		}
	}

	private static void silenceVanilla(Location soundAt, Sound vanilla) {
		for (Player nearby : soundAt.getWorld().getPlayers()) {
			if (nearby.getLocation().distanceSquared(soundAt) <= 64 * 64) {
				nearby.stopSound(vanilla, SoundCategory.RECORDS);
				nearby.stopSound(vanilla);
			}
		}
	}

	private static String locKey(Location loc) {
		return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
	}

	private record Playing(Location location, BukkitTask task, UUID starter, CustomDisc disc) {
	}
}
