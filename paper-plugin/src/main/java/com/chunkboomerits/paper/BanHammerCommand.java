package com.chunkboomerits.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
 * {@code /banhammer <duration> [player]} — gives a wooden Ban Sword with that ban length.
 */
public final class BanHammerCommand implements CommandExecutor, TabCompleter {
	private static final List<String> SUGGESTIONS = List.of(
			"1s", "10s", "30s",
			"1m", "5m", "10m", "30m",
			"1h", "6h", "12h",
			"1d", "3d", "7d", "14d", "30d",
			"1w", "1mo", "1y",
			"perm"
	);

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.isOp() && !sender.hasPermission("chunkboomerits.banhammer")) {
			sender.sendMessage(Component.text("You must be OP to use this.", NamedTextColor.RED));
			return true;
		}
		if (args.length < 1) {
			sender.sendMessage(Component.text("Usage: /banhammer <duration> [player]", NamedTextColor.YELLOW));
			sender.sendMessage(Component.text(
					"Examples: /banhammer 1s · /banhammer 5m · /banhammer 1d · /banhammer 1w · /banhammer perm",
					NamedTextColor.GRAY
			));
			return true;
		}

		Optional<BanDurations.Parsed> parsed = BanDurations.parse(args[0]);
		if (parsed.isEmpty() && args.length >= 2) {
			// Allow "/banhammer 1 d" style
			parsed = BanDurations.parse(args[0] + args[1]);
			if (parsed.isPresent()) {
				return give(sender, parsed.get(), args.length >= 3 ? args[2] : null);
			}
		}
		if (parsed.isEmpty()) {
			sender.sendMessage(Component.text(
					"Invalid duration. Try: 1s, 30s, 1m, 5m, 1h, 1d, 7d, 1w, 1mo, 1y, perm",
					NamedTextColor.RED
			));
			return true;
		}

		String targetName = args.length >= 2 ? args[1] : null;
		return give(sender, parsed.get(), targetName);
	}

	private boolean give(CommandSender sender, BanDurations.Parsed parsed, String targetName) {
		Player target;
		if (targetName != null) {
			target = Bukkit.getPlayerExact(targetName);
			if (target == null) {
				sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
				return true;
			}
		} else if (sender instanceof Player player) {
			target = player;
		} else {
			sender.sendMessage(Component.text("Usage: /banhammer <duration> <player>", NamedTextColor.RED));
			return true;
		}

		ItemStack sword = OpItems.createBanSword(parsed.millis(), parsed.label());
		target.getInventory().addItem(sword).values().forEach(left ->
				target.getWorld().dropItemNaturally(target.getLocation(), left));
		target.sendMessage(Component.text(
				"Received Ban Sword (" + parsed.label() + "). Hit a player to ban them.",
				NamedTextColor.RED
		));
		if (!sender.equals(target)) {
			sender.sendMessage(Component.text(
					"Gave " + target.getName() + " a Ban Sword (" + parsed.label() + ").",
					NamedTextColor.GREEN
			));
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			String p = args[0].toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			for (String s : SUGGESTIONS) {
				if (s.startsWith(p)) {
					out.add(s);
				}
			}
			return out;
		}
		if (args.length == 2) {
			String p = args[1].toLowerCase(Locale.ROOT);
			return Bukkit.getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
					.toList();
		}
		return List.of();
	}
}
