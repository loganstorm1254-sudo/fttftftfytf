package com.minedoom.display;

import com.minedoom.screen.ScreenKind;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Items for the Display Terminal + 16:9 screen. */
public final class DisplayItems {

    public static final String PDC_TERMINAL = "display_terminal";
    public static final String PDC_SCREEN = "display_screen_169";

    private final NamespacedKey terminalKey;
    private final NamespacedKey screenKey;

    public DisplayItems(JavaPlugin plugin) {
        this.terminalKey = new NamespacedKey(plugin, PDC_TERMINAL);
        this.screenKey = new NamespacedKey(plugin, PDC_SCREEN);
    }

    public ItemStack terminalBlock() {
        ItemStack item = new ItemStack(Material.LODESTONE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bDisplay Terminal");
        meta.setLore(List.of(
                "§7Right-click for GUI",
                "§7Set text → take §f16:9 Screen§7 → place on wall",
                "§7Lever ON shows your text on the screen",
                "§7Variables: §f{playercount}§7, §f{maxplayers}§7, §f{time}"
        ));
        meta.getPersistentDataContainer().set(terminalKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack screen169() {
        ItemStack item = new ItemStack(Material.ITEM_FRAME);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b16:9 Display Screen");
        meta.setLore(List.of(
                "§7Place on a wall (look at the face)",
                "§7Links to the nearest Display Terminal",
                "§7Lever ON the terminal → show · OFF → hide"
        ));
        meta.getPersistentDataContainer().set(screenKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTerminal(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(terminalKey, PersistentDataType.BYTE);
    }

    public boolean isScreenItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(screenKey, PersistentDataType.BYTE);
    }

    public ScreenKind screenKind() {
        return ScreenKind.DISPLAY;
    }
}
