package com.chunkboomerits.paper.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /orders — open the buy-order market.
 */
public final class OrdersCommand implements CommandExecutor {
	private final OrdersGui gui;

	public OrdersCommand(OrdersGui gui) {
		this.gui = gui;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
			return true;
		}
		return handle(player, args);
	}

	public boolean handle(Player player, String[] args) {
		if (!player.hasPermission("chunkboomerits.orders") && !player.isOp()) {
			player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
			return true;
		}
		if (args.length > 0 && (args[0].equalsIgnoreCase("mine") || args[0].equalsIgnoreCase("my"))) {
			gui.openMine(player);
			return true;
		}
		if (args.length > 0 && args[0].equalsIgnoreCase("create")) {
			gui.openBrowse(player);
			player.sendMessage(Component.text(
					"Hold an item and click Create buy order in the menu.",
					NamedTextColor.YELLOW
			));
			return true;
		}
		gui.openBrowse(player);
		return true;
	}
}
