package com.chunkboomerits.paper.economy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;
import com.chunkboomerits.paper.OpItems;

/**
 * Fixed per-material sell prices (DonutSMP-style). Same item always pays the same.
 */
public final class SellService {
	private final ChunkBoomeritsPlugin plugin;
	private final File file;
	private final Map<Material, Double> prices = new LinkedHashMap<>();

	public SellService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "sell-prices.yml");
	}

	public void load() {
		prices.clear();
		if (!file.exists()) {
			prices.putAll(defaultPrices());
			save();
			plugin.getLogger().info("Created sell-prices.yml with " + prices.size() + " fixed prices.");
			return;
		}
		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
		if (!data.isConfigurationSection("prices")) {
			prices.putAll(defaultPrices());
			save();
			return;
		}
		for (String key : data.getConfigurationSection("prices").getKeys(false)) {
			Material mat = Material.matchMaterial(key);
			if (mat == null || !mat.isItem()) {
				continue;
			}
			double price = data.getDouble("prices." + key, -1);
			if (price >= 0) {
				prices.put(mat, round(price));
			}
		}
		// Merge any new defaults that are missing (keeps existing prices unchanged)
		boolean added = false;
		for (Map.Entry<Material, Double> entry : defaultPrices().entrySet()) {
			if (!prices.containsKey(entry.getKey())) {
				prices.put(entry.getKey(), entry.getValue());
				added = true;
			}
		}
		if (added) {
			save();
		}
		plugin.getLogger().info("Loaded " + prices.size() + " sell prices from sell-prices.yml");
	}

	public void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("version", 1);
		data.options().header(String.join("\n",
				"Fixed sell prices — same material always sells for the same price.",
				"Edit values here; use /sell reload (OP) to apply.",
				"Missing materials from defaults are added automatically without changing yours."
		));
		Map<String, Double> sorted = new TreeMap<>();
		for (Map.Entry<Material, Double> entry : prices.entrySet()) {
			sorted.put(entry.getKey().name(), entry.getValue());
		}
		for (Map.Entry<String, Double> entry : sorted.entrySet()) {
			data.set("prices." + entry.getKey(), entry.getValue());
		}
		try {
			plugin.getDataFolder().mkdirs();
			data.save(file);
		} catch (IOException ex) {
			plugin.getLogger().warning("Could not save sell-prices.yml: " + ex.getMessage());
		}
	}

	public Double priceOf(Material material) {
		return prices.get(material);
	}

	public double priceOf(ItemStack stack) {
		if (stack == null || stack.getType().isAir() || !isSellable(stack)) {
			return 0;
		}
		Double unit = prices.get(stack.getType());
		if (unit == null) {
			return 0;
		}
		return round(unit * stack.getAmount());
	}

	public boolean isSellable(ItemStack stack) {
		if (stack == null || stack.getType().isAir()) {
			return false;
		}
		if (OpItems.isBoomerits(stack) || OpItems.isKickSword(stack) || OpItems.isKillHammer(stack)
				|| OpItems.isInvincibleHelmet(stack) || OpItems.isScoreboardShovel(stack)
				|| OpItems.isCustomDisc(stack)) {
			return false;
		}
		return prices.containsKey(stack.getType());
	}

	public Map<Material, Double> allPrices() {
		return Collections.unmodifiableMap(prices);
	}

	private static double round(double amount) {
		return Math.round(amount * 100.0) / 100.0;
	}

	/** Curated DonutSMP-ish fixed prices — deterministic, never random. */
	static Map<Material, Double> defaultPrices() {
		Map<Material, Double> p = new LinkedHashMap<>();

		// Blocks / building
		put(p, Material.COBBLESTONE, 0.10);
		put(p, Material.STONE, 0.15);
		put(p, Material.DEEPSLATE, 0.20);
		put(p, Material.COBBLED_DEEPSLATE, 0.15);
		put(p, Material.NETHERRACK, 0.08);
		put(p, Material.END_STONE, 0.50);
		put(p, Material.DIRT, 0.05);
		put(p, Material.SAND, 0.10);
		put(p, Material.RED_SAND, 0.12);
		put(p, Material.GRAVEL, 0.10);
		put(p, Material.CLAY, 0.40);
		put(p, Material.OBSIDIAN, 8.00);
		put(p, Material.CRYING_OBSIDIAN, 12.00);
		put(p, Material.GLASS, 0.30);
		put(p, Material.WHITE_WOOL, 0.50);

		// Logs
		put(p, Material.OAK_LOG, 0.40);
		put(p, Material.SPRUCE_LOG, 0.40);
		put(p, Material.BIRCH_LOG, 0.40);
		put(p, Material.JUNGLE_LOG, 0.45);
		put(p, Material.ACACIA_LOG, 0.45);
		put(p, Material.DARK_OAK_LOG, 0.45);
		put(p, Material.MANGROVE_LOG, 0.50);
		put(p, Material.CHERRY_LOG, 0.55);
		put(p, Material.PALE_OAK_LOG, 0.55);
		put(p, Material.CRIMSON_STEM, 0.60);
		put(p, Material.WARPED_STEM, 0.60);

		// Ores & minerals
		put(p, Material.COAL, 1.00);
		put(p, Material.COAL_ORE, 1.50);
		put(p, Material.DEEPSLATE_COAL_ORE, 1.75);
		put(p, Material.RAW_IRON, 3.00);
		put(p, Material.IRON_INGOT, 5.00);
		put(p, Material.IRON_BLOCK, 45.00);
		put(p, Material.RAW_COPPER, 1.50);
		put(p, Material.COPPER_INGOT, 2.50);
		put(p, Material.RAW_GOLD, 6.00);
		put(p, Material.GOLD_INGOT, 10.00);
		put(p, Material.GOLD_BLOCK, 90.00);
		put(p, Material.GOLD_NUGGET, 1.10);
		put(p, Material.LAPIS_LAZULI, 2.00);
		put(p, Material.REDSTONE, 1.25);
		put(p, Material.QUARTZ, 2.50);
		put(p, Material.AMETHYST_SHARD, 3.00);
		put(p, Material.DIAMOND, 50.00);
		put(p, Material.DIAMOND_BLOCK, 450.00);
		put(p, Material.EMERALD, 35.00);
		put(p, Material.EMERALD_BLOCK, 315.00);
		put(p, Material.NETHERITE_SCRAP, 200.00);
		put(p, Material.NETHERITE_INGOT, 1200.00);
		put(p, Material.NETHERITE_BLOCK, 10800.00);
		put(p, Material.ANCIENT_DEBRIS, 250.00);

		// Farm
		put(p, Material.WHEAT, 0.40);
		put(p, Material.WHEAT_SEEDS, 0.05);
		put(p, Material.CARROT, 0.45);
		put(p, Material.POTATO, 0.40);
		put(p, Material.BEETROOT, 0.45);
		put(p, Material.PUMPKIN, 1.00);
		put(p, Material.MELON_SLICE, 0.25);
		put(p, Material.SUGAR_CANE, 0.50);
		put(p, Material.CACTUS, 0.60);
		put(p, Material.BAMBOO, 0.20);
		put(p, Material.KELP, 0.15);
		put(p, Material.COCOA_BEANS, 0.80);
		put(p, Material.NETHER_WART, 1.50);
		put(p, Material.SWEET_BERRIES, 0.35);
		put(p, Material.GLOW_BERRIES, 0.50);
		put(p, Material.APPLE, 1.00);
		put(p, Material.GOLDEN_APPLE, 75.00);
		put(p, Material.ENCHANTED_GOLDEN_APPLE, 2500.00);
		put(p, Material.BREAD, 1.50);
		put(p, Material.HAY_BLOCK, 3.50);

		// Mob drops
		put(p, Material.ROTTEN_FLESH, 0.20);
		put(p, Material.BONE, 0.50);
		put(p, Material.STRING, 0.60);
		put(p, Material.SPIDER_EYE, 0.80);
		put(p, Material.GUNPOWDER, 2.00);
		put(p, Material.ENDER_PEARL, 12.00);
		put(p, Material.BLAZE_ROD, 15.00);
		put(p, Material.BLAZE_POWDER, 8.00);
		put(p, Material.GHAST_TEAR, 25.00);
		put(p, Material.MAGMA_CREAM, 5.00);
		put(p, Material.SLIME_BALL, 3.00);
		put(p, Material.LEATHER, 1.50);
		put(p, Material.FEATHER, 0.40);
		put(p, Material.ARROW, 0.30);
		put(p, Material.INK_SAC, 0.70);
		put(p, Material.GLOW_INK_SAC, 2.00);
		put(p, Material.PHANTOM_MEMBRANE, 8.00);
		put(p, Material.SHULKER_SHELL, 80.00);
		put(p, Material.TOTEM_OF_UNDYING, 350.00);
		put(p, Material.ECHO_SHARD, 40.00);
		put(p, Material.BREEZE_ROD, 20.00);
		put(p, Material.HEAVY_CORE, 500.00);

		// Food meats
		put(p, Material.BEEF, 0.80);
		put(p, Material.COOKED_BEEF, 1.40);
		put(p, Material.PORKCHOP, 0.80);
		put(p, Material.COOKED_PORKCHOP, 1.40);
		put(p, Material.CHICKEN, 0.60);
		put(p, Material.COOKED_CHICKEN, 1.10);
		put(p, Material.MUTTON, 0.70);
		put(p, Material.COOKED_MUTTON, 1.20);
		put(p, Material.COD, 0.50);
		put(p, Material.COOKED_COD, 0.90);
		put(p, Material.SALMON, 0.60);
		put(p, Material.COOKED_SALMON, 1.00);

		// Nether / end valuables
		put(p, Material.GLOWSTONE_DUST, 1.00);
		put(p, Material.GLOWSTONE, 4.00);
		put(p, Material.NETHER_BRICK, 0.40);
		put(p, Material.SOUL_SAND, 0.50);
		put(p, Material.SOUL_SOIL, 0.50);
		put(p, Material.BLACKSTONE, 0.25);
		put(p, Material.BASALT, 0.20);
		put(p, Material.CHORUS_FRUIT, 1.50);
		put(p, Material.POPPED_CHORUS_FRUIT, 2.00);
		put(p, Material.PURPUR_BLOCK, 2.50);
		put(p, Material.ENDER_EYE, 20.00);
		put(p, Material.DRAGON_BREATH, 30.00);

		// Ocean / rare
		put(p, Material.PRISMARINE_SHARD, 1.50);
		put(p, Material.PRISMARINE_CRYSTALS, 2.50);
		put(p, Material.NAUTILUS_SHELL, 40.00);
		put(p, Material.HEART_OF_THE_SEA, 200.00);
		put(p, Material.TRIDENT, 150.00);
		put(p, Material.SPONGE, 35.00);
		put(p, Material.WET_SPONGE, 30.00);

		// Misc useful
		put(p, Material.FLINT, 0.30);
		put(p, Material.STICK, 0.05);
		put(p, Material.PAPER, 0.40);
		put(p, Material.BOOK, 2.00);
		put(p, Material.EXPERIENCE_BOTTLE, 8.00);
		put(p, Material.NAME_TAG, 25.00);
		put(p, Material.SADDLE, 40.00);
		put(p, Material.ENDER_CHEST, 60.00);
		put(p, Material.SHULKER_BOX, 200.00);
		put(p, Material.ELYTRA, 1500.00);
		put(p, Material.NETHER_STAR, 2000.00);
		put(p, Material.BEACON, 2500.00);
		put(p, Material.DRAGON_EGG, 5000.00);
		put(p, Material.ENCHANTED_BOOK, 15.00);
		put(p, Material.IRON_HORSE_ARMOR, 20.00);
		put(p, Material.GOLDEN_HORSE_ARMOR, 35.00);
		put(p, Material.DIAMOND_HORSE_ARMOR, 80.00);

		return p;
	}

	private static void put(Map<Material, Double> map, Material material, double price) {
		if (material != null) {
			map.put(material, price);
		}
	}
}
