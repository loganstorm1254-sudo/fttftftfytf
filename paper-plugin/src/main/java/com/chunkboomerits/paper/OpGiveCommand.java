package com.chunkboomerits.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
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
import org.bukkit.inventory.PlayerInventory;

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

	public static OpGiveCommand scoreboardShovel() {
		return new OpGiveCommand("chunkboomerits.scoreboard", "Scoreboard Shovel", amount -> OpItems.createScoreboardShovel(), false);
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

	/** Unified give: /cbgive <item> [player] */
	public static CommandExecutor cbgive() {
		return new CbGiveCommand();
	}

	public static TabCompleter cbgiveTab() {
		return new CbGiveCommand();
	}

	private static boolean mayUse(CommandSender sender, String permission) {
		return sender.isOp() || sender.hasPermission(permission) || sender.hasPermission("chunkboomerits.*");
	}

	static void giveStack(CommandSender sender, Player target, ItemStack stack, String itemLabel) {
		PlayerInventory inv = target.getInventory();
		ItemStack hand = inv.getItemInMainHand();
		if (hand.getType().isAir()) {
			inv.setItemInMainHand(stack);
		} else {
			Map<Integer, ItemStack> leftover = inv.addItem(stack);
			leftover.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
		}
		target.updateInventory();

		sender.sendMessage(Component.text(
				"Gave 1 " + itemLabel + " (" + stack.getType().name() + ") to " + target.getName(),
				NamedTextColor.GREEN
		));
		if (!sender.equals(target)) {
			target.sendMessage(Component.text("You received " + itemLabel + ".", NamedTextColor.GOLD));
		} else {
			sender.sendMessage(Component.text("Check your hotbar — item is in your hand or inventory.", NamedTextColor.YELLOW));
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!mayUse(sender, permission)) {
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

		ItemStack stack;
		try {
			stack = factory.apply(amount);
		} catch (Throwable ex) {
			sender.sendMessage(Component.text("Failed to create " + itemLabel + ": " + ex.getMessage(), NamedTextColor.RED));
			ChunkBoomeritsPlugin.get().getLogger().severe("Failed to create " + itemLabel);
			ex.printStackTrace();
			return true;
		}
		if (stack == null || stack.getType().isAir()) {
			sender.sendMessage(Component.text("Failed to create " + itemLabel + " (empty item). Is your server 1.21.11?", NamedTextColor.RED));
			return true;
		}

		if (stackable) {
			Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
			leftover.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
			target.updateInventory();
			sender.sendMessage(Component.text(
					"Gave " + amount + " " + itemLabel + " to " + target.getName(),
					NamedTextColor.GREEN
			));
			if (!sender.equals(target)) {
				target.sendMessage(Component.text("You received " + amount + " " + itemLabel + ".", NamedTextColor.GOLD));
			}
		} else {
			giveStack(sender, target, stack, itemLabel);
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!mayUse(sender, permission)) {
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

	private static final class CbGiveCommand implements CommandExecutor, TabCompleter {
		private static final List<String> ITEMS = List.of(
				"boomerits", "kicksword", "killhammer", "invhelmet", "sbshovel",
				"despacito", "moskau", "kimjonggoon"
		);

		@Override
		public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
			if (args.length < 1) {
				sender.sendMessage(Component.text("Usage: /cbgive <" + String.join("|", ITEMS) + "> [player]", NamedTextColor.YELLOW));
				return true;
			}

			String id = args[0].toLowerCase(Locale.ROOT);
			GiveSpec spec = resolve(id);
			if (spec == null) {
				sender.sendMessage(Component.text("Unknown item. Try: " + String.join(", ", ITEMS), NamedTextColor.RED));
				return true;
			}
			if (!mayUse(sender, spec.permission)) {
				sender.sendMessage(Component.text("You must be OP to use this.", NamedTextColor.RED));
				return true;
			}

			Player target;
			if (args.length >= 2) {
				target = Bukkit.getPlayerExact(args[1]);
				if (target == null) {
					sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
					return true;
				}
			} else if (sender instanceof Player player) {
				target = player;
			} else {
				sender.sendMessage(Component.text("Usage: /cbgive <item> <player>", NamedTextColor.RED));
				return true;
			}

			ItemStack stack;
			try {
				stack = spec.factory.get();
			} catch (Throwable ex) {
				sender.sendMessage(Component.text("Failed to create item: " + ex.getMessage(), NamedTextColor.RED));
				ex.printStackTrace();
				return true;
			}
			giveStack(sender, target, stack, spec.label);
			return true;
		}

		@Override
		public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
			if (args.length == 1) {
				String prefix = args[0].toLowerCase(Locale.ROOT);
				return ITEMS.stream().filter(i -> i.startsWith(prefix)).toList();
			}
			if (args.length == 2) {
				String prefix = args[1].toLowerCase(Locale.ROOT);
				return Bukkit.getOnlinePlayers().stream()
						.map(Player::getName)
						.filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
						.toList();
			}
			return List.of();
		}

		private static GiveSpec resolve(String id) {
			return switch (id) {
				case "boomerits", "chunkboomerits" -> new GiveSpec("chunkboomerits.give", "Chunk Boomerits", () -> OpItems.createBoomerits(1));
				case "kicksword", "kick_sword" -> new GiveSpec("chunkboomerits.kicksword", "Kick Sword", OpItems::createKickSword);
				case "killhammer", "hammer", "instakillhammer" -> new GiveSpec("chunkboomerits.killhammer", "Insta Kill Hammer", OpItems::createKillHammer);
				case "invhelmet", "invinciblehelmet", "copperhelmet" -> new GiveSpec("chunkboomerits.invhelmet", "Invincible Copper Helmet", OpItems::createInvincibleHelmet);
				case "sbshovel", "scoreboardshovel", "scoreshovel" -> new GiveSpec("chunkboomerits.scoreboard", "Scoreboard Shovel", OpItems::createScoreboardShovel);
				case "despacito" -> new GiveSpec("chunkboomerits.disc", CustomDisc.DESPACITO.itemLabel(), CustomDisc.DESPACITO::create);
				case "moskau" -> new GiveSpec("chunkboomerits.disc", CustomDisc.MOSKAU.itemLabel(), CustomDisc.MOSKAU::create);
				case "kimjonggoon", "kimjong" -> new GiveSpec("chunkboomerits.disc", CustomDisc.KIM_JONG_GOON.itemLabel(), CustomDisc.KIM_JONG_GOON::create);
				default -> null;
			};
		}

		private record GiveSpec(String permission, String label, Supplier<ItemStack> factory) {
		}
	}
}
