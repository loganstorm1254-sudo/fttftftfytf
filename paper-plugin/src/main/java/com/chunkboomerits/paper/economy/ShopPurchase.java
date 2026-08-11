package com.chunkboomerits.paper.economy;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Marks items bought from /shop so they cannot be flipped on /sell.
 */
public final class ShopPurchase {
	private static NamespacedKey KEY;

	private ShopPurchase() {
	}

	public static void init(JavaPlugin plugin) {
		KEY = new NamespacedKey(plugin, "shop_purchase");
	}

	public static boolean isMarked(ItemStack stack) {
		if (KEY == null || stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
			return false;
		}
		return stack.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
	}

	/**
	 * Stamp a shop-bought stack and add lore so players know it can't be /sell'd.
	 */
	public static ItemStack mark(ItemStack stack) {
		if (KEY == null || stack == null || stack.getType().isAir()) {
			return stack;
		}
		ItemStack out = stack.clone();
		ItemMeta meta = out.getItemMeta();
		if (meta == null) {
			return out;
		}
		meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
		List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
		lore.add(Component.empty());
		lore.add(Component.text("Shop purchase — cannot /sell", NamedTextColor.DARK_GRAY)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore);
		out.setItemMeta(meta);
		return out;
	}
}
