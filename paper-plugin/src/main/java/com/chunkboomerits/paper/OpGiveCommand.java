package com.chunkboomerits.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
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

/**
 * Shared give logic for OP tools.
 */
public final class OpGiveCommand implements CommandExecutor, TabCompleter {
	private final String permission;
	private final String itemLabel;
	private final Function<Integer, ItemStack> factory;
	private final boolean stackable;

	public OpGiveCommand(String permission, String itemLabel, Function<Integer, ItemStack> factory, boolean stackable) {
		this.permission = permission;
		this.itemLabel = itemLabel;
		this.factory = factory;
		this.stackable = stackable;
	}

	public static OpGiveCommand boomerits() {
		return new OpGiveCommand("chunkboomerits.give", "Chunk Boomerits", OpItems::createBoomerits, true);
	}

	public static OpGiveCommand kickSword() {
		return new OpGiveCommand("chunkboomerits.kicksword", "Kick Sword", amount -> OpItems.createKickSword(), false);
	}

	public static OpGiveCommand killHammer() {
		return new OpGiveCommand("chunkboomerits.killhammer", "Insta Kill Hammer", amount -> OpItems.createKillHammer(), false);
	}

	public static OpGiveCommand invincibleHelmet() {
		return new OpGiveCommand("chunkboomerits.invhelmet", "Invincible Copper Helmet", amount -> OpItems.createInvincibleHelmet(), false);
	}

	public static OpGiveCommand disc(CustomDisc disc) {
		return new OpGiveCommand("chunkboomerits.disc", disc.itemLabel(), amount -> disc.create(), false);
	}

	public static OpGiveCommand despacito() {
		return disc(CustomDisc.DESPACITO);
	}

	public static OpGiveCommand moskau() {
		return disc(CustomDisc.MOSKAU);
	}

	public static OpGiveCommand kimJongGoon() {
		return disc(CustomDisc.KIM_JONG_GOON);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission(permission)) {
			sender.sendMessage(Component.text("You must be OP to use this.", NamedTextColor.RED));
			return true;
		}

		Player target;
		int amount = 1;

		if (args.length == 0) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Component.text("Usage: /" + label + " <player>" + (stackable ? " [amount]" : ""), NamedTextColor.RED));
				return true;
			}
			target = player;
		} else if (args.length == 1) {
			Player maybe = Bukkit.getPlayerExact(args[0]);
			if (maybe != null) {
				target = maybe;
			} else if (stackable && sender instanceof Player player) {
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
			if (stackable) {
				try {
					amount = Integer.parseInt(args[1]);
				} catch (NumberFormatException ex) {
					sender.sendMessage(Component.text("Invalid amount: " + args[1], NamedTextColor.RED));
					return true;
				}
			}
		}

		if (!stackable) {
			amount = 1;
		} else {
			amount = Math.max(1, Math.min(64, amount));
		}

		ItemStack stack = factory.apply(amount);
		target.getInventory().addItem(stack).values()
				.forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

		sender.sendMessage(Component.text(
				"Gave " + amount + " " + itemLabel + " to " + target.getName(),
				NamedTextColor.GREEN
		));
		if (!sender.equals(target)) {
			target.sendMessage(Component.text("You received " + amount + " " + itemLabel + ".", NamedTextColor.GOLD));
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!sender.hasPermission(permission)) {
			return List.of();
		}
		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			List<String> names = Bukkit.getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
					.collect(Collectors.toCollection(ArrayList::new));
			if (stackable) {
				for (String n : List.of("1", "16", "64")) {
					if (n.startsWith(prefix)) {
						names.add(n);
					}
				}
			}
			return names;
		}
		if (stackable && args.length == 2) {
			String prefix = args[1].toLowerCase(Locale.ROOT);
			return List.of("1", "16", "64").stream()
					.filter(n -> n.startsWith(prefix))
					.toList();
		}
		return List.of();
	}
}
