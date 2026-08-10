package com.chunkboomerits.paper.economy;

import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Forces /ah, /shop, /sell, /orders to this plugin even if another plugin stole the names.
 */
public final class MarketCommandIntercept implements Listener {
	private final AuctionCommands auctionCommands;
	private final ShopGui shopGui;
	private final SellCommands sellCommands;
	private final OrdersCommand ordersCommand;

	public MarketCommandIntercept(AuctionCommands auctionCommands, ShopGui shopGui, SellCommands sellCommands,
			OrdersCommand ordersCommand) {
		this.auctionCommands = auctionCommands;
		this.shopGui = shopGui;
		this.sellCommands = sellCommands;
		this.ordersCommand = ordersCommand;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		String raw = event.getMessage().trim();
		if (!raw.startsWith("/")) {
			return;
		}
		String withoutSlash = raw.substring(1).trim();
		String[] parts = withoutSlash.split("\\s+");
		if (parts.length == 0) {
			return;
		}
		String label = parts[0].toLowerCase(Locale.ROOT);
		// strip plugin prefix if present: chunkboomerits:ah
		int colon = label.indexOf(':');
		if (colon >= 0) {
			label = label.substring(colon + 1);
		}

		Player player = event.getPlayer();
		String[] args = new String[Math.max(0, parts.length - 1)];
		System.arraycopy(parts, 1, args, 0, args.length);

		if (label.equals("ah") || label.equals("auction") || label.equals("auctions") || label.equals("cbah")) {
			event.setCancelled(true);
			auctionCommands.handle(player, args);
			return;
		}
		if (label.equals("shop") || label.equals("store") || label.equals("cbshop")) {
			event.setCancelled(true);
			if (!player.hasPermission("chunkboomerits.shop") && !player.isOp()) {
				player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
				return;
			}
			shopGui.open(player);
			return;
		}
		if (label.equals("shopadd") || label.equals("cbshopadd")) {
			event.setCancelled(true);
			ShopAddCommand.handle(player, args);
			return;
		}
		if (label.equals("sell") || label.equals("cbsell")) {
			event.setCancelled(true);
			sellCommands.handle(player, args);
			return;
		}
		if (label.equals("orders") || label.equals("order") || label.equals("cborders") || label.equals("buyorder")) {
			event.setCancelled(true);
			ordersCommand.handle(player, args);
		}
	}
}
