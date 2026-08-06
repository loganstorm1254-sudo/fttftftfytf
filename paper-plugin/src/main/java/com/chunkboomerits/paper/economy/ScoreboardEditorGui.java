package com.chunkboomerits.paper.economy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Chest GUI to tweak the economy sidebar (colors + animation).
 */
public final class ScoreboardEditorGui implements Listener {
	private static final String TITLE = "Scoreboard Editor";
	private static final Set<UUID> OPEN = new HashSet<>();
	private static final Set<UUID> TITLE_PROMPT = new HashSet<>();

	private final EconomyScoreboard scoreboard;

	public ScoreboardEditorGui(EconomyScoreboard scoreboard) {
		this.scoreboard = scoreboard;
	}

	public void open(Player player) {
		Inventory inv = Bukkit.createInventory(player, 27, Component.text(TITLE, NamedTextColor.DARK_GREEN));
		paint(inv);
		OPEN.add(player.getUniqueId());
		player.openInventory(inv);
	}

	private void paint(Inventory inv) {
		inv.clear();
		inv.setItem(10, button(
				scoreboard.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
				scoreboard.isEnabled() ? "Sidebar: ON" : "Sidebar: OFF",
				NamedTextColor.GREEN,
				"Click to toggle the $ balance sidebar"
		));
		inv.setItem(12, button(
				Material.NAME_TAG,
				"Title color",
				scoreboard.titleColor(),
				"Current: " + colorName(scoreboard.titleColor()),
				"Click to cycle"
		));
		inv.setItem(13, button(
				Material.GOLD_INGOT,
				"Balance color",
				scoreboard.balanceColor(),
				"Current: " + colorName(scoreboard.balanceColor()),
				"Click to cycle"
		));
		inv.setItem(14, button(
				scoreboard.isAnimation() ? Material.CLOCK : Material.COAL,
				scoreboard.isAnimation() ? "Animation: ON" : "Animation: OFF",
				NamedTextColor.AQUA,
				"Pulses the sidebar title colors"
		));
		inv.setItem(16, button(
				Material.OAK_SIGN,
				"Edit title text",
				NamedTextColor.YELLOW,
				"Current: " + scoreboard.titleText(),
				"Click, then type a new title in chat"
		));
		inv.setItem(22, button(
				Material.BARRIER,
				"Close",
				NamedTextColor.RED,
				"Close this menu"
		));
	}

	private static ItemStack button(Material material, String name, NamedTextColor color, String... loreLines) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
		java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
		for (String line : loreLines) {
			lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
		}
		meta.lore(lore);
		stack.setItemMeta(meta);
		return stack;
	}

	private static String colorName(NamedTextColor color) {
		if (color.equals(NamedTextColor.GOLD)) return "gold";
		if (color.equals(NamedTextColor.YELLOW)) return "yellow";
		if (color.equals(NamedTextColor.AQUA)) return "aqua";
		if (color.equals(NamedTextColor.GREEN)) return "green";
		if (color.equals(NamedTextColor.LIGHT_PURPLE)) return "light purple";
		if (color.equals(NamedTextColor.RED)) return "red";
		if (color.equals(NamedTextColor.WHITE)) return "white";
		if (color.equals(NamedTextColor.DARK_AQUA)) return "dark aqua";
		return color.toString();
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!OPEN.contains(player.getUniqueId())) {
			return;
		}
		Component title = event.getView().title();
		if (!PlainTextComponentSerializer.plainText().serialize(title).equals(TITLE)) {
			return;
		}
		event.setCancelled(true);
		if (event.getClickedInventory() == null || event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
			return;
		}

		switch (event.getSlot()) {
			case 10 -> {
				scoreboard.setEnabled(!scoreboard.isEnabled());
				player.sendMessage(Component.text("Sidebar " + (scoreboard.isEnabled() ? "enabled" : "disabled") + ".", NamedTextColor.GREEN));
			}
			case 12 -> {
				scoreboard.cycleTitleColor();
				player.sendMessage(Component.text("Title color → " + colorName(scoreboard.titleColor()), scoreboard.titleColor()));
			}
			case 13 -> {
				scoreboard.cycleBalanceColor();
				player.sendMessage(Component.text("Balance color → " + colorName(scoreboard.balanceColor()), scoreboard.balanceColor()));
			}
			case 14 -> {
				scoreboard.toggleAnimation();
				player.sendMessage(Component.text("Title animation " + (scoreboard.isAnimation() ? "ON" : "OFF"), NamedTextColor.AQUA));
			}
			case 16 -> {
				TITLE_PROMPT.add(player.getUniqueId());
				player.closeInventory();
				player.sendMessage(Component.text("Type the new scoreboard title in chat (or 'cancel').", NamedTextColor.YELLOW));
				return;
			}
			case 22 -> {
				player.closeInventory();
				return;
			}
			default -> {
				return;
			}
		}
		paint(event.getInventory());
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (event.getPlayer() instanceof Player player) {
			OPEN.remove(player.getUniqueId());
		}
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		if (!TITLE_PROMPT.remove(player.getUniqueId())) {
			return;
		}
		event.setCancelled(true);
		String msg = event.getMessage().trim();
		Bukkit.getScheduler().runTask(com.chunkboomerits.paper.ChunkBoomeritsPlugin.get(), () -> {
			if (msg.equalsIgnoreCase("cancel")) {
				player.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
				open(player);
				return;
			}
			if (msg.length() > 32) {
				player.sendMessage(Component.text("Title too long (max 32).", NamedTextColor.RED));
				open(player);
				return;
			}
			scoreboard.setTitleText(msg);
			player.sendMessage(Component.text("Sidebar title set to: " + msg, NamedTextColor.GREEN));
			open(player);
		});
	}
}
