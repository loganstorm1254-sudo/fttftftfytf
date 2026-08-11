package com.chunkboomerits.paper.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gives starting balance tracking + shows sidebar on join.
 */
public final class EconomyListener implements Listener {
	private final EconomyService economy;
	private final EconomyScoreboard scoreboard;

	public EconomyListener(EconomyService economy, EconomyScoreboard scoreboard) {
		this.economy = economy;
		this.scoreboard = scoreboard;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		economy.ensurePlayer(event.getPlayer());
		scoreboard.show(event.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		scoreboard.remove(event.getPlayer());
		economy.save();
	}
}
