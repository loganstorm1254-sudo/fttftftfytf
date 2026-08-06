package com.chunkboomerits.paper.economy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * Player auction listings. Survives plugin jar updates via Base64 + backups.
 */
public final class AuctionService {
	public static final class Listing {
		private final int id;
		private final UUID seller;
		private final String sellerName;
		private final double price;
		private final ItemStack item;

		public Listing(int id, UUID seller, String sellerName, double price, ItemStack item) {
			this.id = id;
			this.seller = seller;
			this.sellerName = sellerName;
			this.price = price;
			this.item = item.clone();
		}

		public int id() {
			return id;
		}

		public UUID seller() {
			return seller;
		}

		public String sellerName() {
			return sellerName;
		}

		public double price() {
			return price;
		}

		public ItemStack itemCopy() {
			return item.clone();
		}
	}

	private final ChunkBoomeritsPlugin plugin;
	private final File file;
	private final Map<Integer, Listing> listings = new LinkedHashMap<>();
	private int nextId = 1;

	public AuctionService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "auctions.yml");
	}

	public synchronized void load() {
		listings.clear();
		nextId = 1;
		if (!file.exists()) {
			plugin.getLogger().info("No auctions.yml yet (fresh install).");
			return;
		}
		PersistentItems.backupIfExists(file, plugin.getDataFolder(), plugin.getLogger());
		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
		nextId = Math.max(1, data.getInt("next-id", 1));
		ConfigurationSection section = data.getConfigurationSection("listings");
		if (section == null) {
			plugin.getLogger().info("auctions.yml loaded (0 listings).");
			return;
		}
		int loaded = 0;
		int skipped = 0;
		for (String key : section.getKeys(false)) {
			ConfigurationSection row = section.getConfigurationSection(key);
			if (row == null) {
				skipped++;
				continue;
			}
			try {
				int id = Integer.parseInt(key);
				UUID seller = UUID.fromString(row.getString("seller"));
				String sellerName = row.getString("seller-name", "Unknown");
				double price = row.getDouble("price");
				ItemStack item = PersistentItems.readItem(row, plugin.getLogger(), "ah#" + key);
				if (item == null) {
					item = PersistentItems.simpleFallback(row);
				}
				if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
					skipped++;
					plugin.getLogger().warning("Skipping AH listing " + key + " (item unreadable).");
					continue;
				}
				listings.put(id, new Listing(id, seller, sellerName, price, item));
				nextId = Math.max(nextId, id + 1);
				loaded++;
			} catch (Exception ex) {
				skipped++;
				plugin.getLogger().warning("Bad auction listing " + key + ": " + ex.getMessage());
			}
		}
		plugin.getLogger().info("auctions.yml loaded: " + loaded + " listings" + (skipped > 0 ? " (" + skipped + " skipped)" : "") + ".");
		if (loaded > 0) {
			save();
		}
	}

	public synchronized void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("version", 2);
		data.set("next-id", nextId);
		for (Listing listing : listings.values()) {
			String path = "listings." + listing.id();
			data.set(path + ".seller", listing.seller().toString());
			data.set(path + ".seller-name", listing.sellerName());
			data.set(path + ".price", listing.price());
			PersistentItems.writeItem(data, path, listing.itemCopy(), plugin.getLogger());
		}
		PersistentItems.saveAtomically(data, file, plugin.getLogger());
	}

	public synchronized Listing list(UUID seller, String sellerName, double price, ItemStack item) {
		int id = nextId++;
		Listing listing = new Listing(id, seller, sellerName, price, item);
		listings.put(id, listing);
		save();
		return listing;
	}

	public synchronized Listing get(int id) {
		return listings.get(id);
	}

	public synchronized Listing remove(int id) {
		Listing removed = listings.remove(id);
		if (removed != null) {
			save();
		}
		return removed;
	}

	public synchronized List<Listing> all() {
		return Collections.unmodifiableList(new ArrayList<>(listings.values()));
	}

	public synchronized int size() {
		return listings.size();
	}

	public synchronized int countBySeller(UUID seller) {
		int n = 0;
		for (Listing listing : listings.values()) {
			if (listing.seller().equals(seller)) {
				n++;
			}
		}
		return n;
	}

	public synchronized void removeAllBySeller(UUID seller, List<Listing> out) {
		Iterator<Map.Entry<Integer, Listing>> it = listings.entrySet().iterator();
		while (it.hasNext()) {
			Listing listing = it.next().getValue();
			if (listing.seller().equals(seller)) {
				out.add(listing);
				it.remove();
			}
		}
		if (!out.isEmpty()) {
			save();
		}
	}
}
