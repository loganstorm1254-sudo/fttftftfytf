package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /bal, /pay, /eco, /baltop
 */
public final class EconomyCommands implements CommandExecutor, TabCompleter {
	private final EconomyService economy;
	private final EconomyScoreboard scoreboard;

	public EconomyCommands(EconomyService economy, EconomyScoreboard scoreboard) {
		this.economy = economy;
		this.scoreboard = scoreboard;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String name = command.getName().toLowerCase(Locale.ROOT);
		return switch (name) {
			case "bal", "balance" -> bal(sender, args);
			case "pay" -> pay(sender, args);
			case "eco", "economy" -> eco(sender, args);
			case "baltop" -> baltop(sender, args);
			default -> false;
		};
	}

	private boolean bal(CommandSender sender, String[] args) {
		if (!sender.hasPermission("chunkboomerits.economy.bal") && !sender.isOp()) {
			deny(sender);
			return true;
		}
		Player target;
		if (args.length == 0) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Component.text("Usage: /bal <player>", NamedTextColor.RED));
				return true;
			}
			target = player;
		} else {
			target = Bukkit.getPlayerExact(args[0]);
			if (target == null) {
				sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
				return true;
			}
		}
		double bal = economy.getBalance(target);
		sender.sendMessage(Component.text(
				(sender.equals(target) ? "Balance: " : target.getName() + "'s balance: ") + economy.format(bal),
				NamedTextColor.GREEN
		));
		return true;
	}

	private boolean pay(CommandSender sender, String[] args) {
		if (!(sender instanceof Player from)) {
			sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
			return true;
		}
		if (!from.hasPermission("chunkboomerits.economy.pay") && !from.isOp()) {
			deny(from);
			return true;
		}
		if (args.length < 2) {
			from.sendMessage(Component.text("Usage: /pay <player> <amount>", NamedTextColor.YELLOW));
			return true;
		}
		Player to = Bukkit.getPlayerExact(args[0]);
		if (to == null) {
			from.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
			return true;
		}
		if (to.getUniqueId().equals(from.getUniqueId())) {
			from.sendMessage(Component.text("You can't pay yourself.", NamedTextColor.RED));
			return true;
		}
		double amount;
		try {
			amount = EconomyService.parseAmount(args[1]);
		} catch (NumberFormatException ex) {
			from.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED));
			return true;
		}
		if (amount <= 0) {
			from.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
			return true;
		}
		if (!economy.transfer(from.getUniqueId(), to.getUniqueId(), amount)) {
			from.sendMessage(Component.text("Not enough money. You have " + economy.format(economy.getBalance(from)), NamedTextColor.RED));
			return true;
		}
		scoreboard.refresh(from);
		scoreboard.refresh(to);
		from.sendMessage(Component.text("Paid " + economy.format(amount) + " to " + to.getName() + ".", NamedTextColor.GREEN));
		to.sendMessage(Component.text("Received " + economy.format(amount) + " from " + from.getName() + ".", NamedTextColor.GREEN));
		return true;
	}

	private boolean eco(CommandSender sender, String[] args) {
		if (!sender.isOp() && !sender.hasPermission("chunkboomerits.economy.eco") && !sender.hasPermission("chunkboomerits.*")) {
			deny(sender);
			return true;
		}
		if (args.length < 3) {
			sender.sendMessage(Component.text("Usage: /eco <give|take|set> <player> <amount>", NamedTextColor.YELLOW));
			return true;
		}
		String action = args[0].toLowerCase(Locale.ROOT);
		Player target = Bukkit.getPlayerExact(args[1]);
		if (target == null) {
			sender.sendMessage(Component.text("Player not found (must be online): " + args[1], NamedTextColor.RED));
			return true;
		}
		double amount;
		try {
			amount = EconomyService.parseAmount(args[2]);
		} catch (NumberFormatException ex) {
			sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED));
			return true;
		}
		UUID id = target.getUniqueId();
		switch (action) {
			case "give", "add" -> {
				economy.deposit(id, amount);
				sender.sendMessage(Component.text("Gave " + economy.format(amount) + " to " + target.getName() + ".", NamedTextColor.GREEN));
				target.sendMessage(Component.text("You received " + economy.format(amount) + ".", NamedTextColor.GOLD));
			}
			case "take", "remove" -> {
				if (!economy.withdraw(id, amount)) {
					sender.sendMessage(Component.text(target.getName() + " only has " + economy.format(economy.getBalance(id)), NamedTextColor.RED));
					return true;
				}
				sender.sendMessage(Component.text("Took " + economy.format(amount) + " from " + target.getName() + ".", NamedTextColor.GREEN));
				target.sendMessage(Component.text("You lost " + economy.format(amount) + ".", NamedTextColor.RED));
			}
			case "set" -> {
				economy.setBalance(id, amount);
				sender.sendMessage(Component.text("Set " + target.getName() + "'s balance to " + economy.format(amount) + ".", NamedTextColor.GREEN));
				target.sendMessage(Component.text("Your balance was set to " + economy.format(amount) + ".", NamedTextColor.GOLD));
			}
			default -> {
				sender.sendMessage(Component.text("Usage: /eco <give|take|set> <player> <amount>", NamedTextColor.YELLOW));
				return true;
			}
		}
		scoreboard.refresh(target);
		return true;
	}

	private boolean baltop(CommandSender sender, String[] args) {
		if (!sender.hasPermission("chunkboomerits.economy.bal") && !sender.isOp()) {
			deny(sender);
			return true;
		}
		int limit = 10;
		if (args.length >= 1) {
			try {
				limit = Math.min(20, Math.max(1, Integer.parseInt(args[0])));
			} catch (NumberFormatException ignored) {
			}
		}
		List<Map.Entry<UUID, Double>> top = economy.top(limit);
		sender.sendMessage(Component.text("——— Top Balances ———", NamedTextColor.GOLD));
		if (top.isEmpty()) {
			sender.sendMessage(Component.text("No balances yet.", NamedTextColor.GRAY));
			return true;
		}
		int i = 1;
		for (Map.Entry<UUID, Double> entry : top) {
			sender.sendMessage(Component.text(
					i++ + ". " + economy.nameOf(entry.getKey()) + " — " + economy.format(entry.getValue()),
					NamedTextColor.YELLOW
			));
		}
		return true;
	}

	private static void deny(CommandSender sender) {
		sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		String name = command.getName().toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		if (name.equals("pay") || name.equals("bal") || name.equals("balance")) {
			if (args.length == 1) {
				return online(args[0]);
			}
			if (name.equals("pay") && args.length == 2) {
				return filter(args[1], List.of("10", "50", "100", "1000"));
			}
		}
		if (name.equals("eco") || name.equals("economy")) {
			if (args.length == 1) {
				return filter(args[0], List.of("give", "take", "set"));
			}
			if (args.length == 2) {
				return online(args[1]);
			}
			if (args.length == 3) {
				return filter(args[2], List.of("100", "1000", "10000"));
			}
		}
		if (name.equals("baltop") && args.length == 1) {
			return filter(args[0], List.of("5", "10", "20"));
		}
		return out;
	}

	private static List<String> online(String prefix) {
		String p = prefix.toLowerCase(Locale.ROOT);
		return Bukkit.getOnlinePlayers().stream()
				.map(Player::getName)
				.filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
				.toList();
	}

	private static List<String> filter(String prefix, List<String> options) {
		String p = prefix.toLowerCase(Locale.ROOT);
		return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
	}
}
