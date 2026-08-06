package com.chunkboomerits.paper.economy;

import java.io.File;
import java.io.IOException;
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
 * Admin-set infinite shop stock (totems, etc.).
 * Stored ItemStacks are never exposed mutably — always use {@link Offer#itemCopy()}.
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

		/** Fresh clone for giving / displaying — never mutates stock. */
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

	public void load() {
		offers.clear();
		nextId = 1;
		if (!file.exists()) {
			return;
		}
		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
		nextId = Math.max(1, data.getInt("next-id", 1));
		ConfigurationSection section = data.getConfigurationSection("offers");
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
				double price = row.getDouble("price");
				ItemStack item = row.getItemStack("item");
				if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
					continue;
				}
				offers.put(id, new Offer(id, price, item));
				nextId = Math.max(nextId, id + 1);
			} catch (Exception ex) {
				plugin.getLogger().warning("Bad shop offer " + key + ": " + ex.getMessage());
			}
		}
	}

	public void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("next-id", nextId);
		for (Offer offer : offers.values()) {
			String path = "offers." + offer.id();
			data.set(path + ".price", offer.price());
			data.set(path + ".item", offer.itemCopy());
		}
		try {
			plugin.getDataFolder().mkdirs();
			data.save(file);
		} catch (IOException ex) {
			plugin.getLogger().warning("Could not save shop.yml: " + ex.getMessage());
		}
	}

	public synchronized Offer add(double price, ItemStack item) {
		int id = nextId++;
		Offer offer = new Offer(id, price, item);
		offers.put(id, offer);
		save();
		return offer;
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
}
