package com.chunkboomerits.paper.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /shop — open the admin-configured shop.
 */
public final class ShopCommands implements CommandExecutor {
	private final ShopGui gui;

	public ShopCommands(ShopGui gui) {
		this.gui = gui;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
			return true;
		}
		if (!player.hasPermission("chunkboomerits.shop") && !player.isOp()) {
			player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
			return true;
		}
		gui.open(player);
		return true;
	}
}
