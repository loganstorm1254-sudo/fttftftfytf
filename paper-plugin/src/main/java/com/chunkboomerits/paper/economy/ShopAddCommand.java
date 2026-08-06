package com.chunkboomerits.paper.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * OP: /shopadd &lt;price&gt; while holding an item — no chat prompt needed.
 */
public final class ShopAddCommand implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
			return true;
		}
		return handle(player, args);
	}

	public static boolean handle(Player player, String[] args) {
		if (!player.isOp() && !player.hasPermission("chunkboomerits.scoreboard") && !player.hasPermission("chunkboomerits.*")) {
			player.sendMessage(Component.text("OP only.", NamedTextColor.RED));
			return true;
		}
		if (args.length < 1) {
			player.sendMessage(Component.text("Usage: /shopadd <price>  (hold the item first, e.g. a totem)", NamedTextColor.YELLOW));
			return true;
		}
		double price;
		try {
			price = EconomyService.parseAmount(args[0]);
		} catch (NumberFormatException ex) {
			player.sendMessage(Component.text("Invalid price.", NamedTextColor.RED));
			return true;
		}
		if (price <= 0) {
			player.sendMessage(Component.text("Price must be positive.", NamedTextColor.RED));
			return true;
		}
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand.getType().isAir()) {
			player.sendMessage(Component.text("Hold the item to add (e.g. Totem of Undying).", NamedTextColor.RED));
			return true;
		}
		ShopService shop = ChunkBoomeritsPlugin.get().shop();
		EconomyService economy = ChunkBoomeritsPlugin.get().economy();
		ShopService.Offer offer = shop.add(price, hand.clone());
		player.sendMessage(Component.text(
				"Added " + hand.getAmount() + "x " + hand.getType().name() + " to /shop for " + economy.format(price)
						+ " (#" + offer.id() + ")",
				NamedTextColor.GREEN
		));
		return true;
	}
}
