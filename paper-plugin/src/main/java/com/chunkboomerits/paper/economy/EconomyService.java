package com.chunkboomerits.paper.economy;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * Simple persistent $ economy.
 */
public final class EconomyService {
	private static final DecimalFormat MONEY = createFormat();

	private final ChunkBoomeritsPlugin plugin;
	private final File file;
	private FileConfiguration data;
	private final Map<UUID, Double> balances = new HashMap<>();
	private double startingBalance;

	public EconomyService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "economy.yml");
	}

	public void load() {
		plugin.saveDefaultConfig();
		startingBalance = plugin.getConfig().getDouble("economy.starting-balance", 1000.0);
		if (!file.exists()) {
			try {
				plugin.getDataFolder().mkdirs();
				file.createNewFile();
			} catch (IOException ex) {
				plugin.getLogger().warning("Could not create economy.yml: " + ex.getMessage());
			}
		}
		data = YamlConfiguration.loadConfiguration(file);
		balances.clear();
		if (data.isConfigurationSection("balances")) {
			for (String key : data.getConfigurationSection("balances").getKeys(false)) {
				try {
					balances.put(UUID.fromString(key), round(data.getDouble("balances." + key)));
				} catch (IllegalArgumentException ignored) {
					// skip bad uuid
				}
			}
		}
	}

	public void save() {
		if (data == null) {
			data = new YamlConfiguration();
		}
		data.set("balances", null);
		for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
			data.set("balances." + entry.getKey(), entry.getValue());
		}
		try {
			data.save(file);
		} catch (IOException ex) {
			plugin.getLogger().warning("Could not save economy.yml: " + ex.getMessage());
		}
	}

	public double getBalance(UUID uuid) {
		return balances.computeIfAbsent(uuid, id -> startingBalance);
	}

	public double getBalance(Player player) {
		return getBalance(player.getUniqueId());
	}

	public void setBalance(UUID uuid, double amount) {
		balances.put(uuid, round(Math.max(0, amount)));
		save();
	}

	public void deposit(UUID uuid, double amount) {
		if (amount <= 0) {
			return;
		}
		setBalance(uuid, getBalance(uuid) + amount);
	}

	public boolean withdraw(UUID uuid, double amount) {
		if (amount <= 0) {
			return false;
		}
		double bal = getBalance(uuid);
		if (bal < amount) {
			return false;
		}
		setBalance(uuid, bal - amount);
		return true;
	}

	public boolean transfer(UUID from, UUID to, double amount) {
		if (amount <= 0 || from.equals(to)) {
			return false;
		}
		if (!withdraw(from, amount)) {
			return false;
		}
		deposit(to, amount);
		return true;
	}

	public void ensurePlayer(Player player) {
		getBalance(player.getUniqueId());
	}

	public List<Map.Entry<UUID, Double>> top(int limit) {
		return balances.entrySet().stream()
				.sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
				.limit(Math.max(1, limit))
				.collect(Collectors.toList());
	}

	public String format(double amount) {
		return "$" + MONEY.format(round(amount));
	}

	public double startingBalance() {
		return startingBalance;
	}

	public static double parseAmount(String raw) {
		String cleaned = raw.trim().replace(",", "").replace("$", "");
		return round(Double.parseDouble(cleaned));
	}

	private static double round(double amount) {
		return Math.round(amount * 100.0) / 100.0;
	}

	private static DecimalFormat createFormat() {
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
		DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
		format.setGroupingUsed(true);
		return format;
	}

	public String nameOf(UUID uuid) {
		Player online = Bukkit.getPlayer(uuid);
		if (online != null) {
			return online.getName();
		}
		String name = Bukkit.getOfflinePlayer(uuid).getName();
		return name != null ? name : uuid.toString().substring(0, 8);
	}
}
