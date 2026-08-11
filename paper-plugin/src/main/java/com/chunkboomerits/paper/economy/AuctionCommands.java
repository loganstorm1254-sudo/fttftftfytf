package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /ah — open GUI; /ah sell &lt;price&gt; — list held item.
 */
public final class AuctionCommands implements CommandExecutor, TabCompleter {
	private static final int MAX_LISTINGS_PER_PLAYER = 21;

	private final AuctionService auctions;
	private final AuctionGui gui;
	private final EconomyService economy;

	public AuctionCommands(AuctionService auctions, AuctionGui gui, EconomyService economy) {
		this.auctions = auctions;
		this.gui = gui;
		this.economy = economy;
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
		if (!player.hasPermission("chunkboomerits.auction") && !player.isOp()) {
			player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
			return true;
		}

		if (args.length == 0) {
			gui.open(player);
			return true;
		}

		String sub = args[0].toLowerCase(Locale.ROOT);
		if (sub.equals("sell") || sub.equals("list")) {
			return sell(player, args);
		}
		if (sub.equals("help")) {
			player.sendMessage(Component.text("/ah — browse auctions", NamedTextColor.YELLOW));
			player.sendMessage(Component.text("/ah sell <price> — list the item in your hand", NamedTextColor.YELLOW));
			return true;
		}

		gui.open(player);
		return true;
	}

	private boolean sell(Player player, String[] args) {
		if (args.length < 2) {
			player.sendMessage(Component.text("Usage: /ah sell <price>", NamedTextColor.YELLOW));
			return true;
		}
		double price;
		try {
			price = EconomyService.parseAmount(args[1]);
		} catch (NumberFormatException ex) {
			player.sendMessage(Component.text("Invalid price.", NamedTextColor.RED));
			return true;
		}
		if (price <= 0) {
			player.sendMessage(Component.text("Price must be positive.", NamedTextColor.RED));
			return true;
		}
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand.getType().isAir() || hand.getAmount() <= 0) {
			player.sendMessage(Component.text("Hold the item you want to sell.", NamedTextColor.RED));
			return true;
		}
		if (auctions.countBySeller(player.getUniqueId()) >= MAX_LISTINGS_PER_PLAYER) {
			player.sendMessage(Component.text("You already have " + MAX_LISTINGS_PER_PLAYER + " listings.", NamedTextColor.RED));
			return true;
		}

		ItemStack toList = hand.clone();
		player.getInventory().setItemInMainHand(null);
		player.updateInventory();

		AuctionService.Listing listing = auctions.list(player.getUniqueId(), player.getName(), price, toList);
		player.sendMessage(Component.text(
				"Listed " + toList.getAmount() + "x " + toList.getType().name() + " for " + economy.format(price)
						+ " (#" + listing.id() + ")",
				NamedTextColor.GREEN
		));
		gui.open(player);
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			String p = args[0].toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			for (String s : List.of("sell", "help")) {
				if (s.startsWith(p)) {
					out.add(s);
				}
			}
			return out;
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
			String p = args[1].toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			for (String s : List.of("100", "500", "1000", "5000")) {
				if (s.startsWith(p)) {
					out.add(s);
				}
			}
			return out;
		}
		return List.of();
	}
}
