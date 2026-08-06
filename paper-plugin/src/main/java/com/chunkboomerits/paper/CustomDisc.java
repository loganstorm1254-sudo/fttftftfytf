package com.chunkboomerits.paper;

import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import io.papermc.paper.datacomponent.DataComponentTypes;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Custom music discs played via the resource pack (vanilla song stripped).
 */
public enum CustomDisc {
	DESPACITO(
			"music_disc_despacito",
			"chunkboomerits:music_disc.despacito",
			"Luis Fonsi - Despacito ft. Daddy Yankee",
			List.of("Luis Fonsi - Despacito", "ft. Daddy Yankee"),
			Material.MUSIC_DISC_CAT,
			Sound.MUSIC_DISC_CAT,
			20L * 285
	),
	MOSKAU(
			"music_disc_moskau",
			"chunkboomerits:music_disc.moskau",
			"Dschinghis Khan - Moskau",
			List.of("Dschinghis Khan - Moskau"),
			Material.MUSIC_DISC_13,
			Sound.MUSIC_DISC_13,
			20L * 280
	),
	KIM_JONG_GOON(
			"music_disc_kim_jong_goon",
			"chunkboomerits:music_disc.kim_jong_goon",
			"Hyperbaiter - Kim Jong Goon",
			List.of("Hyperbaiter - Kim Jong Goon"),
			Material.MUSIC_DISC_PIGSTEP,
			Sound.MUSIC_DISC_PIGSTEP,
			20L * 140
	);

	private final String keyPath;
	private final String soundKey;
	private final String nowPlaying;
	private final List<String> loreLines;
	private final Material material;
	private final Sound vanillaSound;
	private final long lengthTicks;
	private NamespacedKey pdcKey;

	CustomDisc(
			String keyPath,
			String soundKey,
			String nowPlaying,
			List<String> loreLines,
			Material material,
			Sound vanillaSound,
			long lengthTicks
	) {
		this.keyPath = keyPath;
		this.soundKey = soundKey;
		this.nowPlaying = nowPlaying;
		this.loreLines = loreLines;
		this.material = material;
		this.vanillaSound = vanillaSound;
		this.lengthTicks = lengthTicks;
	}

	public static void init(JavaPlugin plugin) {
		for (CustomDisc disc : values()) {
			disc.pdcKey = new NamespacedKey(plugin, disc.keyPath);
		}
	}

	public String soundKey() {
		return soundKey;
	}

	public String nowPlaying() {
		return nowPlaying;
	}

	public Sound vanillaSound() {
		return vanillaSound;
	}

	public long lengthTicks() {
		return lengthTicks;
	}

	public NamespacedKey pdcKey() {
		return pdcKey;
	}

	public String commandName() {
		return switch (this) {
			case DESPACITO -> "despacito";
			case MOSKAU -> "moskau";
			case KIM_JONG_GOON -> "kimjonggoon";
		};
	}

	public String itemLabel() {
		return switch (this) {
			case DESPACITO -> "Despacito Disc";
			case MOSKAU -> "Moskau Disc";
			case KIM_JONG_GOON -> "Kim Jong Goon Disc";
		};
	}

	public ItemStack create() {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text("Music Disc", NamedTextColor.AQUA)
				.decoration(TextDecoration.ITALIC, false));
		java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
		for (int i = 0; i < loreLines.size(); i++) {
			NamedTextColor color = i == 0 ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY;
			lore.add(Component.text(loreLines.get(i), color).decoration(TextDecoration.ITALIC, true));
		}
		lore.add(Component.text("Play in a jukebox (resource pack required)", NamedTextColor.YELLOW)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore);
		meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
		meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		meta.setEnchantmentGlintOverride(true);
		stack.setItemMeta(meta);
		stack.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
		return stack;
	}

	public boolean matches(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta() || pdcKey == null) {
			return false;
		}
		return stack.getItemMeta().getPersistentDataContainer().has(pdcKey, PersistentDataType.BYTE);
	}

	public static CustomDisc fromItem(ItemStack stack) {
		if (stack == null) {
			return null;
		}
		for (CustomDisc disc : values()) {
			if (disc.matches(stack)) {
				return disc;
			}
		}
		return null;
	}

	public static CustomDisc fromCommandAlias(String name) {
		if (name == null) {
			return null;
		}
		String n = name.toLowerCase(Locale.ROOT).replace("-", "_");
		return switch (n) {
			case "despacito", "music_disc_despacito", "despacitodisc", "musicdiscdespacito" -> DESPACITO;
			case "moskau", "music_disc_moskau", "moskaudisc", "dschinghis_khan", "dschinghiskhan" -> MOSKAU;
			case "kimjonggoon", "kim_jong_goon", "music_disc_kim_jong_goon", "kimjong", "hyperbaiter" -> KIM_JONG_GOON;
			default -> null;
		};
	}
}
