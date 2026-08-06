package com.chunkboomerits.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ChunkBoomeritsCommand implements CommandExecutor, TabCompleter {
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("chunkboomerits.give")) {
			sender.sendMessage(Component.text("You must be OP to use Chunk Boomerits.", NamedTextColor.RED));
			return true;
		}

		Player target;
		int amount = 1;

		if (args.length == 0) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Component.text("Usage: /" + label + " <player> [amount]", NamedTextColor.RED));
				return true;
			}
			target = player;
		} else if (args.length == 1) {
			// Could be player name OR amount for self
			Player maybe = Bukkit.getPlayerExact(args[0]);
			if (maybe != null) {
				target = maybe;
			} else if (sender instanceof Player player) {
				try {
					amount = Integer.parseInt(args[0]);
					target = player;
				} catch (NumberFormatException ex) {
					sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
					return true;
				}
			} else {
				sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
				return true;
			}
		} else {
			target = Bukkit.getPlayerExact(args[0]);
			if (target == null) {
				sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
				return true;
			}
			try {
				amount = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				sender.sendMessage(Component.text("Invalid amount: " + args[1], NamedTextColor.RED));
				return true;
			}
		}

		amount = Math.max(1, Math.min(64, amount));
		ItemStack stack = ChunkBoomeritsItems.create(amount);
		target.getInventory().addItem(stack).values()
				.forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

		sender.sendMessage(Component.text(
				"Gave " + amount + " Chunk Boomerits to " + target.getName(),
				NamedTextColor.GREEN
		));
		if (!sender.equals(target)) {
			target.sendMessage(Component.text("You received " + amount + " Chunk Boomerits.", NamedTextColor.GOLD));
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!sender.hasPermission("chunkboomerits.give")) {
			return List.of();
		}
		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			List<String> names = Bukkit.getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
					.collect(Collectors.toCollection(ArrayList::new));
			for (String n : List.of("1", "16", "64")) {
				if (n.startsWith(prefix)) {
					names.add(n);
				}
			}
			return names;
		}
		if (args.length == 2) {
			String prefix = args[1].toLowerCase(Locale.ROOT);
			return List.of("1", "16", "64").stream()
					.filter(n -> n.startsWith(prefix))
					.toList();
		}
		return List.of();
	}
}
