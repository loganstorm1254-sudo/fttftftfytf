package com.chunkboomerits.paper.economy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * OP shovel hub: scoreboard editor, sell prices, shop admin.
 */
public final class AdminHubGui implements Listener {
	private static final String TITLE = "Admin Tools";

	private final ScoreboardEditorGui scoreboardGui;
	private final SellPricesAdminGui sellPricesGui;
	private final ShopAdminGui shopAdminGui;
	private final Set<UUID> open = new HashSet<>();

	public AdminHubGui(ScoreboardEditorGui scoreboardGui, SellPricesAdminGui sellPricesGui, ShopAdminGui shopAdminGui) {
		this.scoreboardGui = scoreboardGui;
		this.sellPricesGui = sellPricesGui;
		this.shopAdminGui = shopAdminGui;
	}

	public void open(Player player) {
		GuiHolder holder = new GuiHolder(GuiHolder.Kind.ADMIN_HUB);
		Inventory inv = Bukkit.createInventory(holder, 27, Component.text(TITLE, NamedTextColor.DARK_GREEN));
		holder.inventory(inv);
		inv.setItem(11, button(Material.PAINTING, "Scoreboard Editor", NamedTextColor.GREEN,
				"Colors, animation, sidebar title"));
		inv.setItem(13, button(Material.GOLD_INGOT, "Sell Prices", NamedTextColor.YELLOW,
				"Edit /sell prices for every item",
				"Browse, search, or edit held item"));
		inv.setItem(15, button(Material.TOTEM_OF_UNDYING, "Shop Admin", NamedTextColor.GOLD,
				"Add items players can buy in /shop",
				"Or hold item + /shopadd <price>"));
		inv.setItem(22, button(Material.BARRIER, "Close", NamedTextColor.RED));
		open.add(player.getUniqueId());
		player.openInventory(inv);
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

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.ADMIN_HUB) {
			return;
		}
		event.setCancelled(true);
		if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
			return;
		}
		switch (event.getRawSlot()) {
			case 11 -> {
				player.closeInventory();
				Bukkit.getScheduler().runTask(com.chunkboomerits.paper.ChunkBoomeritsPlugin.get(), () -> scoreboardGui.open(player));
			}
			case 13 -> {
				player.closeInventory();
				Bukkit.getScheduler().runTask(com.chunkboomerits.paper.ChunkBoomeritsPlugin.get(), () -> sellPricesGui.open(player));
			}
			case 15 -> {
				player.closeInventory();
				Bukkit.getScheduler().runTask(com.chunkboomerits.paper.ChunkBoomeritsPlugin.get(), () -> shopAdminGui.open(player));
			}
			case 22 -> player.closeInventory();
			default -> {
			}
		}
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder
				&& holder.kind() == GuiHolder.Kind.ADMIN_HUB) {
			event.setCancelled(true);
		}
	}
}
