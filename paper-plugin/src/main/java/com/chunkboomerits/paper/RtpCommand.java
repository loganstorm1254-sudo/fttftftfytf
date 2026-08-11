package com.chunkboomerits.paper;

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

/**
 * {@code /rtp} opens the distance GUI. {@code /rtp <blocks>} skips the menu.
 */
public final class RtpCommand implements CommandExecutor, TabCompleter {
	private static final List<String> QUICK = List.of("500", "1000", "2500", "5000", "10000", "25000");

	private final RtpGui gui;
	private final RtpService rtp;

	public RtpCommand(RtpGui gui, RtpService rtp) {
		this.gui = gui;
		this.rtp = rtp;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
			return true;
		}
		if (!player.hasPermission("chunkboomerits.rtp") && !player.isOp()) {
			player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
			return true;
		}

		if (args.length == 0) {
			gui.open(player);
			return true;
		}

		String raw = args[0].toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
		if (raw.endsWith("k")) {
			try {
				int k = Integer.parseInt(raw.substring(0, raw.length() - 1));
				rtp.teleport(player, k * 1000);
				return true;
			} catch (NumberFormatException ignored) {
				// fall through
			}
		}
		try {
			int blocks = Integer.parseInt(raw);
			rtp.teleport(player, blocks);
		} catch (NumberFormatException ex) {
			player.sendMessage(Component.text("Usage: /rtp  or  /rtp <blocks>", NamedTextColor.YELLOW));
			player.sendMessage(Component.text("Examples: /rtp 1000 · /rtp 5k · /rtp 25000", NamedTextColor.GRAY));
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			String p = args[0].toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			for (String s : QUICK) {
				if (s.startsWith(p)) {
					out.add(s);
				}
			}
			return out;
		}
		return List.of();
	}
}
