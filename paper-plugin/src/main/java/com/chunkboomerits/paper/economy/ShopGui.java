package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * Player shop browser — buy admin-set items (infinite stock; listings never remove on buy).
 */
public final class ShopGui implements Listener {
	private static final String TITLE = "Server Shop";

	private final ShopService shop;
	private final EconomyService economy;
	private final EconomyScoreboard scoreboard;
	private final Map<UUID, Map<Integer, Integer>> slotToOffer = new HashMap<>();

	public ShopGui(ShopService shop, EconomyService economy, EconomyScoreboard scoreboard) {
		this.shop = shop;
		this.economy = economy;
		this.scoreboard = scoreboard;
	}

	public void open(Player player) {
		GuiHolder holder = new GuiHolder(GuiHolder.Kind.SHOP);
		Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE, NamedTextColor.AQUA));
		holder.inventory(inv);

		Map<Integer, Integer> map = new HashMap<>();
		List<ShopService.Offer> offers = shop.all();
		int slot = 0;
		for (ShopService.Offer offer : offers) {
			if (slot >= 45) {
				break;
			}
			inv.setItem(slot, display(offer));
			map.put(slot, offer.id());
			slot++;
		}
		if (offers.isEmpty()) {
			inv.setItem(22, button(Material.BARRIER, "Shop is empty", NamedTextColor.RED,
					"OP: hold a totem and run:",
					"/shopadd 500",
					"Or /sbshovel → Shop Admin"));
		}
		inv.setItem(49, button(Material.BARRIER, "Close", NamedTextColor.RED));
		paintBalance(inv, player);
		slotToOffer.put(player.getUniqueId(), map);
		player.openInventory(inv);
	}

	private void paintBalance(Inventory inv, Player player) {
		inv.setItem(48, button(Material.EMERALD, "Your balance: " + economy.format(economy.getBalance(player)), NamedTextColor.GREEN,
				"Buy with your dollar balance",
				"Stock is infinite — items stay listed"));
	}

	private ItemStack display(ShopService.Offer offer) {
		ItemStack stack = offer.itemCopy();
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			return stack;
		}
		List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
		lore.add(Component.empty());
		lore.add(Component.text("Price: " + economy.format(offer.price()), NamedTextColor.GOLD)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("Click to buy (infinite stock)", NamedTextColor.AQUA)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore);
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack button(Material material, String name, NamedTextColor color, String... loreLines) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
		if (loreLines.length > 0) {
			List<Component> lore = new ArrayList<>();
			for (String line : loreLines) {
				lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
			}
			meta.lore(lore);
		}
		stack.setItemMeta(meta);
		return stack;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.SHOP) {
			return;
		}
		event.setCancelled(true);
		event.setResult(Event.Result.DENY);
		if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
			return;
		}
		int slot = event.getRawSlot();
		if (slot == 49) {
			player.closeInventory();
			return;
		}
		Map<Integer, Integer> map = slotToOffer.get(player.getUniqueId());
		if (map == null || !map.containsKey(slot)) {
			return;
		}
		int offerId = map.get(slot);
		ShopService.Offer offer = shop.get(offerId);
		if (offer == null) {
			player.sendMessage(Component.text("That offer was removed by an admin.", NamedTextColor.RED));
			Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> open(player));
			return;
		}

		double price = offer.price();
		ItemStack product = offer.itemCopy();

		// Run next tick so the click cancel fully applies — keeps listing + bought item stable.
		Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> {
			if (!player.isOnline()) {
				return;
			}
			// Still listed (infinite stock) — never remove on buy.
			if (shop.get(offerId) == null) {
				player.sendMessage(Component.text("That offer was removed by an admin.", NamedTextColor.RED));
				open(player);
				return;
			}
			if (economy.getBalance(player) < price) {
				player.sendMessage(Component.text("Need " + economy.format(price), NamedTextColor.RED));
				return;
			}
			if (!economy.withdraw(player.getUniqueId(), price)) {
				player.sendMessage(Component.text("Payment failed.", NamedTextColor.RED));
				return;
			}
			ItemStack tagged = ShopPurchase.mark(product);
			AuctionGui.giveOrDrop(player, tagged);
			scoreboard.refresh(player);
			player.sendMessage(Component.text(
					"Purchased for " + economy.format(price) + "! (shop items can't be /sell'd)",
					NamedTextColor.GREEN
			));

			// Refresh balance only — do NOT remove the shop listing from the GUI.
			if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h
					&& h.kind() == GuiHolder.Kind.SHOP) {
				paintBalance(player.getOpenInventory().getTopInventory(), player);
				// Re-assert the product icon in case the client cleared the slot visually.
				player.getOpenInventory().getTopInventory().setItem(slot, display(shop.get(offerId)));
				player.updateInventory();
			} else {
				open(player);
			}
		});
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder
				&& holder.kind() == GuiHolder.Kind.SHOP) {
			event.setCancelled(true);
			event.setResult(Event.Result.DENY);
		}
	}
}
