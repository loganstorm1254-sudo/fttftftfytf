package com.chunkboomerits.paper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Makes {@code /give <player> chunkboomerits[:chunk_boomerits] [amount]} work on Paper/Purpur.
 */
public final class GiveInterceptListener implements Listener {
	private static final Pattern GIVE = Pattern.compile(
			"^/?give\\s+(\\S+)\\s+(?:chunkboomerits:)?chunk_?boomerits\\b(?:\\s+(\\d+))?",
			Pattern.CASE_INSENSITIVE
	);

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
		if (tryHandle(event.getPlayer(), event.getMessage())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onServerCommand(ServerCommandEvent event) {
		String cmd = event.getCommand();
		if (!cmd.startsWith("/")) {
			cmd = "/" + cmd;
		}
		if (tryHandle(event.getSender(), cmd)) {
			event.setCancelled(true);
		}
	}

	private boolean tryHandle(CommandSender sender, String raw) {
		String message = raw.startsWith("/") ? raw.substring(1) : raw;
		Matcher matcher = GIVE.matcher(message.trim());
		if (!matcher.find()) {
			return false;
		}

		if (!sender.hasPermission("chunkboomerits.give") && !sender.isOp()) {
			sender.sendMessage(Component.text("You must be OP to give Chunk Boomerits.", NamedTextColor.RED));
			return true;
		}

		String targetName = matcher.group(1);
		Player target = resolvePlayer(sender, targetName);
		if (target == null) {
			sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
			return true;
		}

		int amount = 1;
		if (matcher.group(2) != null) {
			try {
				amount = Integer.parseInt(matcher.group(2));
			} catch (NumberFormatException ignored) {
				amount = 1;
			}
		}
		amount = Math.max(1, Math.min(64, amount));

		ItemStack stack = ChunkBoomeritsItems.create(amount);
		target.getInventory().addItem(stack).values()
				.forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

		sender.sendMessage(Component.text(
				"Gave " + amount + " [" + "Chunk Boomerits" + "] to " + target.getName(),
				NamedTextColor.GREEN
		));
		return true;
	}

	private Player resolvePlayer(CommandSender sender, String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.equals("@s") || lower.equals("@p")) {
			return sender instanceof Player player ? player : null;
		}
		if (lower.startsWith("@")) {
			// Keep simple: only @s/@p; otherwise exact name
			return null;
		}
		return Bukkit.getPlayerExact(name);
	}
}
