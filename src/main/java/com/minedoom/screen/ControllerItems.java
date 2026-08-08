package com.minedoom.screen;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Two placeable switch blocks (Doom + Google) for lever-controlled screen hide/show.
 */
public final class ControllerItems {

    public static final String PDC_KIND = "controller_kind";

    private final NamespacedKey kindKey;

    public ControllerItems(JavaPlugin plugin) {
        this.kindKey = new NamespacedKey(plugin, PDC_KIND);
    }

    public NamespacedKey getKindKey() {
        return kindKey;
    }

    public ItemStack doomSwitch() {
        return create(ScreenKind.DOOM, Material.REDSTONE_BLOCK, "§cDoom Screen Switch",
                "§7Place near a Doom screen, then put a §flever§7 on it.",
                "§7Lever §aON §7→ show screen · §cOFF §7→ hide (air)");
    }

    public ItemStack googleSwitch() {
        return create(ScreenKind.GOOGLE, Material.LAPIS_BLOCK, "§eGoogle Screen Switch",
                "§7Place near a Google screen, then put a §flever§7 on it.",
                "§7Lever §aON §7→ show screen · §cOFF §7→ hide (air)");
    }

    public ItemStack forKind(ScreenKind kind) {
        return kind == ScreenKind.GOOGLE ? googleSwitch() : doomSwitch();
    }

    private ItemStack create(ScreenKind kind, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.name());
        item.setItemMeta(meta);
        return item;
    }

    public ScreenKind kindOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        return ScreenKind.fromString(raw);
    }

    public boolean isController(ItemStack item) {
        return kindOf(item) != null;
    }

    public Material materialFor(ScreenKind kind) {
        return kind == ScreenKind.GOOGLE ? Material.LAPIS_BLOCK : Material.REDSTONE_BLOCK;
    }
}
