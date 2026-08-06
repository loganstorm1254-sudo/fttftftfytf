package com.chunkboomerits.paper.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.chunkboomerits.paper.OpItems;

/**
 * OP shovel opens the admin hub (scoreboard + shop).
 */
public final class ScoreboardShovelListener implements Listener {
	private final AdminHubGui hub;

	public ScoreboardShovelListener(AdminHubGui hub) {
		this.hub = hub;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		Player player = event.getPlayer();
		ItemStack item = player.getInventory().getItemInMainHand();
		if (!OpItems.isScoreboardShovel(item)) {
			return;
		}

		event.setCancelled(true);

		if (!player.isOp() && !player.hasPermission("chunkboomerits.scoreboard") && !player.hasPermission("chunkboomerits.*")) {
			player.sendMessage(Component.text("Admin shovel is OP-only.", NamedTextColor.RED));
			return;
		}

		hub.open(player);
	}
}
