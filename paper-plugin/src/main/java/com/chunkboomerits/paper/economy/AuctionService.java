package com.chunkboomerits.paper.economy;

import java.io.File;
import java.io.IOException;
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
 * Player auction listings persisted to auctions.yml.
 */
public final class AuctionService {
	public record Listing(int id, UUID seller, String sellerName, double price, ItemStack item) {
	}

	private final ChunkBoomeritsPlugin plugin;
	private final File file;
	private final Map<Integer, Listing> listings = new LinkedHashMap<>();
	private int nextId = 1;

	public AuctionService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "auctions.yml");
	}

	public void load() {
		listings.clear();
		nextId = 1;
		if (!file.exists()) {
			return;
		}
		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
		nextId = Math.max(1, data.getInt("next-id", 1));
		ConfigurationSection section = data.getConfigurationSection("listings");
		if (section == null) {
			return;
		}
		for (String key : section.getKeys(false)) {
			ConfigurationSection row = section.getConfigurationSection(key);
			if (row == null) {
				continue;
			}
			try {
				int id = Integer.parseInt(key);
				UUID seller = UUID.fromString(row.getString("seller"));
				String sellerName = row.getString("seller-name", "Unknown");
				double price = row.getDouble("price");
				ItemStack item = row.getItemStack("item");
				if (item == null || item.getType().isAir()) {
					continue;
				}
				listings.put(id, new Listing(id, seller, sellerName, price, item.clone()));
				nextId = Math.max(nextId, id + 1);
			} catch (Exception ex) {
				plugin.getLogger().warning("Bad auction listing " + key + ": " + ex.getMessage());
			}
		}
	}

	public void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("next-id", nextId);
		for (Listing listing : listings.values()) {
			String path = "listings." + listing.id();
			data.set(path + ".seller", listing.seller().toString());
			data.set(path + ".seller-name", listing.sellerName());
			data.set(path + ".price", listing.price());
			data.set(path + ".item", listing.item());
		}
		try {
			plugin.getDataFolder().mkdirs();
			data.save(file);
		} catch (IOException ex) {
			plugin.getLogger().warning("Could not save auctions.yml: " + ex.getMessage());
		}
	}

	public synchronized Listing list(UUID seller, String sellerName, double price, ItemStack item) {
		int id = nextId++;
		Listing listing = new Listing(id, seller, sellerName, price, item.clone());
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
