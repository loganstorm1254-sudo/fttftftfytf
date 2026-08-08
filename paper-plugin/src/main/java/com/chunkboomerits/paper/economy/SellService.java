package com.chunkboomerits.paper.economy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
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
 * Same material always sells for the same price.
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
		Map<Material, Double> defaults = defaultPrices();
		if (!file.exists()) {
			prices.putAll(defaults);
			save();
			plugin.getLogger().info("Created sell-prices.yml with " + prices.size() + " fixed prices (all items/blocks).");
			return;
		}
		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
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
		// Fill in every missing item/block — never changes prices you already set
		int added = 0;
		for (Map.Entry<Material, Double> entry : defaults.entrySet()) {
			if (!prices.containsKey(entry.getKey())) {
				prices.put(entry.getKey(), entry.getValue());
				added++;
			}
		}
		if (added > 0 || !file.exists()) {
			save();
		}
		plugin.getLogger().info("Loaded " + prices.size() + " sell prices (+" + added + " new materials).");
	}

	/** OP: rebuild missing entries only (keeps custom overrides). */
	public void fillMissing() {
		int before = prices.size();
		for (Map.Entry<Material, Double> entry : defaultPrices().entrySet()) {
			prices.putIfAbsent(entry.getKey(), entry.getValue());
		}
		save();
		plugin.getLogger().info("Sell prices filled: " + before + " -> " + prices.size());
	}

	/** OP: rewrite ALL prices from the formula (overwrites custom edits). */
	public void regenerateAll() {
		prices.clear();
		prices.putAll(defaultPrices());
		save();
		plugin.getLogger().info("Regenerated sell-prices.yml with " + prices.size() + " prices.");
	}

	public void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("version", 2);
		data.options().header(String.join("\n",
				"Fixed sell prices for every item/block — same material = same price always.",
				"Edit any value; /sell reload applies. /sell fill adds missing materials only.",
				"/sell regenerate (OP) rebuilds ALL prices from defaults (overwrites edits)."
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

	/**
	 * Every non-legacy item Material gets a deterministic price.
	 */
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
	 * Deterministic formula — same material always returns the same price.
	 */
	static double computePrice(Material mat) {
		String n = mat.name();

		// Ultra rares / progression
		if (n.equals("DRAGON_EGG")) return 5000.00;
		if (n.equals("NETHER_STAR") || n.equals("BEACON")) return 2000.00;
		if (n.equals("ELYTRA")) return 1500.00;
		if (n.equals("HEAVY_CORE") || n.equals("MACE")) return 800.00;
		if (n.equals("ENCHANTED_GOLDEN_APPLE")) return 2500.00;
		if (n.equals("TOTEM_OF_UNDYING")) return 350.00;
		if (n.equals("HEART_OF_THE_SEA")) return 200.00;
		if (n.equals("CONDUIT")) return 400.00;
		if (n.equals("SHULKER_SHELL")) return 80.00;
		if (n.contains("SHULKER_BOX")) return 200.00;
		if (n.equals("DRAGON_HEAD") || n.equals("DRAGON_BREATH")) return 100.00;
		if (n.equals("WITHER_SKELETON_SKULL")) return 120.00;
		if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) return 25.00;

		// Netherite
		if (n.contains("NETHERITE")) {
			if (n.contains("BLOCK")) return 10800.00;
			if (n.contains("INGOT")) return 1200.00;
			if (n.contains("SCRAP") || n.equals("ANCIENT_DEBRIS")) return 250.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS")) return 1500.00;
			return 400.00;
		}

		// Diamond
		if (n.contains("DIAMOND")) {
			if (n.contains("BLOCK")) return 450.00;
			if (n.equals("DIAMOND")) return 50.00;
			if (n.contains("ORE")) return 40.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("HORSE_ARMOR")) return 120.00;
			return 35.00;
		}

		// Emerald
		if (n.contains("EMERALD")) {
			if (n.contains("BLOCK")) return 315.00;
			if (n.equals("EMERALD")) return 35.00;
			if (n.contains("ORE")) return 28.00;
			return 20.00;
		}

		// Gold
		if (n.contains("GOLD") && !n.contains("GOLDEN_APPLE") && !n.contains("GOLDEN_CARROT")) {
			if (n.contains("BLOCK")) return 90.00;
			if (n.contains("INGOT")) return 10.00;
			if (n.contains("NUGGET")) return 1.10;
			if (n.contains("ORE") || n.contains("RAW_GOLD")) return 6.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("HORSE_ARMOR")) return 25.00;
			return 8.00;
		}
		if (n.equals("GOLDEN_APPLE")) return 75.00;
		if (n.equals("GOLDEN_CARROT")) return 8.00;

		// Iron
		if (n.contains("IRON") && !n.contains("IRON_BARS") && !n.contains("IRON_DOOR") && !n.contains("IRON_TRAPDOOR")
				&& !n.contains("IRON_CHAIN") && !n.contains("IRON_BARS")) {
			if (n.contains("BLOCK")) return 45.00;
			if (n.contains("INGOT")) return 5.00;
			if (n.contains("NUGGET")) return 0.55;
			if (n.contains("ORE") || n.contains("RAW_IRON")) return 3.00;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("HORSE_ARMOR")) return 15.00;
		}
		if (n.equals("IRON_BARS") || n.equals("IRON_DOOR") || n.equals("IRON_TRAPDOOR") || n.equals("CHAIN")) {
			return 2.00;
		}

		// Copper
		if (n.contains("COPPER") || n.contains("EXPOSED_") || n.contains("WEATHERED_") || n.contains("OXIDIZED_")) {
			if (n.contains("BLOCK") || n.contains("CUT_") || n.contains("CHISELED_") || n.contains("GRATE")
					|| n.contains("BULB") || n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("LANTERN")) {
				return 4.00;
			}
			if (n.contains("INGOT")) return 2.50;
			if (n.contains("ORE") || n.contains("RAW_COPPER")) return 1.50;
			if (n.contains("NUGGET")) return 0.25;
			if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE")
					|| n.contains("SHOVEL") || n.contains("HELMET") || n.contains("CHESTPLATE")
					|| n.contains("LEGGINGS") || n.contains("BOOTS")) return 8.00;
			return 2.00;
		}

		// Coal / redstone / lapis / quartz / amethyst
		if (n.equals("COAL") || n.equals("CHARCOAL")) return 1.00;
		if (n.contains("COAL_ORE") || n.equals("COAL_BLOCK")) return n.contains("BLOCK") ? 9.00 : 1.50;
		if (n.equals("REDSTONE")) return 1.25;
		if (n.equals("REDSTONE_BLOCK")) return 11.00;
		if (n.contains("REDSTONE_ORE")) return 2.00;
		if (n.equals("LAPIS_LAZULI")) return 2.00;
		if (n.equals("LAPIS_BLOCK")) return 18.00;
		if (n.contains("LAPIS_ORE")) return 3.00;
		if (n.equals("QUARTZ")) return 2.50;
		if (n.contains("QUARTZ") && n.contains("BLOCK")) return 10.00;
		if (n.contains("NETHER_QUARTZ_ORE")) return 3.00;
		if (n.equals("AMETHYST_SHARD")) return 3.00;
		if (n.contains("AMETHYST")) return 5.00;

		// Spawn eggs / buckets of mobs
		if (n.endsWith("_SPAWN_EGG")) return 50.00;
		if (n.equals("BUCKET")) return 3.00;
		if (n.contains("BUCKET")) return 8.00;

		// Music discs / pottery / templates
		if (n.startsWith("MUSIC_DISC_")) return 40.00;
		if (n.endsWith("_POTTERY_SHERD")) return 15.00;
		if (n.endsWith("_SMITHING_TEMPLATE") || n.contains("ARMOR_TRIM") || n.endsWith("_TRIM")) return 30.00;
		if (n.equals("ENCHANTED_BOOK")) return 15.00;
		if (n.equals("EXPERIENCE_BOTTLE")) return 8.00;

		// Mob drops / valuables
		if (n.equals("ENDER_PEARL")) return 12.00;
		if (n.equals("ENDER_EYE")) return 20.00;
		if (n.equals("BLAZE_ROD")) return 15.00;
		if (n.equals("BLAZE_POWDER")) return 8.00;
		if (n.equals("GHAST_TEAR")) return 25.00;
		if (n.equals("MAGMA_CREAM")) return 5.00;
		if (n.equals("SLIME_BALL")) return 3.00;
		if (n.equals("SLIME_BLOCK")) return 27.00;
		if (n.equals("GUNPOWDER")) return 2.00;
		if (n.equals("BONE") || n.equals("BONE_BLOCK") || n.equals("BONE_MEAL")) {
			if (n.equals("BONE_BLOCK")) return 4.50;
			if (n.equals("BONE_MEAL")) return 0.20;
			return 0.50;
		}
		if (n.equals("STRING")) return 0.60;
		if (n.equals("ROTTEN_FLESH")) return 0.20;
		if (n.equals("SPIDER_EYE") || n.equals("FERMENTED_SPIDER_EYE")) return 0.80;
		if (n.equals("LEATHER")) return 1.50;
		if (n.equals("FEATHER")) return 0.40;
		if (n.equals("ARROW") || n.contains("_ARROW")) return 0.30;
		if (n.equals("PHANTOM_MEMBRANE")) return 8.00;
		if (n.equals("ECHO_SHARD")) return 40.00;
		if (n.equals("BREEZE_ROD") || n.equals("WIND_CHARGE")) return 20.00;
		if (n.equals("PRISMARINE_SHARD")) return 1.50;
		if (n.equals("PRISMARINE_CRYSTALS")) return 2.50;
		if (n.equals("NAUTILUS_SHELL")) return 40.00;
		if (n.equals("TRIDENT")) return 150.00;
		if (n.equals("SPONGE") || n.equals("WET_SPONGE")) return 35.00;
		if (n.equals("NAME_TAG")) return 25.00;
		if (n.equals("SADDLE")) return 40.00;
		if (n.equals("END_CRYSTAL")) return 45.00;

		// Crops / food
		if (n.equals("WHEAT") || n.equals("CARROT") || n.equals("POTATO") || n.equals("BEETROOT")) return 0.40;
		if (n.endsWith("_SEEDS") || n.equals("PITCHER_POD") || n.equals("TORCHFLOWER_SEEDS")) return 0.10;
		if (n.equals("NETHER_WART")) return 1.50;
		if (n.equals("SUGAR_CANE") || n.equals("CACTUS") || n.equals("BAMBOO") || n.equals("KELP")) return 0.40;
		if (n.equals("APPLE")) return 1.00;
		if (n.equals("BREAD")) return 1.50;
		if (n.equals("HAY_BLOCK")) return 3.50;
		if (n.startsWith("COOKED_")) return 1.20;
		if (n.equals("BEEF") || n.equals("PORKCHOP") || n.equals("CHICKEN") || n.equals("MUTTON")
				|| n.equals("RABBIT") || n.equals("COD") || n.equals("SALMON")) return 0.70;
		if (mat.isEdible()) return 1.00;

		// Wood / logs
		if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || n.endsWith("_WOOD")) return 0.45;
		if (n.contains("STRIPPED_") && (n.contains("LOG") || n.contains("STEM") || n.contains("WOOD") || n.contains("HYPHAE"))) {
			return 0.50;
		}
		if (n.endsWith("_PLANKS")) return 0.15;
		if (n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE")) return 0.50;
		if (n.endsWith("_LEAVES") || n.equals("LEAF_LITTER")) return 0.05;
		if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_FENCE") || n.endsWith("_GATE")
				|| n.endsWith("_STAIRS") || n.endsWith("_SLAB") || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE")
				|| n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN") || n.endsWith("_SHELF")) {
			return 0.35;
		}
		if (n.endsWith("_BOAT") || n.endsWith("_RAFT") || n.endsWith("_CHEST_BOAT") || n.endsWith("_CHEST_RAFT")) {
			return 2.00;
		}

		// Stone / dirt family
		if (n.equals("COBBLESTONE") || n.equals("COBBLED_DEEPSLATE")) return 0.10;
		if (n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("GRANITE") || n.equals("DIORITE")
				|| n.equals("ANDESITE") || n.equals("TUFF") || n.equals("CALCITE") || n.equals("DRIPSTONE_BLOCK")) {
			return 0.15;
		}
		if (n.equals("NETHERRACK") || n.equals("BASALT") || n.equals("BLACKSTONE") || n.equals("END_STONE")) {
			return n.equals("END_STONE") ? 0.50 : 0.12;
		}
		if (n.equals("DIRT") || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT") || n.equals("MUD")
				|| n.equals("CLAY") || n.equals("GRAVEL") || n.equals("SAND") || n.equals("RED_SAND")
				|| n.equals("SOUL_SAND") || n.equals("SOUL_SOIL")) {
			if (n.equals("CLAY")) return 0.40;
			return 0.08;
		}
		if (n.equals("OBSIDIAN")) return 8.00;
		if (n.equals("CRYING_OBSIDIAN")) return 12.00;
		if (n.equals("GLOWSTONE") || n.equals("GLOWSTONE_DUST")) return n.equals("GLOWSTONE") ? 4.00 : 1.00;
		if (n.contains("TERRACOTTA") || n.contains("CONCRETE") || n.endsWith("_WOOL") || n.endsWith("_CARPET")
				|| n.contains("GLAZED_TERRACOTTA")) {
			return 0.60;
		}
		if (n.contains("GLASS") || n.contains("STAINED_GLASS")) return 0.30;
		if (n.endsWith("_BED")) return 3.00;
		if (n.endsWith("_BANNER") || n.equals("SHIELD")) return 2.50;

		// Rails / redstone components
		if (n.contains("RAIL") || n.equals("MINECART") || n.contains("_MINECART")) return 3.00;
		if (n.equals("REDSTONE_TORCH") || n.equals("REPEATER") || n.equals("COMPARATOR") || n.equals("PISTON")
				|| n.equals("STICKY_PISTON") || n.equals("DISPENSER") || n.equals("DROPPER") || n.equals("HOPPER")
				|| n.equals("OBSERVER") || n.equals("DAYLIGHT_DETECTOR") || n.equals("TARGET") || n.equals("LECTERN")
				|| n.equals("NOTE_BLOCK") || n.equals("JUKEBOX") || n.equals("CRAFTING_TABLE") || n.equals("FURNACE")
				|| n.equals("BLAST_FURNACE") || n.equals("SMOKER") || n.equals("BREWING_STAND") || n.equals("ANVIL")
				|| n.equals("CHIPPED_ANVIL") || n.equals("DAMAGED_ANVIL") || n.equals("ENCHANTING_TABLE")
				|| n.equals("ENDER_CHEST") || n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL")
				|| n.equals("CAMPFIRE") || n.equals("SOUL_CAMPFIRE") || n.equals("LANTERN") || n.equals("SOUL_LANTERN")
				|| n.equals("TORCH") || n.equals("SOUL_TORCH") || n.equals("LANTERN") || n.equals("COPPER_TORCH")
				|| n.equals("COPPER_LANTERN")) {
			if (n.equals("ENCHANTING_TABLE") || n.equals("ENDER_CHEST") || n.equals("ANVIL")) return 40.00;
			if (n.equals("HOPPER") || n.equals("BREWING_STAND")) return 20.00;
			return 4.00;
		}

		// Potions / tipped
		if (n.contains("POTION") || n.equals("GLASS_BOTTLE") || n.equals("HONEY_BOTTLE")) {
			if (n.equals("GLASS_BOTTLE")) return 0.50;
			return 6.00;
		}

		// Coral / flowers / plants
		if (n.contains("CORAL")) return 1.50;
		if (n.endsWith("_FLOWER") || n.equals("DANDELION") || n.equals("POPPY") || n.equals("BLUE_ORCHID")
				|| n.equals("ALLIUM") || n.equals("AZURE_BLUET") || n.equals("OXEYE_DAISY") || n.equals("CORNFLOWER")
				|| n.equals("LILY_OF_THE_VALLEY") || n.equals("TORCHFLOWER") || n.equals("PITCHER_PLANT")
				|| n.equals("WITHER_ROSE") || n.equals("SUNFLOWER") || n.equals("LILAC") || n.equals("ROSE_BUSH")
				|| n.equals("PEONY") || n.equals("PINK_PETALS") || n.equals("WILDFLOWERS") || n.equals("LEAF_LITTER")) {
			return 0.40;
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
			return 0.25;
		}

		// Ice / snow
		if (n.contains("ICE") || n.contains("SNOW") || n.equals("POWDER_SNOW_BUCKET")) return 0.50;

		// Candles / cakes / honey
		if (n.contains("CANDLE") || n.equals("CAKE") || n.contains("HONEYCOMB") || n.equals("HONEY_BLOCK")
				|| n.equals("HONEYCOMB_BLOCK") || n.equals("BEEHIVE") || n.equals("BEE_NEST")) {
			return 3.00;
		}

		// Dyes
		if (n.endsWith("_DYE")) return 0.75;

		// Books / paper / maps
		if (n.equals("PAPER") || n.equals("MAP") || n.equals("FILLED_MAP") || n.equals("BOOK")
				|| n.equals("WRITABLE_BOOK") || n.equals("WRITTEN_BOOK") || n.equals("KNOWLEDGE_BOOK")) {
			return n.contains("BOOK") ? 2.00 : 0.40;
		}

		// Fireworks / explosives
		if (n.equals("TNT") || n.equals("TNT_MINECART") || n.equals("FIREWORK_ROCKET") || n.equals("FIREWORK_STAR")
				|| n.equals("FIRE_CHARGE")) {
			return 5.00;
		}

		// Armor stands / frames / misc
		if (n.equals("ARMOR_STAND") || n.equals("ITEM_FRAME") || n.equals("GLOW_ITEM_FRAME") || n.equals("PAINTING")
				|| n.equals("FLOWER_POT") || n.equals("LIGHT") || n.equals("BARRIER") || n.equals("STRUCTURE_VOID")
				|| n.equals("STRUCTURE_BLOCK") || n.equals("JIGSAW") || n.equals("COMMAND_BLOCK")
				|| n.equals("CHAIN_COMMAND_BLOCK") || n.equals("REPEATING_COMMAND_BLOCK") || n.equals("COMMAND_BLOCK_MINECART")
				|| n.equals("DEBUG_STICK") || n.equals("KNOWLEDGE_BOOK") || n.equals("SPAWNER")
				|| n.equals("TRIAL_SPAWNER") || n.equals("VAULT") || n.equals("CREAKING_HEART")) {
			if (n.contains("COMMAND") || n.equals("STRUCTURE_BLOCK") || n.equals("JIGSAW") || n.equals("DEBUG_STICK")
					|| n.equals("BARRIER") || n.equals("LIGHT") || n.equals("SPAWNER") || n.equals("TRIAL_SPAWNER")
					|| n.equals("VAULT")) {
				return 100.00;
			}
			return 5.00;
		}

		// Swords / tools / armor generic (wood/stone/leather/chain)
		if (n.contains("SWORD") || n.contains("AXE") || n.contains("PICKAXE") || n.contains("HOE") || n.contains("SHOVEL")
				|| n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS")
				|| n.equals("BOW") || n.equals("CROSSBOW") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL")
				|| n.equals("SHEARS") || n.equals("BRUSH") || n.equals("SPYGLASS") || n.equals("RECOVERY_COMPASS")
				|| n.equals("COMPASS") || n.equals("CLOCK") || n.equals("LEAD") || n.equals("GOAT_HORN")
				|| n.equals("CARROT_ON_A_STICK") || n.equals("WARPED_FUNGUS_ON_A_STICK")) {
			if (n.startsWith("WOODEN_") || n.startsWith("LEATHER_")) return 2.00;
			if (n.startsWith("STONE_") || n.startsWith("CHAINMAIL_")) return 4.00;
			return 6.00;
		}

		// Spears (1.21.11)
		if (n.endsWith("_SPEAR")) return 25.00;

		// Nautilus armor
		if (n.contains("NAUTILUS_ARMOR")) return 60.00;

		// Try tags for leftover blocks
		try {
			if (Tag.LOGS.isTagged(mat) || Tag.PLANKS.isTagged(mat)) return 0.40;
			if (Tag.WOOL.isTagged(mat)) return 0.50;
			if (Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(mat)) return 10.00;
		} catch (Throwable ignored) {
			// tags may throw for non-keyed edge cases
		}

		// Generic block vs misc item fallback — still deterministic
		if (mat.isBlock()) {
			return 0.25;
		}
		return 0.50;
	}
}
