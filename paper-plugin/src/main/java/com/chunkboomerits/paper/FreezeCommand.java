package com.chunkboomerits.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /freeze &lt;player|all&gt; · /unfreeze &lt;player|all&gt;
 */
public final class FreezeCommand implements CommandExecutor, TabCompleter {
	private final FreezeService freeze;
	private final boolean freezeMode;

	public FreezeCommand(FreezeService freeze, boolean freezeMode) {
		this.freeze = freeze;
		this.freezeMode = freezeMode;
	}

	public static FreezeCommand freeze(FreezeService service) {
		return new FreezeCommand(service, true);
	}

	public static FreezeCommand unfreeze(FreezeService service) {
		return new FreezeCommand(service, false);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.isOp() && !sender.hasPermission("chunkboomerits.freeze")) {
			sender.sendMessage(Component.text("You must be OP to use this.", NamedTextColor.RED));
			return true;
		}
		if (args.length < 1) {
			sender.sendMessage(Component.text(
					"Usage: /" + label + " <player|all>",
					NamedTextColor.YELLOW
			));
			return true;
		}

		String target = args[0].toLowerCase(Locale.ROOT);
		if (target.equals("all") || target.equals("*") || target.equals("everyone")) {
			if (freezeMode) {
				Player except = sender instanceof Player p ? p : null;
				int n = freeze.freezeAll(except);
				sender.sendMessage(Component.text(
						"Froze " + n + " player(s). Hologram: Frozen",
						NamedTextColor.AQUA
				));
				if (except != null) {
					sender.sendMessage(Component.text("(You were not frozen.)", NamedTextColor.DARK_GRAY));
				}
			} else {
				int n = freeze.unfreezeAll();
				sender.sendMessage(Component.text("Unfroze " + n + " player(s).", NamedTextColor.GREEN));
			}
			return true;
		}

		Player player = Bukkit.getPlayerExact(args[0]);
		if (player == null) {
			sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
			return true;
		}

		if (freezeMode) {
			if (freeze.isFrozen(player)) {
				sender.sendMessage(Component.text(player.getName() + " is already frozen.", NamedTextColor.YELLOW));
				return true;
			}
			freeze.freeze(player);
			sender.sendMessage(Component.text("Froze " + player.getName() + ".", NamedTextColor.AQUA));
		} else {
			if (!freeze.isFrozen(player)) {
				sender.sendMessage(Component.text(player.getName() + " is not frozen.", NamedTextColor.YELLOW));
				return true;
			}
			freeze.unfreeze(player);
			sender.sendMessage(Component.text("Unfroze " + player.getName() + ".", NamedTextColor.GREEN));
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length != 1) {
			return List.of();
		}
		String p = args[0].toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		if ("all".startsWith(p)) {
			out.add("all");
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
				out.add(player.getName());
			}
		}
		return out;
	}
}
