package com.minedoom.input;

import com.minedoom.screen.SelectionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Built-in wooden-axe selection (WorldEdit-style) when WorldEdit is absent or unused.
 */
public final class WandListener implements Listener {

    private final SelectionService selection;

    public WandListener(SelectionService selection) {
        this.selection = selection;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.WOODEN_AXE) {
            return;
        }
        // If WorldEdit is handling the wand, still record builtin corners as backup
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            selection.setPos1(player, block.getLocation());
            player.sendMessage("§cMineDoom §7pos1 set · §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
            event.setCancelled(true);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            selection.setPos2(player, block.getLocation());
            player.sendMessage("§cMineDoom §7pos2 set · §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
            event.setCancelled(true);
        }
    }
}
