package com.chunkboomerits.paper;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpItems {
	public static NamespacedKey BOOMERITS_KEY;
	public static NamespacedKey KICK_SWORD_KEY;
	public static NamespacedKey KILL_HAMMER_KEY;
	public static NamespacedKey INVINCIBLE_HELMET_KEY;
	public static NamespacedKey SCOREBOARD_SHOVEL_KEY;
	public static NamespacedKey HOLE_FILLER_KEY;
	public static NamespacedKey HOLE_FILLER_MODE_KEY;

	private OpItems() {
	}

	public static void init(JavaPlugin plugin) {
		BOOMERITS_KEY = new NamespacedKey(plugin, "chunk_boomerits");
		KICK_SWORD_KEY = new NamespacedKey(plugin, "kick_sword");
		KILL_HAMMER_KEY = new NamespacedKey(plugin, "kill_hammer");
		INVINCIBLE_HELMET_KEY = new NamespacedKey(plugin, "invincible_helmet");
		SCOREBOARD_SHOVEL_KEY = new NamespacedKey(plugin, "scoreboard_shovel");
		HOLE_FILLER_KEY = new NamespacedKey(plugin, "hole_filler");
		HOLE_FILLER_MODE_KEY = new NamespacedKey(plugin, "hole_filler_mode");
		CustomDisc.init(plugin);
	}

	public static ItemStack createBoomerits(int amount) {
		ItemStack stack = new ItemStack(Material.FIRE_CHARGE, clampAmount(amount));
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text("Chunk Boomerits", NamedTextColor.GOLD)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP Tools", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Right-click: delete the chunk you hit", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false)
		));
		meta.getPersistentDataContainer().set(BOOMERITS_KEY, PersistentDataType.BYTE, (byte) 1);
		meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
		meta.setMaxStackSize(64);
		stack.setItemMeta(meta);
		return stack;
	}

	public static ItemStack createKickSword() {
		ItemStack stack = new ItemStack(Material.NETHERITE_SWORD);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text("Kick Sword", NamedTextColor.LIGHT_PURPLE)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP Tools", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Hit a player to kick them from the server", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false),
				Component.text("Does not deal damage — instant kick", NamedTextColor.DARK_GRAY)
						.decoration(TextDecoration.ITALIC, false)
		));
		meta.getPersistentDataContainer().set(KICK_SWORD_KEY, PersistentDataType.BYTE, (byte) 1);
		meta.setUnbreakable(true);
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
		stack.setItemMeta(meta);
		return stack;
	}

	public static ItemStack createKillHammer() {
		Material mat = firstAvailable(Material.MACE, Material.NETHERITE_AXE, Material.IRON_AXE);
		ItemStack stack = new ItemStack(mat);
		ItemMeta meta = requireMeta(stack, "Insta Kill Hammer");
		meta.displayName(Component.text("Insta Kill Hammer", NamedTextColor.RED)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP Tools", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Hit anything to kill it instantly", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false),
				Component.text(mat == Material.MACE ? "Mace texture" : "Axe stand-in (mace missing on this server)", NamedTextColor.DARK_GRAY)
						.decoration(TextDecoration.ITALIC, false)
		));
		meta.getPersistentDataContainer().set(KILL_HAMMER_KEY, PersistentDataType.BYTE, (byte) 1);
		meta.setUnbreakable(true);
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
		stack.setItemMeta(meta);
		return stack;
	}

	public static ItemStack createInvincibleHelmet() {
		Material copper = Material.matchMaterial("COPPER_HELMET");
		ItemStack stack;
		if (copper != null && copper.isItem()) {
			stack = new ItemStack(copper);
			ItemMeta meta = requireMeta(stack, "Invincible Copper Helmet");
			applyInvHelmetMeta(meta);
			stack.setItemMeta(meta);
			return stack;
		}

		// Fallback: leather helmet dyed copper-colored if copper armor isn't on this server build.
		stack = new ItemStack(Material.LEATHER_HELMET);
		LeatherArmorMeta meta = (LeatherArmorMeta) requireMeta(stack, "Invincible Copper Helmet");
		meta.setColor(Color.fromRGB(184, 115, 51));
		applyInvHelmetMeta(meta);
		meta.addItemFlags(ItemFlag.HIDE_DYE);
		stack.setItemMeta(meta);
		return stack;
	}

	public static ItemStack createScoreboardShovel() {
		ItemStack stack = new ItemStack(Material.GOLDEN_SHOVEL);
		ItemMeta meta = requireMeta(stack, "Scoreboard Shovel");
		meta.displayName(Component.text("Scoreboard Shovel", NamedTextColor.GREEN)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP Tools", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Right-click: admin hub", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false),
				Component.text("Scoreboard · Sell prices · Shop", NamedTextColor.DARK_GRAY)
						.decoration(TextDecoration.ITALIC, false)
		));
		meta.getPersistentDataContainer().set(SCOREBOARD_SHOVEL_KEY, PersistentDataType.BYTE, (byte) 1);
		meta.setUnbreakable(true);
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
		stack.setItemMeta(meta);
		return stack;
	}

	private static void applyInvHelmetMeta(ItemMeta meta) {
		meta.displayName(Component.text("Invincible Copper Helmet", NamedTextColor.GOLD)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP Armor", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Helmet only — wear to become invincible", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false),
				Component.text("Nothing can kill you while worn", NamedTextColor.DARK_GRAY)
						.decoration(TextDecoration.ITALIC, false)
		));
		meta.getPersistentDataContainer().set(INVINCIBLE_HELMET_KEY, PersistentDataType.BYTE, (byte) 1);
		meta.setUnbreakable(true);
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
	}

	private static ItemMeta requireMeta(ItemStack stack, String label) {
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			throw new IllegalStateException(label + " has no ItemMeta for " + stack.getType());
		}
		return meta;
	}

	private static Material firstAvailable(Material... materials) {
		for (Material material : materials) {
			if (material != null && material.isItem()) {
				return material;
			}
		}
		return Material.IRON_AXE;
	}

	public static ItemStack createDespacitoDisc() {
		return CustomDisc.DESPACITO.create();
	}

	public static ItemStack createMoskauDisc() {
		return CustomDisc.MOSKAU.create();
	}

	public static ItemStack createKimJongGoonDisc() {
		return CustomDisc.KIM_JONG_GOON.create();
	}

	public static boolean isBoomerits(ItemStack stack) {
		return hasKey(stack, BOOMERITS_KEY);
	}

	public static boolean isKickSword(ItemStack stack) {
		return hasKey(stack, KICK_SWORD_KEY);
	}

	public static boolean isKillHammer(ItemStack stack) {
		return hasKey(stack, KILL_HAMMER_KEY);
	}

	public static boolean isInvincibleHelmet(ItemStack stack) {
		return hasKey(stack, INVINCIBLE_HELMET_KEY);
	}

	public static boolean isScoreboardShovel(ItemStack stack) {
		return hasKey(stack, SCOREBOARD_SHOVEL_KEY);
	}

	public static ItemStack createHoleFiller() {
		ItemStack stack = new ItemStack(Material.BRUSH);
		ItemMeta meta = requireMeta(stack, "Hole Filler");
		meta.getPersistentDataContainer().set(HOLE_FILLER_KEY, PersistentDataType.BYTE, (byte) 1);
		meta.getPersistentDataContainer().set(HOLE_FILLER_MODE_KEY, PersistentDataType.STRING, NaturalFiller.Mode.HOLE.name());
		meta.setUnbreakable(true);
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
		applyHoleFillerMeta(meta, NaturalFiller.Mode.HOLE);
		stack.setItemMeta(meta);
		return stack;
	}

	public static boolean isHoleFiller(ItemStack stack) {
		return hasKey(stack, HOLE_FILLER_KEY);
	}

	public static NaturalFiller.Mode holeFillerMode(ItemStack stack) {
		if (!isHoleFiller(stack) || !stack.hasItemMeta()) {
			return NaturalFiller.Mode.HOLE;
		}
		String raw = stack.getItemMeta().getPersistentDataContainer().get(HOLE_FILLER_MODE_KEY, PersistentDataType.STRING);
		if (raw == null) {
			return NaturalFiller.Mode.HOLE;
		}
		try {
			return NaturalFiller.Mode.valueOf(raw);
		} catch (IllegalArgumentException ex) {
			return NaturalFiller.Mode.HOLE;
		}
	}

	public static NaturalFiller.Mode cycleHoleFillerMode(ItemStack stack) {
		NaturalFiller.Mode next = holeFillerMode(stack) == NaturalFiller.Mode.HOLE
				? NaturalFiller.Mode.WALL
				: NaturalFiller.Mode.HOLE;
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			return next;
		}
		meta.getPersistentDataContainer().set(HOLE_FILLER_MODE_KEY, PersistentDataType.STRING, next.name());
		applyHoleFillerMeta(meta, next);
		stack.setItemMeta(meta);
		return next;
	}

	private static void applyHoleFillerMeta(ItemMeta meta, NaturalFiller.Mode mode) {
		meta.displayName(Component.text("Hole Filler", NamedTextColor.GREEN)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP Tools", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Mode: " + mode.name(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
				Component.text("Right-click gap: fill naturally", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false),
				Component.text("Sneak + right-click: HOLE ↔ WALL", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false),
				Component.text("Left-click: undo last fill", NamedTextColor.DARK_GRAY)
						.decoration(TextDecoration.ITALIC, false)
		));
	}

	public static boolean isDespacitoDisc(ItemStack stack) {
		return CustomDisc.DESPACITO.matches(stack);
	}

	public static boolean isCustomDisc(ItemStack stack) {
		return CustomDisc.fromItem(stack) != null;
	}

	private static boolean hasKey(ItemStack stack, NamespacedKey key) {
		if (stack == null || !stack.hasItemMeta() || key == null) {
			return false;
		}
		return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
	}

	private static int clampAmount(int amount) {
		return Math.max(1, Math.min(64, amount));
	}
}
