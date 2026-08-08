package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * OP shovel GUI — browse and edit every /sell price.
 */
public final class SellPricesAdminGui implements Listener {
	private static final String TITLE = "Sell Prices";
	private static final int PAGE_SIZE = 45;

	private enum Prompt {
		PRICE,
		SEARCH,
		CONFIRM_REGEN
	}

	private final SellService sell;
	private final EconomyService economy;
	private final Map<UUID, Integer> pages = new HashMap<>();
	private final Map<UUID, String> filters = new HashMap<>();
	private final Map<UUID, Map<Integer, Material>> slotMats = new HashMap<>();
	private final Map<UUID, Prompt> prompts = new HashMap<>();
	private final Map<UUID, Material> pendingMaterial = new HashMap<>();
	private final Set<UUID> open = new HashSet<>();

	public SellPricesAdminGui(SellService sell, EconomyService economy) {
		this.sell = sell;
		this.economy = economy;
	}

	public void open(Player player) {
		open(player, pages.getOrDefault(player.getUniqueId(), 0));
	}

	public void open(Player player, int page) {
		List<Material> materials = filtered(player.getUniqueId());
		int maxPage = Math.max(0, (materials.size() - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, maxPage));
		pages.put(player.getUniqueId(), page);

		GuiHolder holder = new GuiHolder(GuiHolder.Kind.SELL_PRICES);
		Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE, NamedTextColor.DARK_GREEN));
		holder.inventory(inv);

		Map<Integer, Material> map = new HashMap<>();
		int start = page * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && start + i < materials.size(); i++) {
			Material mat = materials.get(start + i);
			inv.setItem(i, display(mat));
			map.put(i, mat);
		}
		slotMats.put(player.getUniqueId(), map);

		String filter = filters.getOrDefault(player.getUniqueId(), "");
		inv.setItem(45, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW,
				"Page " + (page + 1) + " / " + (maxPage + 1)));
		inv.setItem(46, button(Material.GOLDEN_PICKAXE, "Edit held item", NamedTextColor.GOLD,
				"Hold an item, click here,",
				"then type the new /sell price in chat"));
		inv.setItem(47, button(Material.COMPASS, "Search", NamedTextColor.AQUA,
				filter.isEmpty() ? "No filter — click to search" : "Filter: " + filter,
				"Type a material name in chat (or cancel)"));
		inv.setItem(48, button(Material.PAPER, "Page " + (page + 1) + "/" + (maxPage + 1), NamedTextColor.WHITE,
				materials.size() + " items",
				filter.isEmpty() ? "Showing all sellable materials" : "Filtered by: " + filter));
		inv.setItem(49, button(Material.BARRIER, "Back", NamedTextColor.RED, "Return to admin hub"));
		inv.setItem(50, button(Material.MILK_BUCKET, "Clear search", NamedTextColor.GRAY, "Show all materials again"));
		inv.setItem(51, button(Material.TNT, "Regenerate ALL defaults", NamedTextColor.RED,
				"Overwrites every custom price",
				"You will be asked to confirm in chat"));
		inv.setItem(52, button(Material.HOPPER, "Fill missing", NamedTextColor.GREEN,
				"Add any materials that have no price yet"));
		inv.setItem(53, button(Material.ARROW, "Next page", NamedTextColor.YELLOW,
				"Page " + (page + 1) + " / " + (maxPage + 1)));

		open.add(player.getUniqueId());
		player.openInventory(inv);
	}

	private List<Material> filtered(UUID id) {
		String filter = filters.getOrDefault(id, "").toUpperCase(Locale.ROOT).replace(' ', '_');
		List<Material> out = new ArrayList<>();
		for (Material mat : sell.allPrices().keySet()) {
			if (filter.isEmpty() || mat.name().contains(filter)) {
				out.add(mat);
			}
		}
		out.sort(Comparator.comparing(Enum::name));
		return out;
	}

	private ItemStack display(Material mat) {
		ItemStack stack = new ItemStack(mat);
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			return stack;
		}
		Double price = sell.priceOf(mat);
		double unit = price == null ? 0 : price;
		double def = sell.defaultPriceOf(mat);
		meta.displayName(Component.text(pretty(mat), NamedTextColor.YELLOW)
				.decoration(TextDecoration.ITALIC, false));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.text("Sell price: " + economy.format(unit), NamedTextColor.GREEN)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("Default: " + economy.format(def), NamedTextColor.DARK_GRAY)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.empty());
		lore.add(Component.text("Left-click: change price", NamedTextColor.AQUA)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("Right-click: reset to default", NamedTextColor.GRAY)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("Set 0 to make unsellable", NamedTextColor.DARK_GRAY)
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

	private static String pretty(Material material) {
		String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		StringBuilder out = new StringBuilder();
		for (String part : name.split(" ")) {
			if (part.isEmpty()) {
				continue;
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
		}
		return out.toString().trim();
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.SELL_PRICES) {
			return;
		}
		event.setCancelled(true);
		if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
			return;
		}

		int slot = event.getRawSlot();
		UUID id = player.getUniqueId();
		int page = pages.getOrDefault(id, 0);

		switch (slot) {
			case 45 -> open(player, page - 1);
			case 53 -> open(player, page + 1);
			case 49 -> {
				player.closeInventory();
				Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () ->
						ChunkBoomeritsPlugin.get().adminHubGui().open(player));
			}
			case 50 -> {
				filters.remove(id);
				open(player, 0);
			}
			case 46 -> startHeldEdit(player);
			case 47 -> {
				prompts.put(id, Prompt.SEARCH);
				player.closeInventory();
				player.sendMessage(Component.text(
						"Type a material name to search (e.g. diamond, oak_log). Or 'cancel'.",
						NamedTextColor.YELLOW
				));
			}
			case 51 -> {
				prompts.put(id, Prompt.CONFIRM_REGEN);
				player.closeInventory();
				player.sendMessage(Component.text(
						"Type 'confirm' to regenerate ALL sell prices to defaults (overwrites customs), or 'cancel'.",
						NamedTextColor.RED
				));
			}
			case 52 -> {
				int before = sell.allPrices().size();
				sell.fillMissing();
				player.sendMessage(Component.text(
						"Filled missing prices (+" + (sell.allPrices().size() - before) + ").",
						NamedTextColor.GREEN
				));
				open(player, page);
			}
			default -> {
				Map<Integer, Material> map = slotMats.get(id);
				if (map == null || !map.containsKey(slot)) {
					return;
				}
				Material mat = map.get(slot);
				if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
					sell.resetPrice(mat);
					player.sendMessage(Component.text(
							pretty(mat) + " reset to " + economy.format(sell.defaultPriceOf(mat)),
							NamedTextColor.GREEN
					));
					open(player, page);
					return;
				}
				pendingMaterial.put(id, mat);
				prompts.put(id, Prompt.PRICE);
				player.closeInventory();
				Double cur = sell.priceOf(mat);
				player.sendMessage(Component.text(
						"New /sell price for " + pretty(mat) + " (current "
								+ economy.format(cur == null ? 0 : cur) + "). Type a number, or 'cancel'.",
						NamedTextColor.YELLOW
				));
			}
		}
	}

	private void startHeldEdit(Player player) {
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand.getType().isAir()) {
			player.sendMessage(Component.text("Hold the item whose sell price you want to change.", NamedTextColor.RED));
			return;
		}
		Material mat = hand.getType();
		if (!mat.isItem()) {
			player.sendMessage(Component.text("That can't be sold.", NamedTextColor.RED));
			return;
		}
		pendingMaterial.put(player.getUniqueId(), mat);
		prompts.put(player.getUniqueId(), Prompt.PRICE);
		player.closeInventory();
		Double cur = sell.priceOf(mat);
		player.sendMessage(Component.text(
				"New /sell price for " + pretty(mat) + " (current "
						+ economy.format(cur == null ? 0 : cur) + "). Type a number, or 'cancel'.",
				NamedTextColor.YELLOW
		));
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder
				&& holder.kind() == GuiHolder.Kind.SELL_PRICES) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onChat(AsyncChatEvent event) {
		Player player = event.getPlayer();
		Prompt prompt = prompts.remove(player.getUniqueId());
		if (prompt == null) {
			return;
		}
		event.setCancelled(true);
		String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
		Material pending = pendingMaterial.remove(player.getUniqueId());

		Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> {
			if (msg.equalsIgnoreCase("cancel")) {
				player.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
				open(player);
				return;
			}
			switch (prompt) {
				case SEARCH -> {
					filters.put(player.getUniqueId(), msg);
					open(player, 0);
					player.sendMessage(Component.text("Filter set to: " + msg, NamedTextColor.AQUA));
				}
				case CONFIRM_REGEN -> {
					if (!msg.equalsIgnoreCase("confirm")) {
						player.sendMessage(Component.text("Cancelled — type confirm to regenerate.", NamedTextColor.GRAY));
						open(player);
						return;
					}
					sell.regenerateAll();
					player.sendMessage(Component.text(
							"Regenerated all sell prices (" + sell.allPrices().size() + ").",
							NamedTextColor.GOLD
					));
					open(player, 0);
				}
				case PRICE -> {
					if (pending == null) {
						player.sendMessage(Component.text("No item selected.", NamedTextColor.RED));
						open(player);
						return;
					}
					double price;
					try {
						price = EconomyService.parseAmount(msg);
					} catch (NumberFormatException ex) {
						player.sendMessage(Component.text("Invalid number. Try again from the menu.", NamedTextColor.RED));
						open(player);
						return;
					}
					if (price < 0) {
						player.sendMessage(Component.text("Price cannot be negative.", NamedTextColor.RED));
						open(player);
						return;
					}
					sell.setPrice(pending, price);
					player.sendMessage(Component.text(
							pretty(pending) + " sell price set to " + economy.format(price)
									+ (price == 0 ? " (unsellable)" : ""),
							NamedTextColor.GREEN
					));
					open(player);
				}
			}
		});
	}
}
