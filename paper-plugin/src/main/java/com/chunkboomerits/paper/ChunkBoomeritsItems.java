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

public final class ChunkBoomeritsItems {
	public static NamespacedKey KEY;

	private ChunkBoomeritsItems() {
	}

	public static void init(JavaPlugin plugin) {
		KEY = new NamespacedKey(plugin, "chunk_boomerits");
	}

	public static ItemStack create(int amount) {
		ItemStack stack = new ItemStack(Material.FIRE_CHARGE, Math.max(1, Math.min(64, amount)));
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text("Chunk Boomerits", NamedTextColor.GOLD)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("OP-only", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
				Component.text("Throw at a chunk to delete it", NamedTextColor.GRAY)
						.decoration(TextDecoration.ITALIC, false)
		));
		meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
		meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
		stack.setItemMeta(meta);
		return stack;
	}

	public static boolean isChunkBoomerits(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta() || KEY == null) {
			return false;
		}
		return stack.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
	}
}
