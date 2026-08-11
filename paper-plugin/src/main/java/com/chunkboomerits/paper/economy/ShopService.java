package com.chunkboomerits.paper.economy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * Admin-set infinite shop stock. Survives plugin jar updates via Base64 + backups.
 */
public final class ShopService {
	public static final class Offer {
		private final int id;
		private final double price;
		private final ItemStack item;

		public Offer(int id, double price, ItemStack item) {
			this.id = id;
			this.price = price;
			this.item = item.clone();
		}

		public int id() {
			return id;
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
	private final Map<Integer, Offer> offers = new LinkedHashMap<>();
	private int nextId = 1;

	public ShopService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "shop.yml");
	}

	public synchronized void load() {
		offers.clear();
		nextId = 1;
		if (!file.exists()) {
			plugin.getLogger().info("No shop.yml yet (fresh install).");
			return;
		}
		PersistentItems.backupIfExists(file, plugin.getDataFolder(), plugin.getLogger());
		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
		nextId = Math.max(1, data.getInt("next-id", 1));
		ConfigurationSection section = data.getConfigurationSection("offers");
		if (section == null) {
			plugin.getLogger().info("shop.yml loaded (0 offers).");
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
				double price = row.getDouble("price");
				ItemStack item = PersistentItems.readItem(row, plugin.getLogger(), "shop#" + key);
				if (item == null) {
					item = PersistentItems.simpleFallback(row);
				}
				if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
					skipped++;
					plugin.getLogger().warning("Skipping shop offer " + key + " (item unreadable).");
					continue;
				}
				offers.put(id, new Offer(id, price, item));
				nextId = Math.max(nextId, id + 1);
				loaded++;
			} catch (Exception ex) {
				skipped++;
				plugin.getLogger().warning("Bad shop offer " + key + ": " + ex.getMessage());
			}
		}
		plugin.getLogger().info("shop.yml loaded: " + loaded + " offers" + (skipped > 0 ? " (" + skipped + " skipped)" : "") + ".");
		// Re-save in durable Base64 format after upgrading from legacy YAML items
		if (loaded > 0) {
			save();
		}
	}

	public synchronized void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("version", 2);
		data.set("next-id", nextId);
		for (Offer offer : offers.values()) {
			String path = "offers." + offer.id();
			data.set(path + ".price", offer.price());
			PersistentItems.writeItem(data, path, offer.itemCopy(), plugin.getLogger());
		}
		PersistentItems.saveAtomically(data, file, plugin.getLogger());
	}

	public synchronized Offer add(double price, ItemStack item) {
		int id = nextId++;
		Offer offer = new Offer(id, price, item);
		offers.put(id, offer);
		save();
		return offer;
	}

	/**
	 * Lowest per-item shop price for this material, or null if none listed.
	 * Used to stop /shop → /sell flipping of the same material.
	 */
	public synchronized Double lowestUnitPrice(org.bukkit.Material material) {
		if (material == null || material.isAir()) {
			return null;
		}
		double lowest = Double.POSITIVE_INFINITY;
		boolean found = false;
		for (Offer offer : offers.values()) {
			ItemStack stack = offer.itemCopy();
			if (stack.getType() != material || stack.getAmount() <= 0) {
				continue;
			}
			double unit = offer.price() / stack.getAmount();
			if (unit < lowest) {
				lowest = unit;
				found = true;
			}
		}
		return found ? Math.round(lowest * 100.0) / 100.0 : null;
	}

	public synchronized Offer get(int id) {
		return offers.get(id);
	}

	public synchronized Offer remove(int id) {
		Offer removed = offers.remove(id);
		if (removed != null) {
			save();
		}
		return removed;
	}

	public synchronized List<Offer> all() {
		return Collections.unmodifiableList(new ArrayList<>(offers.values()));
	}

	public synchronized int size() {
		return offers.size();
	}
}
