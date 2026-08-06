package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * Browse / buy / cancel auction listings.
 */
public final class AuctionGui implements Listener {
	private static final String TITLE = "Auction House";
	private static final int PAGE_SIZE = 45;

	private final AuctionService auctions;
	private final EconomyService economy;
	private final EconomyScoreboard scoreboard;
	private final Map<UUID, Integer> pages = new HashMap<>();
	private final Map<UUID, Map<Integer, Integer>> slotToListing = new HashMap<>();

	public AuctionGui(AuctionService auctions, EconomyService economy, EconomyScoreboard scoreboard) {
		this.auctions = auctions;
		this.economy = economy;
		this.scoreboard = scoreboard;
	}

	public void open(Player player) {
		open(player, pages.getOrDefault(player.getUniqueId(), 0));
	}

	public void open(Player player, int page) {
		List<AuctionService.Listing> all = auctions.all();
		int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, maxPage));
		pages.put(player.getUniqueId(), page);

		Inventory inv = Bukkit.createInventory(player, 54, Component.text(TITLE, NamedTextColor.GOLD));
		Map<Integer, Integer> map = new HashMap<>();
		int start = page * PAGE_SIZE;
		int end = Math.min(all.size(), start + PAGE_SIZE);
		int slot = 0;
		for (int i = start; i < end; i++) {
			AuctionService.Listing listing = all.get(i);
			inv.setItem(slot, display(listing, player));
			map.put(slot, listing.id());
			slot++;
		}

		inv.setItem(45, nav(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
		inv.setItem(49, nav(Material.BARRIER, "Close", NamedTextColor.RED));
		inv.setItem(53, nav(Material.ARROW, "Next page", NamedTextColor.YELLOW));
		inv.setItem(48, nav(Material.PAPER, "Page " + (page + 1) + "/" + (maxPage + 1), NamedTextColor.GRAY,
				"List with: /ah sell <price>",
				"Click a listing to buy",
				"Click your own listing to cancel"));

		slotToListing.put(player.getUniqueId(), map);
		player.openInventory(inv);
	}

	private ItemStack display(AuctionService.Listing listing, Player viewer) {
		ItemStack stack = listing.item().clone();
		ItemMeta meta = stack.getItemMeta();
		List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
		lore.add(Component.empty());
		lore.add(Component.text("Price: " + economy.format(listing.price()), NamedTextColor.GREEN)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("Seller: " + listing.sellerName(), NamedTextColor.GRAY)
				.decoration(TextDecoration.ITALIC, false));
		lore.add(Component.text("ID #" + listing.id(), NamedTextColor.DARK_GRAY)
				.decoration(TextDecoration.ITALIC, false));
		if (listing.seller().equals(viewer.getUniqueId())) {
			lore.add(Component.text("Click to CANCEL & return item", NamedTextColor.RED)
					.decoration(TextDecoration.ITALIC, false));
		} else {
			lore.add(Component.text("Click to BUY", NamedTextColor.AQUA)
					.decoration(TextDecoration.ITALIC, false));
		}
		meta.lore(lore);
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack nav(Material material, String name, NamedTextColor color, String... loreLines) {
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

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!slotToListing.containsKey(player.getUniqueId())) {
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
		if (slot == 45) {
			open(player, pages.getOrDefault(player.getUniqueId(), 0) - 1);
			return;
		}
		if (slot == 53) {
			open(player, pages.getOrDefault(player.getUniqueId(), 0) + 1);
			return;
		}
		if (slot >= PAGE_SIZE) {
			return;
		}

		Map<Integer, Integer> map = slotToListing.get(player.getUniqueId());
		if (map == null || !map.containsKey(slot)) {
			return;
		}
		int id = map.get(slot);
		AuctionService.Listing listing = auctions.get(id);
		if (listing == null) {
			player.sendMessage(Component.text("That listing is gone.", NamedTextColor.RED));
			open(player);
			return;
		}

		if (listing.seller().equals(player.getUniqueId())) {
			cancel(player, listing);
		} else {
			buy(player, listing);
		}
	}

	private void buy(Player buyer, AuctionService.Listing listing) {
		if (listing.seller().equals(buyer.getUniqueId())) {
			return;
		}
		if (economy.getBalance(buyer) < listing.price()) {
			buyer.sendMessage(Component.text("Not enough money. Need " + economy.format(listing.price()), NamedTextColor.RED));
			return;
		}
		AuctionService.Listing removed = auctions.remove(listing.id());
		if (removed == null) {
			buyer.sendMessage(Component.text("Someone else bought that first.", NamedTextColor.RED));
			open(buyer);
			return;
		}
		if (!economy.withdraw(buyer.getUniqueId(), removed.price())) {
			auctions.list(removed.seller(), removed.sellerName(), removed.price(), removed.item());
			buyer.sendMessage(Component.text("Payment failed.", NamedTextColor.RED));
			return;
		}
		economy.deposit(removed.seller(), removed.price());
		giveOrDrop(buyer, removed.item().clone());
		scoreboard.refresh(buyer);
		Player seller = Bukkit.getPlayer(removed.seller());
		if (seller != null) {
			scoreboard.refresh(seller);
			seller.sendMessage(Component.text(
					buyer.getName() + " bought your AH listing for " + economy.format(removed.price()) + ".",
					NamedTextColor.GREEN
			));
		}
		buyer.sendMessage(Component.text("Bought for " + economy.format(removed.price()) + "!", NamedTextColor.GREEN));
		open(buyer);
	}

	private void cancel(Player player, AuctionService.Listing listing) {
		AuctionService.Listing removed = auctions.remove(listing.id());
		if (removed == null) {
			player.sendMessage(Component.text("Listing already gone.", NamedTextColor.RED));
			open(player);
			return;
		}
		giveOrDrop(player, removed.item().clone());
		player.sendMessage(Component.text("Cancelled listing #" + removed.id() + ".", NamedTextColor.YELLOW));
		open(player);
	}

	static void giveOrDrop(Player player, ItemStack item) {
		Map<Integer, ItemStack> left = player.getInventory().addItem(item);
		left.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
		player.updateInventory();
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (event.getPlayer() instanceof Player player) {
			// Delay clear so reopen from click still works; clear if not reopening same tick
			UUID id = player.getUniqueId();
			Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> {
				if (player.getOpenInventory() == null
						|| !PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()).equals(TITLE)) {
					slotToListing.remove(id);
				}
			});
		}
	}
}
