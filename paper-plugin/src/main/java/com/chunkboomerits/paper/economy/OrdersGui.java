package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * DonutSMP-style /orders — place buy orders, fulfill others for money.
 */
public final class OrdersGui implements Listener {
	private static final int PAGE_SIZE = 45;

	private enum Prompt {
		AMOUNT,
		PRICE
	}

	private final OrderService orders;
	private final EconomyService economy;
	private final EconomyScoreboard scoreboard;
	private final Map<UUID, Integer> pages = new HashMap<>();
	private final Map<UUID, Boolean> viewingMine = new HashMap<>();
	private final Map<UUID, Map<Integer, Integer>> slotToOrder = new HashMap<>();
	private final Map<UUID, Prompt> prompts = new HashMap<>();
	private final Map<UUID, ItemStack> pendingItem = new HashMap<>();
	private final Map<UUID, Integer> pendingAmount = new HashMap<>();
	private final Set<UUID> creating = new HashSet<>();

	public OrdersGui(OrderService orders, EconomyService economy, EconomyScoreboard scoreboard) {
		this.orders = orders;
		this.economy = economy;
		this.scoreboard = scoreboard;
	}

	public void openBrowse(Player player) {
		viewingMine.put(player.getUniqueId(), false);
		open(player, pages.getOrDefault(player.getUniqueId(), 0));
	}

	public void openMine(Player player) {
		viewingMine.put(player.getUniqueId(), true);
		open(player, 0);
	}

	public void open(Player player, int page) {
		boolean mine = viewingMine.getOrDefault(player.getUniqueId(), false);
		List<OrderService.Order> list = mine ? orders.byBuyer(player.getUniqueId()) : orders.allOpen();
		int maxPage = Math.max(0, (list.size() - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, maxPage));
		pages.put(player.getUniqueId(), page);

		String title = mine ? "My Orders" : "Orders";
		GuiHolder holder = new GuiHolder(GuiHolder.Kind.ORDERS);
		Inventory inv = Bukkit.createInventory(holder, 54, Component.text(title, NamedTextColor.DARK_AQUA));
		holder.inventory(inv);

		Map<Integer, Integer> map = new HashMap<>();
		int start = page * PAGE_SIZE;
		int end = Math.min(list.size(), start + PAGE_SIZE);
		int slot = 0;
		for (int i = start; i < end; i++) {
			OrderService.Order order = list.get(i);
			inv.setItem(slot, display(order, player, mine));
			map.put(slot, order.id());
			slot++;
		}
		slotToOrder.put(player.getUniqueId(), map);

		if (list.isEmpty()) {
			inv.setItem(22, button(Material.HOPPER, mine ? "No orders yet" : "No open orders", NamedTextColor.GRAY,
					mine ? "Create one with the emerald button" : "Be the first — create a buy order"));
		}

		inv.setItem(45, button(Material.ARROW, "Previous", NamedTextColor.YELLOW));
		inv.setItem(46, button(Material.CHEST, mine ? "Browse orders" : "My orders", NamedTextColor.AQUA,
				mine ? "See everyone's open buy orders" : "Manage your buy orders",
				mine ? "" : "Claim delivered items · cancel orders"));
		if (mine) {
			inv.setItem(47, button(Material.HOPPER, "Claim all delivered", NamedTextColor.LIGHT_PURPLE,
					"Take every item waiting on your orders"));
		}
		inv.setItem(48, button(Material.PAPER, "Page " + (page + 1) + "/" + (maxPage + 1), NamedTextColor.WHITE,
				"Buy orders: pay upfront, get items when filled",
				"Others deliver items and take your money"));
		inv.setItem(49, button(Material.BARRIER, "Close", NamedTextColor.RED));
		inv.setItem(52, button(Material.EMERALD, "Create buy order", NamedTextColor.GREEN,
				"Hold the item you want,",
				"click here, then type amount & price each",
				"You can place many orders for the same item",
				"Money is held in escrow until filled"));
		inv.setItem(53, button(Material.ARROW, "Next", NamedTextColor.YELLOW));

		player.openInventory(inv);
	}

	private ItemStack display(OrderService.Order order, Player viewer, boolean mineView) {
		ItemStack stack = order.displayCopy();
		stack.setAmount(Math.min(64, Math.max(1, order.amountRemaining() > 0 ? order.amountRemaining() : 1)));
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			return stack;
		}
		meta.displayName(Component.text(pretty(order.material()), NamedTextColor.YELLOW)
				.decoration(TextDecoration.ITALIC, false));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.text("Buyer: " + order.buyerName(), NamedTextColor.GRAY)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text(
				"Progress: " + order.amountFilled() + " / " + order.amountWanted()
						+ "  (" + order.amountRemaining() + " left)",
				NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("Pays: " + economy.format(order.pricePer()) + " each", NamedTextColor.GREEN)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text(
				"Full payout left: " + economy.format(order.remainingEscrow()),
				NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		if (order.pendingClaimItems() > 0) {
			lore.add(Component.text("Ready to claim: " + order.pendingClaimItems() + " items", NamedTextColor.LIGHT_PURPLE)
					.decoration(TextDecoration.ITALIC, false));
		}
		lore.add(Component.empty());
		if (order.buyer().equals(viewer.getUniqueId())) {
			lore.add(Component.text("Left-click: claim delivered items", NamedTextColor.AQUA)
					.decoration(TextDecoration.ITALIC, false));
			lore.add(Component.text("Right-click: cancel (refund leftover escrow)", NamedTextColor.RED)
					.decoration(TextDecoration.ITALIC, false));
		} else if (!order.complete()) {
			lore.add(Component.text("Click: deliver matching items from your inv", NamedTextColor.GREEN)
					.decoration(TextDecoration.ITALIC, false));
			lore.add(Component.text("You get paid instantly per item delivered", NamedTextColor.DARK_GRAY)
					.decoration(TextDecoration.ITALIC, false));
		} else {
			lore.add(Component.text("Order complete", NamedTextColor.DARK_GRAY)
					.decoration(TextDecoration.ITALIC, false));
		}
		lore.add(Component.text("ID #" + order.id(), NamedTextColor.DARK_GRAY)
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
			if (line == null || line.isEmpty()) {
				continue;
			}
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
				|| holder.kind() != GuiHolder.Kind.ORDERS) {
			return;
		}
		event.setCancelled(true);
		if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
			return;
		}

		int slot = event.getRawSlot();
		int page = pages.getOrDefault(player.getUniqueId(), 0);
		boolean mine = viewingMine.getOrDefault(player.getUniqueId(), false);

		switch (slot) {
			case 45 -> open(player, page - 1);
			case 53 -> open(player, page + 1);
			case 49 -> player.closeInventory();
			case 46 -> {
				if (mine) {
					openBrowse(player);
				} else {
					openMine(player);
				}
			}
			case 47 -> {
				if (mine) {
					claimAll(player);
				}
			}
			case 52 -> startCreate(player);
			default -> {
				Map<Integer, Integer> map = slotToOrder.get(player.getUniqueId());
				if (map == null || !map.containsKey(slot)) {
					return;
				}
				OrderService.Order order = orders.get(map.get(slot));
				if (order == null) {
					open(player, page);
					return;
				}
				if (order.buyer().equals(player.getUniqueId())) {
					if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
						cancelOrder(player, order);
					} else {
						claimOrder(player, order);
					}
				} else {
					fulfillOrder(player, order);
				}
			}
		}
	}

	private void startCreate(Player player) {
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand.getType().isAir()) {
			player.sendMessage(Component.text("Hold the item you want to order first.", NamedTextColor.RED));
			return;
		}
		pendingItem.put(player.getUniqueId(), hand.clone());
		prompts.put(player.getUniqueId(), Prompt.AMOUNT);
		creating.add(player.getUniqueId());
		player.closeInventory();
		player.sendMessage(Component.text(
				"How many " + pretty(hand.getType()) + " do you want? Type a number (or cancel).",
				NamedTextColor.YELLOW
		));
	}

	private void fulfillOrder(Player player, OrderService.Order order) {
		if (order.complete()) {
			player.sendMessage(Component.text("That order is already filled.", NamedTextColor.RED));
			openBrowse(player);
			return;
		}
		int available = countMaterial(player.getInventory(), order.material());
		if (available <= 0) {
			player.sendMessage(Component.text(
					"You don't have any " + pretty(order.material()) + " to deliver.",
					NamedTextColor.RED
			));
			return;
		}
		int toGive = Math.min(available, order.amountRemaining());
		List<ItemStack> taken = removeMaterial(player.getInventory(), order.material(), toGive);
		OrderService.FulfillResult result = orders.fulfill(order.id(), taken);
		if (result.delivered() <= 0) {
			// refund items
			giveItems(player, taken);
			player.sendMessage(Component.text("Could not fulfill that order.", NamedTextColor.RED));
			return;
		}
		economy.deposit(player.getUniqueId(), result.paid());
		scoreboard.refresh(player);
		player.sendMessage(Component.text(
				"Delivered " + result.delivered() + "x " + pretty(order.material())
						+ " for " + economy.format(result.paid()) + ".",
				NamedTextColor.GREEN
		));
		Player buyer = Bukkit.getPlayer(order.buyer());
		if (buyer != null) {
			buyer.sendMessage(Component.text(
					player.getName() + " delivered " + result.delivered() + "x " + pretty(order.material())
							+ " to your order #" + order.id() + ". /orders → My orders to claim.",
					NamedTextColor.AQUA
			));
		}
		openBrowse(player);
	}

	private void claimOrder(Player player, OrderService.Order order) {
		List<ItemStack> claimed = orders.claimPending(order.id(), player.getUniqueId());
		if (claimed.isEmpty()) {
			player.sendMessage(Component.text("Nothing to claim on that order yet.", NamedTextColor.GRAY));
			openMine(player);
			return;
		}
		giveItems(player, claimed);
		int n = 0;
		for (ItemStack stack : claimed) {
			n += stack.getAmount();
		}
		player.sendMessage(Component.text("Claimed " + n + " items from order #" + order.id() + ".", NamedTextColor.GREEN));
		openMine(player);
	}

	private void claimAll(Player player) {
		List<ItemStack> claimed = orders.claimAllPending(player.getUniqueId());
		if (claimed.isEmpty()) {
			player.sendMessage(Component.text("Nothing to claim yet.", NamedTextColor.GRAY));
			openMine(player);
			return;
		}
		giveItems(player, claimed);
		int n = 0;
		for (ItemStack stack : claimed) {
			n += stack.getAmount();
		}
		player.sendMessage(Component.text("Claimed " + n + " items from your orders.", NamedTextColor.GREEN));
		openMine(player);
	}

	private void cancelOrder(Player player, OrderService.Order order) {
		OrderService.CancelResult result = orders.cancel(order.id(), player.getUniqueId());
		if (result == null) {
			player.sendMessage(Component.text("Could not cancel that order.", NamedTextColor.RED));
			return;
		}
		if (result.refund() > 0) {
			economy.deposit(player.getUniqueId(), result.refund());
			scoreboard.refresh(player);
		}
		giveItems(player, result.pendingClaims());
		player.sendMessage(Component.text(
				"Cancelled order #" + order.id() + ". Refunded " + economy.format(result.refund())
						+ (result.pendingClaims().isEmpty() ? "." : " and returned unclaimed deliveries."),
				NamedTextColor.YELLOW
		));
		openMine(player);
	}

	private static int countMaterial(PlayerInventory inv, Material material) {
		int n = 0;
		for (ItemStack stack : inv.getStorageContents()) {
			if (stack != null && stack.getType() == material) {
				n += stack.getAmount();
			}
		}
		ItemStack off = inv.getItemInOffHand();
		if (off.getType() == material) {
			n += off.getAmount();
		}
		return n;
	}

	private static List<ItemStack> removeMaterial(PlayerInventory inv, Material material, int amount) {
		List<ItemStack> taken = new ArrayList<>();
		int left = amount;
		ItemStack[] contents = inv.getStorageContents();
		for (int i = 0; i < contents.length && left > 0; i++) {
			ItemStack stack = contents[i];
			if (stack == null || stack.getType() != material) {
				continue;
			}
			int take = Math.min(left, stack.getAmount());
			ItemStack piece = stack.clone();
			piece.setAmount(take);
			taken.add(piece);
			int remain = stack.getAmount() - take;
			if (remain <= 0) {
				contents[i] = null;
			} else {
				stack.setAmount(remain);
				contents[i] = stack;
			}
			left -= take;
		}
		inv.setStorageContents(contents);
		if (left > 0) {
			ItemStack off = inv.getItemInOffHand();
			if (off.getType() == material) {
				int take = Math.min(left, off.getAmount());
				ItemStack piece = off.clone();
				piece.setAmount(take);
				taken.add(piece);
				int remain = off.getAmount() - take;
				if (remain <= 0) {
					inv.setItemInOffHand(null);
				} else {
					off.setAmount(remain);
					inv.setItemInOffHand(off);
				}
			}
		}
		return taken;
	}

	private static void giveItems(Player player, List<ItemStack> items) {
		for (ItemStack stack : items) {
			Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
			for (ItemStack drop : leftover.values()) {
				player.getWorld().dropItemNaturally(player.getLocation(), drop);
			}
		}
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder
				&& holder.kind() == GuiHolder.Kind.ORDERS) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onChat(AsyncChatEvent event) {
		Player player = event.getPlayer();
		Prompt prompt = prompts.get(player.getUniqueId());
		if (prompt == null) {
			return;
		}
		event.setCancelled(true);
		String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
		Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> handlePrompt(player, prompt, msg));
	}

	private void handlePrompt(Player player, Prompt prompt, String msg) {
		UUID id = player.getUniqueId();
		if (msg.equalsIgnoreCase("cancel")) {
			prompts.remove(id);
			pendingItem.remove(id);
			pendingAmount.remove(id);
			creating.remove(id);
			player.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
			openBrowse(player);
			return;
		}

		if (prompt == Prompt.AMOUNT) {
			int amount;
			try {
				amount = Integer.parseInt(msg.replace(",", ""));
			} catch (NumberFormatException ex) {
				player.sendMessage(Component.text("Invalid number. Type how many you want, or cancel.", NamedTextColor.RED));
				return;
			}
			if (amount <= 0 || amount > 1_000_000) {
				player.sendMessage(Component.text("Amount must be 1–1000000.", NamedTextColor.RED));
				return;
			}
			pendingAmount.put(id, amount);
			prompts.put(id, Prompt.PRICE);
			ItemStack item = pendingItem.get(id);
			player.sendMessage(Component.text(
					"Price per " + pretty(item.getType()) + "? Type a number (or cancel).",
					NamedTextColor.YELLOW
			));
			return;
		}

		if (prompt == Prompt.PRICE) {
			double price;
			try {
				price = EconomyService.parseAmount(msg);
			} catch (NumberFormatException ex) {
				player.sendMessage(Component.text("Invalid price. Try again, or cancel.", NamedTextColor.RED));
				return;
			}
			if (price <= 0 || price > 1_000_000_000) {
				player.sendMessage(Component.text("Price must be positive.", NamedTextColor.RED));
				return;
			}
			ItemStack item = pendingItem.remove(id);
			Integer amount = pendingAmount.remove(id);
			prompts.remove(id);
			creating.remove(id);
			if (item == null || amount == null) {
				player.sendMessage(Component.text("Create failed — try again.", NamedTextColor.RED));
				openBrowse(player);
				return;
			}
			double total = Math.round(amount * price * 100.0) / 100.0;
			if (!economy.withdraw(player.getUniqueId(), total)) {
				player.sendMessage(Component.text(
						"Need " + economy.format(total) + " in escrow (you have "
								+ economy.format(economy.getBalance(player.getUniqueId())) + ").",
						NamedTextColor.RED
				));
				openBrowse(player);
				return;
			}
			scoreboard.refresh(player);
			OrderService.Order order = orders.create(player.getUniqueId(), player.getName(), item, amount, price);
			player.sendMessage(Component.text(
					"Order #" + order.id() + ": buying " + amount + "x " + pretty(item.getType())
							+ " at " + economy.format(price) + " each (escrow "
							+ economy.format(total) + ").",
					NamedTextColor.GREEN
			));
			openMine(player);
		}
	}
}
