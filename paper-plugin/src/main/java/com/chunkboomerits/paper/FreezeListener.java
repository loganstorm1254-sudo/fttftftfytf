package com.chunkboomerits.paper;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Blocks movement / actions for frozen players.
 */
public final class FreezeListener implements Listener {
	private final FreezeService freeze;

	public FreezeListener(FreezeService freeze) {
		this.freeze = freeze;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		if (!freeze.isFrozen(event.getPlayer())) {
			return;
		}
		Location from = event.getFrom();
		Location to = event.getTo();
		if (to == null) {
			return;
		}
		// Allow looking around; block position changes
		if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
			Location stay = from.clone();
			stay.setYaw(to.getYaw());
			stay.setPitch(to.getPitch());
			event.setTo(stay);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		if (!freeze.isFrozen(event.getPlayer())) {
			return;
		}
		// Allow plugin/plugin-internal? Block player-driven teleports including RTP/ender pearl
		if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
				|| event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
			// Still block — frozen means frozen
		}
		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent event) {
		if (freeze.isFrozen(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (freeze.isFrozen(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (freeze.isFrozen(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		if (freeze.isFrozen(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onAttack(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof Player player && freeze.isFrozen(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		freeze.handleQuit(event.getPlayer());
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		freeze.handleJoin(event.getPlayer());
	}
}
