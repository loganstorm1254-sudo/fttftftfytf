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
 * Makes vanilla-style {@code /give} work for plugin OP items.
 */
public final class GiveInterceptListener implements Listener {
	private static final Pattern BOOMERITS = Pattern.compile(
			"^/?give\\s+(\\S+)\\s+(?:chunkboomerits:)?chunk_?boomerits\\b(?:\\s+(\\d+))?",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern KICK_SWORD = Pattern.compile(
			"^/?give\\s+(\\S+)\\s+(?:chunkboomerits:)?kick_?sword\\b(?:\\s+(\\d+))?",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CUSTOM_DISC = Pattern.compile(
			"^/?give\\s+(\\S+)\\s+(?:chunkboomerits:)?(despacito|music_?disc_?despacito|moskau|music_?disc_?moskau|dschinghis_?khan|kimjonggoon|kim_?jong_?goon|music_?disc_?kim_?jong_?goon|kimjong|hyperbaiter)\\b(?:\\s+(\\d+))?",
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
		message = message.trim();

		Matcher boomerits = BOOMERITS.matcher(message);
		if (boomerits.find()) {
			return give(sender, boomerits.group(1), parseAmount(boomerits.group(2)), ItemKind.BOOMERITS, null);
		}

		Matcher kickSword = KICK_SWORD.matcher(message);
		if (kickSword.find()) {
			return give(sender, kickSword.group(1), 1, ItemKind.KICK_SWORD, null);
		}

		Matcher disc = CUSTOM_DISC.matcher(message);
		if (disc.find()) {
			CustomDisc custom = CustomDisc.fromCommandAlias(disc.group(2));
			if (custom != null) {
				return give(sender, disc.group(1), 1, ItemKind.DISC, custom);
			}
		}

		return false;
	}

	private enum ItemKind { BOOMERITS, KICK_SWORD, DISC }

	private boolean give(CommandSender sender, String targetName, int amount, ItemKind kind, CustomDisc disc) {
		String permission = switch (kind) {
			case BOOMERITS -> "chunkboomerits.give";
			case KICK_SWORD -> "chunkboomerits.kicksword";
			case DISC -> "chunkboomerits.disc";
		};
		String label = switch (kind) {
			case BOOMERITS -> "Chunk Boomerits";
			case KICK_SWORD -> "Kick Sword";
			case DISC -> disc.itemLabel();
		};

		if (!sender.hasPermission(permission) && !sender.isOp()) {
			sender.sendMessage(Component.text("You don't have permission to give " + label + ".", NamedTextColor.RED));
			return true;
		}

		Player target = resolvePlayer(sender, targetName);
		if (target == null) {
			sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
			return true;
		}

		ItemStack stack = switch (kind) {
			case BOOMERITS -> OpItems.createBoomerits(amount);
			case KICK_SWORD -> OpItems.createKickSword();
			case DISC -> disc.create();
		};
		target.getInventory().addItem(stack).values()
				.forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

		sender.sendMessage(Component.text(
				"Gave " + (kind == ItemKind.BOOMERITS ? amount + " " : "") + "[" + label + "] to " + target.getName(),
				NamedTextColor.GREEN
		));
		return true;
	}

	private static int parseAmount(String raw) {
		if (raw == null) {
			return 1;
		}
		try {
			return Math.max(1, Math.min(64, Integer.parseInt(raw)));
		} catch (NumberFormatException ex) {
			return 1;
		}
	}

	private Player resolvePlayer(CommandSender sender, String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.equals("@s") || lower.equals("@p")) {
			return sender instanceof Player player ? player : null;
		}
		if (lower.startsWith("@")) {
			return null;
		}
		return Bukkit.getPlayerExact(name);
	}
}
