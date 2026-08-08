package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * DonutSMP-style /sell GUI: drop items in the grid, click the green SELL button.
 * Layout: 4 rows (9x4) — 3 deposit rows + bottom control bar.
 */
public final class SellGui implements Listener {
	private static final String TITLE = "Sell";
	/** 4-row chest like DonutSell. */
	private static final int SIZE = 36;
	private static final int DEPOSIT_SLOTS = 27;
	private static final int SELL_ALL_SLOT = 27;
	private static final int INFO_SLOT = 29;
	private static final int SELL_SLOT = 31;
	private static final int CLOSE_SLOT = 35;

	private final SellService sell;
	private final EconomyService economy;
	private final EconomyScoreboard scoreboard;

	public SellGui(SellService sell, EconomyService economy, EconomyScoreboard scoreboard) {
		this.sell = sell;
		this.economy = economy;
		this.scoreboard = scoreboard;
	}

	public void open(Player player) {
		GuiHolder holder = new GuiHolder(GuiHolder.Kind.SELL);
		Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text(TITLE, NamedTextColor.DARK_GREEN));
		holder.inventory(inv);
		paintControls(inv);
		player.openInventory(inv);
	}

	private void paintControls(Inventory inv) {
		ItemStack pane = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = DEPOSIT_SLOTS; i < SIZE; i++) {
			inv.setItem(i, pane);
		}
		inv.setItem(SELL_ALL_SLOT, button(Material.CHEST, "Sell Inventory", NamedTextColor.GOLD,
				"Quick-sell every sellable item",
				"in your inventory (not this menu)"));
		inv.setItem(INFO_SLOT, button(Material.PAPER, "How to sell", NamedTextColor.AQUA,
				"1. Put items in the empty slots",
				"2. Click the green SELL button",
				"3. Closing returns unsold items"));
		refreshSellButton(inv);
		inv.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED,
				"Returns unsold items to you"));
	}

	private void refreshSellButton(Inventory inv) {
		double total = 0;
		int stacks = 0;
		int items = 0;
		int unsellable = 0;
		for (int i = 0; i < DEPOSIT_SLOTS; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack == null || stack.getType().isAir()) {
				continue;
			}
			if (sell.isSellable(stack)) {
				total += sell.priceOf(stack);
				stacks++;
				items += stack.getAmount();
			} else {
				unsellable++;
			}
		}
		total = Math.round(total * 100.0) / 100.0;
		List<String> lore = new ArrayList<>();
		lore.add("Click to sell everything in the grid");
		lore.add("");
		if (stacks == 0) {
			lore.add("Payout: $0.00");
			lore.add("Put items above, then click");
		} else {
			lore.add("Items: " + items + " (" + stacks + " stacks)");
			lore.add("Payout: " + economy.format(total));
		}
		if (unsellable > 0) {
			lore.add(unsellable + " stack(s) can't be sold (kept)");
		}
		inv.setItem(SELL_SLOT, button(Material.LIME_CONCRETE, "SELL", NamedTextColor.GREEN,
				lore.toArray(new String[0])));
	}

	private static ItemStack pane(Material material, String name) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack button(Material material, String name, NamedTextColor color, String... loreLines) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
		List<Component> lore = new ArrayList<>();
		for (String line : loreLines) {
			lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
		}
		meta.lore(lore);
		stack.setItemMeta(meta);
		return stack;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.SELL) {
			return;
		}

		Inventory top = event.getView().getTopInventory();
		int raw = event.getRawSlot();

		if (raw >= DEPOSIT_SLOTS && raw < top.getSize()) {
			event.setCancelled(true);
			if (raw == SELL_SLOT) {
				confirmSell(player, top);
			} else if (raw == SELL_ALL_SLOT) {
				sellAllInventory(player);
				refreshSellButton(top);
			} else if (raw == CLOSE_SLOT) {
				player.closeInventory();
			}
			return;
		}

		if (raw < DEPOSIT_SLOTS || event.getClickedInventory() == event.getView().getBottomInventory()) {
			Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> {
				if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h
						&& h.kind() == GuiHolder.Kind.SELL) {
					ItemStack sellBtn = player.getOpenInventory().getTopInventory().getItem(SELL_SLOT);
					if (sellBtn == null || sellBtn.getType() != Material.LIME_CONCRETE) {
						paintControls(player.getOpenInventory().getTopInventory());
					} else {
						refreshSellButton(player.getOpenInventory().getTopInventory());
					}
				}
			});
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onDrag(InventoryDragEvent event) {
		if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.SELL) {
			return;
		}
		for (int slot : event.getRawSlots()) {
			if (slot >= DEPOSIT_SLOTS && slot < event.getView().getTopInventory().getSize()) {
				event.setCancelled(true);
				return;
			}
		}
		if (event.getWhoClicked() instanceof Player player) {
			Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> {
				if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h
						&& h.kind() == GuiHolder.Kind.SELL) {
					refreshSellButton(player.getOpenInventory().getTopInventory());
				}
			});
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		if (!(event.getInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.SELL) {
			return;
		}
		returnItems(player, event.getInventory());
	}

	private void confirmSell(Player player, Inventory inv) {
		double total = 0;
		int stacks = 0;
		int items = 0;
		int kept = 0;

		for (int i = 0; i < DEPOSIT_SLOTS; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack == null || stack.getType().isAir()) {
				continue;
			}
			if (sell.isSellable(stack)) {
				total += sell.priceOf(stack);
				items += stack.getAmount();
				stacks++;
				inv.setItem(i, null);
			} else {
				kept++;
			}
		}

		if (stacks == 0) {
			player.sendMessage(Component.text("Put sellable items in the grid first.", NamedTextColor.RED));
			refreshSellButton(inv);
			return;
		}

		total = Math.round(total * 100.0) / 100.0;
		economy.deposit(player.getUniqueId(), total);
		scoreboard.refresh(player);
		player.sendMessage(Component.text(
				"Sold " + items + " items (" + stacks + " stacks) for " + economy.format(total) + ".",
				NamedTextColor.GREEN
		));
		if (kept > 0) {
			player.sendMessage(Component.text(
					kept + " stack(s) could not be sold and are still in the menu.",
					NamedTextColor.GRAY
			));
		}
		refreshSellButton(inv);
	}

	private void sellAllInventory(Player player) {
		PlayerInventory inv = player.getInventory();
		double total = 0;
		int stacks = 0;
		int items = 0;

		ItemStack[] contents = inv.getStorageContents();
		for (int i = 0; i < contents.length; i++) {
			ItemStack stack = contents[i];
			if (!sell.isSellable(stack)) {
				continue;
			}
			total += sell.priceOf(stack);
			items += stack.getAmount();
			stacks++;
			contents[i] = null;
		}
		inv.setStorageContents(contents);

		ItemStack off = inv.getItemInOffHand();
		if (sell.isSellable(off)) {
			total += sell.priceOf(off);
			items += off.getAmount();
			stacks++;
			inv.setItemInOffHand(null);
		}

		if (stacks == 0) {
			player.sendMessage(Component.text("No sellable items in your inventory.", NamedTextColor.RED));
			return;
		}

		total = Math.round(total * 100.0) / 100.0;
		economy.deposit(player.getUniqueId(), total);
		scoreboard.refresh(player);
		player.updateInventory();
		player.sendMessage(Component.text(
				"Sold " + items + " items (" + stacks + " stacks) for " + economy.format(total) + ".",
				NamedTextColor.GREEN
		));
	}

	private void returnItems(Player player, Inventory inv) {
		for (int i = 0; i < DEPOSIT_SLOTS; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack == null || stack.getType().isAir()) {
				continue;
			}
			inv.setItem(i, null);
			Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
			for (ItemStack drop : leftover.values()) {
				player.getWorld().dropItemNaturally(player.getLocation(), drop);
			}
		}
	}
}
