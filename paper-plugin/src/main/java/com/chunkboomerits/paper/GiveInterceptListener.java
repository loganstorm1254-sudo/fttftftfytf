package com.chunkboomerits.paper;

import java.util.Locale;
import java.util.function.Supplier;
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
	private static final Pattern KILL_HAMMER = Pattern.compile(
			"^/?give\\s+(\\S+)\\s+(?:chunkboomerits:)?(?:kill_?hammer|insta_?kill_?hammer|hammer)\\b(?:\\s+(\\d+))?",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern INV_HELMET = Pattern.compile(
			"^/?give\\s+(\\S+)\\s+(?:chunkboomerits:)?(?:invincible_?helmet|inv_?helmet|copper_?helmet)\\b(?:\\s+(\\d+))?",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern SB_SHOVEL = Pattern.compile(
			"^/?give\\s+(\\S+)\\s+(?:chunkboomerits:)?(?:sb_?shovel|scoreboard_?shovel|eco_?shovel)\\b(?:\\s+(\\d+))?",
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
			int amount = parseAmount(boomerits.group(2));
			return give(sender, boomerits.group(1), amount,
					"chunkboomerits.give", "Chunk Boomerits", true, () -> OpItems.createBoomerits(amount));
		}

		Matcher kickSword = KICK_SWORD.matcher(message);
		if (kickSword.find()) {
			return give(sender, kickSword.group(1), 1,
					"chunkboomerits.kicksword", "Kick Sword", false, OpItems::createKickSword);
		}

		Matcher killHammer = KILL_HAMMER.matcher(message);
		if (killHammer.find()) {
			return give(sender, killHammer.group(1), 1,
					"chunkboomerits.killhammer", "Insta Kill Hammer", false, OpItems::createKillHammer);
		}

		Matcher invHelmet = INV_HELMET.matcher(message);
		if (invHelmet.find()) {
			return give(sender, invHelmet.group(1), 1,
					"chunkboomerits.invhelmet", "Invincible Copper Helmet", false, OpItems::createInvincibleHelmet);
		}

		Matcher sbShovel = SB_SHOVEL.matcher(message);
		if (sbShovel.find()) {
			return give(sender, sbShovel.group(1), 1,
					"chunkboomerits.scoreboard", "Scoreboard Shovel", false, OpItems::createScoreboardShovel);
		}

		Matcher disc = CUSTOM_DISC.matcher(message);
		if (disc.find()) {
			CustomDisc custom = CustomDisc.fromCommandAlias(disc.group(2));
			if (custom != null) {
				return give(sender, disc.group(1), 1,
						"chunkboomerits.disc", custom.itemLabel(), false, custom::create);
			}
		}

		return false;
	}

	private boolean give(
			CommandSender sender,
			String targetName,
			int amount,
			String permission,
			String label,
			boolean showAmount,
			Supplier<ItemStack> factory
	) {
		if (!sender.hasPermission(permission) && !sender.isOp() && !sender.hasPermission("chunkboomerits.*")) {
			sender.sendMessage(Component.text("You don't have permission to give " + label + ".", NamedTextColor.RED));
			return true;
		}

		Player target = resolvePlayer(sender, targetName);
		if (target == null) {
			sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
			return true;
		}

		ItemStack stack = factory.get();
		target.getInventory().addItem(stack).values()
				.forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

		sender.sendMessage(Component.text(
				"Gave " + (showAmount ? amount + " " : "") + "[" + label + "] to " + target.getName(),
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
