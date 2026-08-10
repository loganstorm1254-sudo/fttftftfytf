package com.chunkboomerits.paper.economy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;
import com.chunkboomerits.paper.OpItems;

/**
 * Fixed per-material sell prices for every item/block in the game.
 * DonutSMP auction-scale /sell prices (inspired by donut.build AH rates).
 */
public final class SellService {
	/** Bump when default price table changes — auto-regenerates sell-prices.yml. */
	private static final int PRICE_TABLE_VERSION = 5;

	private final ChunkBoomeritsPlugin plugin;
	private final File file;
	private final Map<Material, Double> prices = new LinkedHashMap<>();

	public SellService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "sell-prices.yml");
	}

	public void load() {
		prices.clear();
		Map<Material, Double> defaults = defaultPrices();
		if (!file.exists()) {
			prices.putAll(defaults);
			save();
			plugin.getLogger().info("Created sell-prices.yml with " + prices.size() + " Donut-scale sell prices.");
			return;
		}

		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
		int fileVersion = data.getInt("version", 1);
		if (fileVersion < PRICE_TABLE_VERSION) {
			prices.putAll(defaults);
			save();
			plugin.getLogger().info("Upgraded sell-prices.yml to Donut-scale v" + PRICE_TABLE_VERSION
					+ " (" + prices.size() + " prices).");
			return;
		}

		if (data.isConfigurationSection("prices")) {
			for (String key : data.getConfigurationSection("prices").getKeys(false)) {
				Material mat = Material.matchMaterial(key);
				if (mat == null || !mat.isItem() || isExcluded(mat)) {
					continue;
				}
				double price = data.getDouble("prices." + key, -1);
				if (price >= 0) {
					prices.put(mat, round(price));
				}
			}
		}
		int added = 0;
		for (Map.Entry<Material, Double> entry : defaults.entrySet()) {
			if (!prices.containsKey(entry.getKey())) {
				prices.put(entry.getKey(), entry.getValue());
				added++;
			}
		}
		if (added > 0) {
			save();
		}
		plugin.getLogger().info("Loaded " + prices.size() + " sell prices (+" + added + " new materials).");
	}

	public void fillMissing() {
		int before = prices.size();
		for (Map.Entry<Material, Double> entry : defaultPrices().entrySet()) {
			prices.putIfAbsent(entry.getKey(), entry.getValue());
		}
		save();
		plugin.getLogger().info("Sell prices filled: " + before + " -> " + prices.size());
	}

	public void regenerateAll() {
		prices.clear();
		prices.putAll(defaultPrices());
		save();
		plugin.getLogger().info("Regenerated sell-prices.yml with " + prices.size() + " prices.");
	}

	public void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("version", PRICE_TABLE_VERSION);
		data.options().header(String.join("\n",
				"DonutSMP auction-scale sell prices (donut.build-inspired).",
				"Examples: sand $100, oak log $300, diamond $1,200, leather $10,000, debris $1,700,000.",
				"Edit any value; /sell reload applies. /sell fill adds missing only.",
				"/sell regenerate (OP) rebuilds ALL prices from defaults."
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

	/** OP / shovel: set or change a material's /sell price (0 = not sellable). */
	public void setPrice(Material material, double price) {
		if (material == null || isExcluded(material) || !material.isItem()) {
			return;
		}
		prices.put(material, round(Math.max(0, price)));
		save();
	}

	/** Reset one material to the built-in default price. */
	public void resetPrice(Material material) {
		if (material == null || isExcluded(material) || !material.isItem()) {
			return;
		}
		prices.put(material, computePrice(material));
		save();
	}

	public double defaultPriceOf(Material material) {
		if (material == null || isExcluded(material) || !material.isItem()) {
			return 0;
		}
		return computePrice(material);
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
				|| OpItems.isBanSword(stack) || OpItems.isCustomDisc(stack)) {
			return false;
		}
		Double price = prices.get(stack.getType());
		return price != null && price > 0;
	}

	public Map<Material, Double> allPrices() {
		return Collections.unmodifiableMap(prices);
	}

	private static double round(double amount) {
		return Math.round(amount * 100.0) / 100.0;
	}

	private static boolean isExcluded(Material mat) {
		if (mat == null || mat.isAir()) {
			return true;
		}
		String name = mat.name();
		return name.startsWith("LEGACY_") || name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR");
	}

	static Map<Material, Double> defaultPrices() {
		Map<Material, Double> map = new LinkedHashMap<>();
		for (Material mat : Material.values()) {
			if (isExcluded(mat) || !mat.isItem()) {
				continue;
			}
			map.put(mat, computePrice(mat));
		}
		return map;
	}

	/**
	 * DonutSMP auction-scale prices (donut.build AH / wiki /sell anchors).
	 */
	static double computePrice(Material mat) {
		String n = mat.name();

		// === Confirmed DonutSMP wiki / donut.build AH-scale anchors ===
		if (n.equals("OAK_LOG")) return 300.00;
		if (n.equals("SAND")) return 100.00;
		if (n.equals("DIAMOND")) return 1_200.00;
		if (n.equals("NETHER_GOLD_ORE")) return 1_200.00;
		if (n.equals("LEATHER")) return 10_000.00;
		if (n.equals("BAMBOO_BLOCK")) return 500.00;
		if (n.equals("DRIED_KELP_BLOCK")) return 550.00;
		if (n.equals("ANCIENT_DEBRIS")) return 1_700_000.00;
		// donut.build auction comps for common bulk items
		if (n.equals("CLAY_BALL")) return 50.00;
		if (n.equals("WHEAT_SEEDS")) return 80.00;
		if (n.equals("RED_SAND")) return 200.00;
		if (n.equals("SPRUCE_PLANKS") || n.equals("OAK_SLAB") || n.equals("COBBLESTONE_SLAB")) return 160.00;
		if (n.equals("GILDED_BLACKSTONE")) return 850_000.00;

		// === Ultra rares ===
		if (n.equals("DRAGON_EGG")) return 50_000_000.00;
		if (n.equals("NETHER_STAR")) return 25_000_000.00;
		if (n.equals("BEACON")) return 28_000_000.00;
		if (n.equals("ELYTRA")) return 15_000_000.00;
		if (n.equals("ENCHANTED_GOLDEN_APPLE")) return 8_000_000.00;
		if (n.equals("HEAVY_CORE") || n.equals("MACE")) return 12_000_000.00;
		if (n.equals("TOTEM_OF_UNDYING")) return 2_500_000.00;
		if (n.equals("HEART_OF_THE_SEA")) return 1_500_000.00;
		if (n.equals("CONDUIT")) return 3_000_000.00;
		if (n.equals("SHULKER_SHELL")) return 750_000.00;
		if (n.contains("SHULKER_BOX")) return 3_000_000.00;
		if (n.equals("DRAGON_HEAD")) return 5_000_000.00;
		if (n.equals("DRAGON_BREATH")) return 250_000.00;
		if (n.equals("WITHER_SKELETON_SKULL")) return 2_000_000.00;
		if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) return 150_000.00;
		if (n.equals("SPAWNER") || n.equals("TRIAL_SPAWNER") || n.equals("VAULT")) return 10_000_000.00;
		if (n.equals("ECHO_SHARD")) return 500_000.00;
		if (n.equals("RECOVERY_COMPASS")) return 2_000_000.00;
		if (n.equals("TRIDENT")) return 1_200_000.00;
		if (n.equals("NAUTILUS_SHELL")) return 400_000.00;
		if (n.equals("SPONGE") || n.equals("WET_SPONGE")) return 350_000.00;
		if (n.equals("NAME_TAG")) return 200_000.00;
		if (n.equals("SADDLE")) return 250_000.00;
		if (n.equals("END_CRYSTAL")) return 400_000.00;
		if (n.equals("GHAST_TEAR")) return 180_000.00;
		if (n.equals("PHANTOM_MEMBRANE")) return 80_000.00;
		if (n.equals("BREEZE_ROD")) return 150_000.00;
		if (n.equals("WIND_CHARGE")) return 25_000.00;

		// === Netherite (debris confirmed $1.7M) ===
		if (n.equals("NETHERITE_SCRAP")) return 1_800_000.00;
		if (n.equals("NETHERITE_INGOT")) return 8_000_000.00;
		if (n.equals("NETHERITE_BLOCK")) return 72_000_000.00;
		if (n.contains("NETHERITE")) {
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.endsWith("_SPEAR")) {
				return 10_000_000.00;
			}
			if (n.contains("UPGRADE")) return 5_000_000.00;
			return 2_000_000.00;
		}

		// === Diamond (gem confirmed $1,200) ===
		if (n.contains("DIAMOND")) {
			if (n.contains("BLOCK")) return 10_800.00;
			if (n.contains("ORE")) return 1_000.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("HORSE_ARMOR")
					|| n.endsWith("_SPEAR")) {
				return 8_000.00;
			}
			return 1_000.00;
		}

		// === Emerald ===
		if (n.contains("EMERALD")) {
			if (n.contains("BLOCK")) return 13_500.00;
			if (n.equals("EMERALD")) return 1_500.00;
			if (n.contains("ORE")) return 1_200.00;
			return 1_000.00;
		}

		// === Gold (nether gold ore confirmed $1,200) ===
		if (n.equals("GOLDEN_APPLE")) return 75_000.00;
		if (n.equals("GOLDEN_CARROT")) return 8_000.00;
		if (n.contains("GOLD") || n.startsWith("GOLDEN_")) {
			if (n.contains("BLOCK")) return 9_000.00;
			if (n.contains("INGOT")) return 1_000.00;
			if (n.contains("NUGGET")) return 110.00;
			if (n.contains("ORE") || n.contains("RAW_GOLD")) return 800.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("HORSE_ARMOR")
					|| n.endsWith("_SPEAR")) {
				return 4_000.00;
			}
			return 900.00;
		}

		// === Iron ===
		if (n.equals("IRON_BARS") || n.equals("IRON_DOOR") || n.equals("IRON_TRAPDOOR") || n.equals("CHAIN")
				|| n.equals("HEAVY_WEIGHTED_PRESSURE_PLATE")) {
			return 400.00;
		}
		if (n.contains("IRON")) {
			if (n.contains("BLOCK")) return 4_500.00;
			if (n.contains("INGOT")) return 500.00;
			if (n.contains("NUGGET")) return 55.00;
			if (n.contains("ORE") || n.contains("RAW_IRON")) return 400.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("HORSE_ARMOR")
					|| n.endsWith("_SPEAR")) {
				return 2_500.00;
			}
			return 450.00;
		}

		// === Copper ===
		if (n.contains("COPPER") || n.contains("EXPOSED_") || n.contains("WEATHERED_") || n.contains("OXIDIZED_")) {
			if (n.contains("BLOCK") || n.contains("CUT_") || n.contains("CHISELED_") || n.contains("GRATE")
					|| n.contains("BULB") || n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("LANTERN")) {
				return 1_200.00;
			}
			if (n.contains("INGOT")) return 250.00;
			if (n.contains("ORE") || n.contains("RAW_COPPER")) return 200.00;
			if (n.contains("NUGGET")) return 28.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.endsWith("_SPEAR")) {
				return 1_200.00;
			}
			return 300.00;
		}

		// === Coal / redstone / lapis / quartz / amethyst ===
		if (n.equals("COAL") || n.equals("CHARCOAL")) return 200.00;
		if (n.equals("COAL_BLOCK")) return 1_800.00;
		if (n.contains("COAL_ORE")) return 250.00;
		if (n.equals("REDSTONE")) return 250.00;
		if (n.equals("REDSTONE_BLOCK")) return 2_250.00;
		if (n.contains("REDSTONE_ORE")) return 300.00;
		if (n.equals("LAPIS_LAZULI")) return 300.00;
		if (n.equals("LAPIS_BLOCK")) return 2_700.00;
		if (n.contains("LAPIS_ORE")) return 350.00;
		if (n.equals("QUARTZ")) return 350.00;
		if (n.contains("QUARTZ") && n.contains("BLOCK")) return 1_400.00;
		if (n.contains("NETHER_QUARTZ_ORE")) return 400.00;
		if (n.equals("AMETHYST_SHARD")) return 400.00;
		if (n.contains("AMETHYST")) return 1_200.00;
		if (n.equals("GILDED_BLACKSTONE")) return 50_000.00;

		// === Spawn eggs / buckets ===
		if (n.endsWith("_SPAWN_EGG")) return 500_000.00;
		if (n.equals("BUCKET")) return 400.00;
		if (n.equals("WATER_BUCKET") || n.equals("LAVA_BUCKET") || n.equals("POWDER_SNOW_BUCKET")
				|| n.equals("MILK_BUCKET")) {
			return 800.00;
		}
		if (n.contains("BUCKET")) return 50_000.00; // fish / axolotl / tadpole buckets

		// === Discs / pottery / templates ===
		if (n.startsWith("MUSIC_DISC_")) return 250_000.00;
		if (n.endsWith("_POTTERY_SHERD")) return 80_000.00;
		if (n.endsWith("_SMITHING_TEMPLATE") || n.contains("ARMOR_TRIM") || n.endsWith("_TRIM")) return 400_000.00;
		if (n.equals("ENCHANTED_BOOK")) return 100_000.00;
		if (n.equals("EXPERIENCE_BOTTLE")) return 15_000.00;

		// === Mob drops (leather confirmed $10,000) ===
		if (n.equals("ENDER_PEARL")) return 25_000.00;
		if (n.equals("ENDER_EYE")) return 40_000.00;
		if (n.equals("BLAZE_ROD")) return 35_000.00;
		if (n.equals("BLAZE_POWDER")) return 18_000.00;
		if (n.equals("MAGMA_CREAM")) return 12_000.00;
		if (n.equals("SLIME_BALL")) return 8_000.00;
		if (n.equals("SLIME_BLOCK")) return 72_000.00;
		if (n.equals("GUNPOWDER")) return 5_000.00;
		if (n.equals("BONE")) return 2_000.00;
		if (n.equals("BONE_BLOCK")) return 18_000.00;
		if (n.equals("BONE_MEAL")) return 250.00;
		if (n.equals("STRING")) return 1_500.00;
		if (n.equals("ROTTEN_FLESH")) return 200.00;
		if (n.equals("SPIDER_EYE") || n.equals("FERMENTED_SPIDER_EYE")) return 3_000.00;
		if (n.equals("FEATHER")) return 1_200.00;
		if (n.equals("ARROW") || n.contains("_ARROW")) return 800.00;
		if (n.equals("PRISMARINE_SHARD")) return 2_000.00;
		if (n.equals("PRISMARINE_CRYSTALS")) return 4_000.00;
		if (n.equals("INK_SAC") || n.equals("GLOW_INK_SAC")) return 1_500.00;
		if (n.equals("RABBIT_HIDE") || n.equals("RABBIT_FOOT")) return 8_000.00;
		if (n.equals("SCUTE") || n.equals("TURTLE_SCUTE") || n.equals("ARMADILLO_SCUTE")) return 50_000.00;

		// === Farm / crop (bamboo block $500, dried kelp block $550) ===
		if (n.equals("BAMBOO")) return 50.00;
		if (n.equals("STRIPPED_BAMBOO_BLOCK")) return 520.00;
		if (n.equals("KELP")) return 40.00;
		if (n.equals("DRIED_KELP")) return 60.00;
		if (n.equals("SUGAR_CANE")) return 80.00;
		if (n.equals("CACTUS")) return 100.00;
		if (n.equals("NETHER_WART")) return 500.00;
		if (n.equals("WHEAT") || n.equals("CARROT") || n.equals("POTATO") || n.equals("BEETROOT")) return 150.00;
		if (n.endsWith("_SEEDS") || n.equals("PITCHER_POD") || n.equals("TORCHFLOWER_SEEDS")) return 50.00;
		if (n.equals("APPLE")) return 400.00;
		if (n.equals("BREAD")) return 500.00;
		if (n.equals("HAY_BLOCK")) return 1_350.00;
		if (n.equals("MELON") || n.equals("MELON_SLICE") || n.equals("PUMPKIN")
				|| n.equals("CARVED_PUMPKIN") || n.equals("JACK_O_LANTERN")) {
			return n.equals("MELON_SLICE") ? 80.00 : 400.00;
		}
		if (n.startsWith("COOKED_")) return 600.00;
		if (n.equals("BEEF") || n.equals("PORKCHOP") || n.equals("CHICKEN") || n.equals("MUTTON")
				|| n.equals("RABBIT") || n.equals("COD") || n.equals("SALMON") || n.equals("TROPICAL_FISH")
				|| n.equals("PUFFERFISH")) {
			return 350.00;
		}
		if (mat.isEdible()) return 400.00;

		// === Wood / logs (oak log confirmed $300) ===
		if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || n.endsWith("_WOOD")) return 300.00;
		if (n.contains("STRIPPED_") && (n.contains("LOG") || n.contains("STEM") || n.contains("WOOD") || n.contains("HYPHAE"))) {
			return 320.00;
		}
		if (n.endsWith("_PLANKS")) return 100.00;
		if (n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE")) return 200.00;
		if (n.endsWith("_LEAVES") || n.equals("LEAF_LITTER")) return 40.00;
		if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_FENCE") || n.endsWith("_GATE")
				|| n.endsWith("_STAIRS") || n.endsWith("_SLAB") || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE")
				|| n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN") || n.endsWith("_SHELF")) {
			return 150.00;
		}
		if (n.endsWith("_BOAT") || n.endsWith("_RAFT") || n.endsWith("_CHEST_BOAT") || n.endsWith("_CHEST_RAFT")) {
			return 1_200.00;
		}

		// === Stone / dirt (sand confirmed $100) ===
		if (n.equals("COBBLESTONE") || n.equals("COBBLED_DEEPSLATE")) return 50.00;
		if (n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("GRANITE") || n.equals("DIORITE")
				|| n.equals("ANDESITE") || n.equals("TUFF") || n.equals("CALCITE") || n.equals("DRIPSTONE_BLOCK")) {
			return 60.00;
		}
		if (n.equals("NETHERRACK") || n.equals("BASALT") || n.equals("BLACKSTONE")) return 40.00;
		if (n.equals("END_STONE")) return 200.00;
		if (n.equals("DIRT") || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT") || n.equals("MUD")
				|| n.equals("GRAVEL") || n.equals("RED_SAND") || n.equals("CLAY")
				|| n.equals("SOUL_SAND") || n.equals("SOUL_SOIL")) {
			if (n.equals("CLAY")) return 200.00;
			if (n.equals("SOUL_SAND") || n.equals("SOUL_SOIL")) return 150.00;
			if (n.equals("RED_SAND")) return 100.00;
			return 50.00;
		}
		if (n.equals("OBSIDIAN")) return 5_000.00;
		if (n.equals("CRYING_OBSIDIAN")) return 8_000.00;
		if (n.equals("GLOWSTONE")) return 2_000.00;
		if (n.equals("GLOWSTONE_DUST")) return 500.00;
		if (n.contains("TERRACOTTA") || n.contains("CONCRETE") || n.endsWith("_WOOL") || n.endsWith("_CARPET")
				|| n.contains("GLAZED_TERRACOTTA")) {
			return 300.00;
		}
		if (n.contains("GLASS") || n.contains("STAINED_GLASS")) return 150.00;
		if (n.endsWith("_BED")) return 2_000.00;
		if (n.endsWith("_BANNER") || n.equals("SHIELD")) return 1_500.00;

		// === Rails / machines ===
		if (n.contains("RAIL") || n.equals("MINECART") || n.contains("_MINECART")) return 2_000.00;
		if (n.equals("ENCHANTING_TABLE") || n.equals("ENDER_CHEST") || n.equals("ANVIL")
				|| n.equals("CHIPPED_ANVIL") || n.equals("DAMAGED_ANVIL")) {
			return 50_000.00;
		}
		if (n.equals("HOPPER") || n.equals("BREWING_STAND") || n.equals("BEACON")) return 25_000.00;
		if (n.equals("REDSTONE_TORCH") || n.equals("REPEATER") || n.equals("COMPARATOR") || n.equals("PISTON")
				|| n.equals("STICKY_PISTON") || n.equals("DISPENSER") || n.equals("DROPPER") || n.equals("OBSERVER")
				|| n.equals("DAYLIGHT_DETECTOR") || n.equals("TARGET") || n.equals("LECTERN") || n.equals("NOTE_BLOCK")
				|| n.equals("JUKEBOX") || n.equals("CRAFTING_TABLE") || n.equals("FURNACE") || n.equals("BLAST_FURNACE")
				|| n.equals("SMOKER") || n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL")
				|| n.equals("CAMPFIRE") || n.equals("SOUL_CAMPFIRE") || n.equals("LANTERN") || n.equals("SOUL_LANTERN")
				|| n.equals("TORCH") || n.equals("SOUL_TORCH") || n.equals("COPPER_TORCH") || n.equals("COPPER_LANTERN")
				|| n.equals("LOOM") || n.equals("CARTOGRAPHY_TABLE") || n.equals("FLETCHING_TABLE")
				|| n.equals("SMITHING_TABLE") || n.equals("GRINDSTONE") || n.equals("STONECUTTER")
				|| n.equals("COMPOSTER") || n.equals("CAULDRON") || n.equals("BELL") || n.equals("RESPAWN_ANCHOR")) {
			return 2_500.00;
		}

		// === Potions ===
		if (n.equals("GLASS_BOTTLE")) return 200.00;
		if (n.contains("POTION") || n.equals("HONEY_BOTTLE")) return 8_000.00;

		// === Coral / flowers / plants ===
		if (n.contains("CORAL")) return 2_000.00;
		if (n.endsWith("_FLOWER") || n.equals("DANDELION") || n.equals("POPPY") || n.equals("BLUE_ORCHID")
				|| n.equals("ALLIUM") || n.equals("AZURE_BLUET") || n.equals("OXEYE_DAISY") || n.equals("CORNFLOWER")
				|| n.equals("LILY_OF_THE_VALLEY") || n.equals("TORCHFLOWER") || n.equals("PITCHER_PLANT")
				|| n.equals("WITHER_ROSE") || n.equals("SUNFLOWER") || n.equals("LILAC") || n.equals("ROSE_BUSH")
				|| n.equals("PEONY") || n.equals("PINK_PETALS") || n.equals("WILDFLOWERS")) {
			return n.equals("WITHER_ROSE") ? 25_000.00 : 200.00;
		}
		if (n.contains("MUSHROOM") || n.equals("BROWN_MUSHROOM") || n.equals("RED_MUSHROOM") || n.equals("CRIMSON_FUNGUS")
				|| n.equals("WARPED_FUNGUS") || n.equals("NETHER_SPROUTS") || n.equals("WARPED_ROOTS")
				|| n.equals("CRIMSON_ROOTS") || n.equals("WEEPING_VINES") || n.equals("TWISTING_VINES")
				|| n.equals("VINE") || n.equals("GLOW_LICHEN") || n.equals("MOSS_BLOCK") || n.equals("MOSS_CARPET")
				|| n.equals("PALE_MOSS_BLOCK") || n.equals("PALE_MOSS_CARPET") || n.equals("HANGING_ROOTS")
				|| n.equals("BIG_DRIPLEAF") || n.equals("SMALL_DRIPLEAF") || n.equals("SPORE_BLOSSOM")
				|| n.equals("SEA_PICKLE") || n.equals("LILY_PAD") || n.equals("DEAD_BUSH") || n.equals("FERN")
				|| n.equals("LARGE_FERN") || n.equals("SHORT_GRASS") || n.equals("TALL_GRASS") || n.equals("BUSH")
				|| n.equals("FIREFLY_BUSH") || n.equals("CACTUS_FLOWER") || n.equals("TALL_DRY_GRASS")
				|| n.equals("SHORT_DRY_GRASS")) {
			return 100.00;
		}

		// === Ice / snow ===
		if (n.contains("ICE") || n.contains("SNOW") || n.equals("POWDER_SNOW_BUCKET")) return 200.00;

		// === Honey / candles ===
		if (n.contains("CANDLE") || n.equals("CAKE") || n.contains("HONEYCOMB") || n.equals("HONEY_BLOCK")
				|| n.equals("HONEYCOMB_BLOCK") || n.equals("BEEHIVE") || n.equals("BEE_NEST")) {
			return 3_000.00;
		}

		// === Dyes / paper / books ===
		if (n.endsWith("_DYE")) return 400.00;
		if (n.equals("PAPER") || n.equals("MAP") || n.equals("FILLED_MAP")) return 200.00;
		if (n.equals("BOOK") || n.equals("WRITABLE_BOOK") || n.equals("WRITTEN_BOOK") || n.equals("KNOWLEDGE_BOOK")) {
			return 1_500.00;
		}

		// === Explosives ===
		if (n.equals("TNT") || n.equals("TNT_MINECART") || n.equals("FIREWORK_ROCKET") || n.equals("FIREWORK_STAR")
				|| n.equals("FIRE_CHARGE")) {
			return 5_000.00;
		}

		// === Creative / rare blocks ===
		if (n.equals("ARMOR_STAND") || n.equals("ITEM_FRAME") || n.equals("GLOW_ITEM_FRAME") || n.equals("PAINTING")
				|| n.equals("FLOWER_POT")) {
			return 2_000.00;
		}
		if (n.equals("LIGHT") || n.equals("BARRIER") || n.equals("STRUCTURE_VOID") || n.equals("STRUCTURE_BLOCK")
				|| n.equals("JIGSAW") || n.equals("COMMAND_BLOCK") || n.equals("CHAIN_COMMAND_BLOCK")
				|| n.equals("REPEATING_COMMAND_BLOCK") || n.equals("COMMAND_BLOCK_MINECART") || n.equals("DEBUG_STICK")
				|| n.equals("CREAKING_HEART")) {
			return 1_000_000.00;
		}

		// === Tools / armor leftover ===
		if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE") || n.contains("SHOVEL")
				|| n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS")
				|| n.equals("BOW") || n.equals("CROSSBOW") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL")
				|| n.equals("SHEARS") || n.equals("BRUSH") || n.equals("SPYGLASS") || n.equals("COMPASS")
				|| n.equals("CLOCK") || n.equals("LEAD") || n.equals("GOAT_HORN") || n.equals("CARROT_ON_A_STICK")
				|| n.equals("WARPED_FUNGUS_ON_A_STICK") || n.endsWith("_SPEAR") || n.contains("NAUTILUS_ARMOR")) {
			if (n.startsWith("WOODEN_") || n.startsWith("LEATHER_")) return 1_000.00;
			if (n.startsWith("STONE_") || n.startsWith("CHAINMAIL_")) return 2_000.00;
			if (n.contains("NAUTILUS_ARMOR")) return 500_000.00;
			return 3_000.00;
		}

		try {
			if (Tag.LOGS.isTagged(mat)) return 300.00;
			if (Tag.PLANKS.isTagged(mat)) return 100.00;
			if (Tag.WOOL.isTagged(mat)) return 300.00;
			if (Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(mat)) return 3_000.00;
		} catch (Throwable ignored) {
			// ignore tag edge cases
		}

		if (mat.isBlock()) {
			return 100.00;
		}
		return 250.00;
	}
}
