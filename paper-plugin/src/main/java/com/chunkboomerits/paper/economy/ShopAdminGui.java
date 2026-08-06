package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * OP shop editor — add held items (e.g. totems) with a price, or remove offers.
 */
public final class ShopAdminGui implements Listener {
	private static final String TITLE = "Shop Admin";

	private final ShopService shop;
	private final EconomyService economy;
	private final Map<UUID, Map<Integer, Integer>> slotToOffer = new HashMap<>();
	private final Set<UUID> pricePrompt = new HashSet<>();
	private final Map<UUID, ItemStack> pendingItem = new HashMap<>();

	public ShopAdminGui(ShopService shop, EconomyService economy) {
		this.shop = shop;
		this.economy = economy;
	}

	public void open(Player player) {
		Inventory inv = Bukkit.createInventory(player, 54, Component.text(TITLE, NamedTextColor.DARK_PURPLE));
		Map<Integer, Integer> map = new HashMap<>();
		int slot = 0;
		for (ShopService.Offer offer : shop.all()) {
			if (slot >= 45) {
				break;
			}
			inv.setItem(slot, display(offer));
			map.put(slot, offer.id());
			slot++;
		}
		inv.setItem(45, button(Material.EMERALD, "Add held item", NamedTextColor.GREEN,
				"Hold a totem (or any item), click here,",
				"then type the price in chat"));
		inv.setItem(49, button(Material.BARRIER, "Close", NamedTextColor.RED));
		inv.setItem(53, button(Material.CHEST, "Open player /shop", NamedTextColor.AQUA,
				"Preview the shop players see"));
		slotToOffer.put(player.getUniqueId(), map);
		player.openInventory(inv);
	}

	private ItemStack display(ShopService.Offer offer) {
		ItemStack stack = offer.item().clone();
		ItemMeta meta = stack.getItemMeta();
		List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
		lore.add(Component.empty());
		lore.add(Component.text("Price: " + economy.format(offer.price()), NamedTextColor.GOLD)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("Click to REMOVE from shop", NamedTextColor.RED)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore);
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

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!slotToOffer.containsKey(player.getUniqueId())) {
			return;
		}
		String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
		if (!title.equals(TITLE)) {
			return;
		}
		event.setCancelled(true);
		if (event.getClickedInventory() == null) {
			return;
		}

		int slot = event.getSlot();
		if (slot == 49) {
			player.closeInventory();
			return;
		}
		if (slot == 53) {
			player.closeInventory();
			Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () ->
					ChunkBoomeritsPlugin.get().shopGui().open(player));
			return;
		}
		if (slot == 45) {
			ItemStack hand = player.getInventory().getItemInMainHand();
			if (hand.getType().isAir()) {
				player.sendMessage(Component.text("Hold the item to sell in the shop (e.g. a totem).", NamedTextColor.RED));
				return;
			}
			pendingItem.put(player.getUniqueId(), hand.clone());
			pricePrompt.add(player.getUniqueId());
			player.closeInventory();
			player.sendMessage(Component.text("Type the shop price in chat (or 'cancel'). Example: 500", NamedTextColor.YELLOW));
			return;
		}

		Map<Integer, Integer> map = slotToOffer.get(player.getUniqueId());
		if (map == null || !map.containsKey(slot)) {
			return;
		}
		ShopService.Offer removed = shop.remove(map.get(slot));
		if (removed != null) {
			player.sendMessage(Component.text("Removed " + removed.item().getType().name() + " from the shop.", NamedTextColor.YELLOW));
		}
		open(player);
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (event.getPlayer() instanceof Player player) {
			UUID id = player.getUniqueId();
			Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> {
				if (player.getOpenInventory() == null
						|| !PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()).equals(TITLE)) {
					if (!pricePrompt.contains(id)) {
						slotToOffer.remove(id);
					}
				}
			});
		}
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		if (!pricePrompt.remove(player.getUniqueId())) {
			return;
		}
		event.setCancelled(true);
		String msg = event.getMessage().trim();
		ItemStack item = pendingItem.remove(player.getUniqueId());
		Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> {
			if (msg.equalsIgnoreCase("cancel") || item == null) {
				player.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
				open(player);
				return;
			}
			double price;
			try {
				price = EconomyService.parseAmount(msg);
			} catch (NumberFormatException ex) {
				player.sendMessage(Component.text("Invalid price. Try again from the shovel menu.", NamedTextColor.RED));
				open(player);
				return;
			}
			if (price <= 0) {
				player.sendMessage(Component.text("Price must be positive.", NamedTextColor.RED));
				open(player);
				return;
			}
			ShopService.Offer offer = shop.add(price, item);
			player.sendMessage(Component.text(
					"Added " + item.getAmount() + "x " + item.getType().name() + " to /shop for " + economy.format(price)
							+ " (#" + offer.id() + ")",
					NamedTextColor.GREEN
			));
			open(player);
		});
	}
}
