package com.chunkboomerits.paper;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpItems {
	public static NamespacedKey BOOMERITS_KEY;
	public static NamespacedKey KICK_SWORD_KEY;
	public static NamespacedKey KILL_HAMMER_KEY;
	public static NamespacedKey INVINCIBLE_HELMET_KEY;

	private OpItems() {
	}

	public static void init(JavaPlugin plugin) {
		BOOMERITS_KEY = new NamespacedKey(plugin, "chunk_boomerits");
		KICK_SWORD_KEY = new NamespacedKey(plugin, "kick_sword");
		KILL_HAMMER_KEY = new NamespacedKey(plugin, "kill_hammer");
		INVINCIBLE_HELMET_KEY = new NamespacedKey(plugin, "invincible_helmet");
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
		ItemStack stack = new ItemStack(Material.MACE);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text("Insta Kill Hammer", NamedTextColor.RED)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP Tools", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Hit anything to kill it instantly", NamedTextColor.GRAY)
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
		ItemStack stack = new ItemStack(Material.COPPER_HELMET);
		ItemMeta meta = stack.getItemMeta();
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
		stack.setItemMeta(meta);
		return stack;
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
