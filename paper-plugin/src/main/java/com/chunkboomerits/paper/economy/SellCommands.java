package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * DonutSMP-style /sell — GUI deposit grid + green sell button.
 * /sell — open sell menu
 * /sell hand — sell held item
 * /sell all — sell everything sellable in inventory
 * /sell price — show price of held item
 */
public final class SellCommands implements CommandExecutor, TabCompleter {
	private final SellService sell;
	private final EconomyService economy;
	private final EconomyScoreboard scoreboard;
	private final SellGui sellGui;

	public SellCommands(SellService sell, EconomyService economy, EconomyScoreboard scoreboard, SellGui sellGui) {
		this.sell = sell;
		this.economy = economy;
		this.scoreboard = scoreboard;
		this.sellGui = sellGui;
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
		if (!player.hasPermission("chunkboomerits.sell") && !player.isOp()) {
			player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
			return true;
		}

		if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
			sellGui.open(player);
			return true;
		}

		String sub = args[0].toLowerCase(Locale.ROOT);
		return switch (sub) {
			case "hand" -> sellHand(player);
			case "all", "inv", "inventory" -> sellAll(player);
			case "price", "worth", "check" -> showPrice(player);
			case "help" -> {
				player.sendMessage(Component.text("/sell — open sell menu (put items, click green SELL)", NamedTextColor.YELLOW));
				player.sendMessage(Component.text("/sell hand — sell item in hand", NamedTextColor.YELLOW));
				player.sendMessage(Component.text("/sell all — sell all sellable items in your inventory", NamedTextColor.YELLOW));
				player.sendMessage(Component.text("/sell price — check the fixed price of your held item", NamedTextColor.YELLOW));
				if (player.isOp() || player.hasPermission("chunkboomerits.*")) {
					player.sendMessage(Component.text("/sell fill — add missing item/block prices (OP)", NamedTextColor.YELLOW));
					player.sendMessage(Component.text("/sell regenerate — rebuild ALL prices (OP, overwrites)", NamedTextColor.YELLOW));
					player.sendMessage(Component.text("/sell reload — reload sell-prices.yml (OP)", NamedTextColor.YELLOW));
				}
				player.sendMessage(Component.text(
						"Prices: " + sell.allPrices().size() + " items in sell-prices.yml",
						NamedTextColor.DARK_GRAY
				));
				yield true;
			}
			case "reload" -> {
				if (!player.isOp() && !player.hasPermission("chunkboomerits.*")) {
					player.sendMessage(Component.text("OP only.", NamedTextColor.RED));
					yield true;
				}
				sell.load();
				player.sendMessage(Component.text("Reloaded sell-prices.yml (" + sell.allPrices().size() + " items).", NamedTextColor.GREEN));
				yield true;
			}
			case "fill" -> {
				if (!player.isOp() && !player.hasPermission("chunkboomerits.*")) {
					player.sendMessage(Component.text("OP only.", NamedTextColor.RED));
					yield true;
				}
				int before = sell.allPrices().size();
				sell.fillMissing();
				int after = sell.allPrices().size();
				player.sendMessage(Component.text(
						"Added " + (after - before) + " missing prices. Total: " + after,
						NamedTextColor.GREEN
				));
				yield true;
			}
			case "regenerate" -> {
				if (!player.isOp() && !player.hasPermission("chunkboomerits.*")) {
					player.sendMessage(Component.text("OP only.", NamedTextColor.RED));
					yield true;
				}
				sell.regenerateAll();
				player.sendMessage(Component.text(
						"Regenerated ALL sell prices (" + sell.allPrices().size()
								+ "). Custom prices were overwritten.",
						NamedTextColor.GOLD
				));
				yield true;
			}
			default -> {
				player.sendMessage(Component.text(
						"Usage: /sell | /sell hand|all|price",
						NamedTextColor.YELLOW
				));
				yield true;
			}
		};
	}

	private boolean sellHand(Player player) {
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand.getType().isAir()) {
			player.sendMessage(Component.text("Hold an item to sell, or use /sell", NamedTextColor.RED));
			return true;
		}
		if (!sell.isSellable(hand)) {
			if (ShopPurchase.isMarked(hand)) {
				player.sendMessage(Component.text("Shop purchases can't be sold — no flipping.", NamedTextColor.RED));
			} else {
				player.sendMessage(Component.text("You can't sell " + pretty(hand.getType()) + ".", NamedTextColor.RED));
			}
			return true;
		}
		double payout = sell.priceOf(hand);
		int amount = hand.getAmount();
		Material type = hand.getType();
		player.getInventory().setItemInMainHand(null);
		economy.deposit(player.getUniqueId(), payout);
		scoreboard.refresh(player);
		player.sendMessage(Component.text(
				"Sold " + amount + "x " + pretty(type) + " for " + economy.format(payout) + ".",
				NamedTextColor.GREEN
		));
		return true;
	}

	private boolean sellAll(Player player) {
		PlayerInventory inv = player.getInventory();
		double total = 0;
		int stacks = 0;
		int items = 0;

		ItemStack[] contents = inv.getStorageContents();
		for (int i = 0; i < contents.length; i++) {
			ItemStack stack = contents[i];
			if (!sell.isSellable(stack)) {
				continue;
			}
			total += sell.priceOf(stack);
			items += stack.getAmount();
			stacks++;
			contents[i] = null;
		}
		inv.setStorageContents(contents);

		ItemStack off = inv.getItemInOffHand();
		if (sell.isSellable(off)) {
			total += sell.priceOf(off);
			items += off.getAmount();
			stacks++;
			inv.setItemInOffHand(null);
		}

		if (stacks == 0) {
			player.sendMessage(Component.text("No sellable items in your inventory.", NamedTextColor.RED));
			return true;
		}

		total = Math.round(total * 100.0) / 100.0;
		economy.deposit(player.getUniqueId(), total);
		scoreboard.refresh(player);
		player.updateInventory();
		player.sendMessage(Component.text(
				"Sold " + items + " items (" + stacks + " stacks) for " + economy.format(total) + ".",
				NamedTextColor.GREEN
		));
		return true;
	}

	private boolean showPrice(Player player) {
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand.getType().isAir()) {
			player.sendMessage(Component.text("Hold an item to check its sell price.", NamedTextColor.RED));
			return true;
		}
		Double unit = sell.priceOf(hand.getType());
		if (ShopPurchase.isMarked(hand)) {
			player.sendMessage(Component.text("Shop purchase — cannot /sell (no flipping).", NamedTextColor.RED));
			return true;
		}
		if (unit == null || !sell.isSellable(hand)) {
			player.sendMessage(Component.text(pretty(hand.getType()) + " cannot be sold.", NamedTextColor.RED));
			return true;
		}
		player.sendMessage(Component.text(
				pretty(hand.getType()) + ": " + economy.format(unit) + " each"
						+ "  |  " + hand.getAmount() + "x = " + economy.format(unit * hand.getAmount()),
				NamedTextColor.AQUA
		));
		return true;
	}

	private static String pretty(Material material) {
		String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		StringBuilder out = new StringBuilder();
		for (String part : name.split(" ")) {
			if (part.isEmpty()) {
				continue;
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
		}
		return out.toString().trim();
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			String p = args[0].toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			for (String s : List.of("gui", "hand", "all", "price", "help")) {
				if (s.startsWith(p)) {
					out.add(s);
				}
			}
			if (sender.isOp() || sender.hasPermission("chunkboomerits.*")) {
				for (String s : List.of("reload", "fill", "regenerate")) {
					if (s.startsWith(p)) {
						out.add(s);
					}
				}
			}
			return out;
		}
		return List.of();
	}
}
