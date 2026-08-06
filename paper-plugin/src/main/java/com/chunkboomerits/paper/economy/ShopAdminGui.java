package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.papermc.paper.event.player.AsyncChatEvent;

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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * OP shop editor — add held items with a price, or remove offers.
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
		GuiHolder holder = new GuiHolder(GuiHolder.Kind.SHOP_ADMIN);
		Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE, NamedTextColor.DARK_PURPLE));
		holder.inventory(inv);

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
				"then type the price in chat",
				"Or use: /shopadd <price>"));
		inv.setItem(49, button(Material.BARRIER, "Close", NamedTextColor.RED));
		inv.setItem(53, button(Material.CHEST, "Open player /shop", NamedTextColor.AQUA,
				"Preview the shop players see"));
		slotToOffer.put(player.getUniqueId(), map);
		player.openInventory(inv);
	}

	private ItemStack display(ShopService.Offer offer) {
		ItemStack stack = offer.item().clone();
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			return stack;
		}
		List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
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
		if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.SHOP_ADMIN) {
			return;
		}
		event.setCancelled(true);
		if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
			return;
		}

		int slot = event.getRawSlot();
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
				player.sendMessage(Component.text("Tip: /shopadd <price> also works.", NamedTextColor.GRAY));
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
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder
				&& holder.kind() == GuiHolder.Kind.SHOP_ADMIN) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onChat(AsyncChatEvent event) {
		Player player = event.getPlayer();
		if (!pricePrompt.remove(player.getUniqueId())) {
			return;
		}
		event.setCancelled(true);
		String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
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
				player.sendMessage(Component.text("Invalid price. Use /shopadd <price> instead.", NamedTextColor.RED));
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
