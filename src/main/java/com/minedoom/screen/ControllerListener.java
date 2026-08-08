package com.minedoom.screen;

import com.minedoom.MineDoomPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Places switch blocks, feeds them into a give GUI / creative picks, and toggles
 * nearest screens when a lever on a switch is powered.
 */
public final class ControllerListener implements Listener {

    public static final class GiveMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final MineDoomPlugin plugin;
    private final ScreenManager screens;
    private final ControllerStore store;
    private final ControllerItems items;
    private final Map<UUID, Boolean> lastPower = new HashMap<>();

    public ControllerListener(
            MineDoomPlugin plugin,
            ScreenManager screens,
            ControllerStore store,
            ControllerItems items
    ) {
        this.plugin = plugin;
        this.screens = screens;
        this.store = store;
        this.items = items;
    }

    public void openGiveMenu(Player player) {
        GiveMenuHolder holder = new GiveMenuHolder();
        Inventory inv = plugin.getServer().createInventory(holder, 9, "MineDoom Blocks");
        holder.setInventory(inv);
        inv.setItem(2, items.doomSwitch());
        inv.setItem(4, items.googleSwitch());
        if (plugin.getDisplayItems() != null) {
            inv.setItem(6, plugin.getDisplayItems().terminalBlock());
        }
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        ScreenKind kind = items.kindOf(hand);
        if (kind == null) {
            if (event.getBlockPlaced().getType() == Material.LEVER) {
                Block attached = getLeverAttachedBlock(event.getBlockPlaced());
                if (attached != null) {
                    Optional<ControllerStore.Controller> controller = store.get(attached.getLocation());
                    if (controller.isPresent()) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                applyLeverState(event.getBlockPlaced(), controller.get(), event.getPlayer()));
                    }
                }
            }
            return;
        }

        Block block = event.getBlockPlaced();
        store.put(new ControllerStore.Controller(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                kind
        ));
        event.getPlayer().sendMessage("§a" + (kind == ScreenKind.GOOGLE ? "Google" : "Doom")
                + " screen switch placed. §7Put a lever on it — ON shows the nearest screen, OFF hides it.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<ControllerStore.Controller> controller = store.remove(event.getBlock().getLocation());
        if (controller.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    items.forKind(controller.get().kind()));
        }
        lastPower.remove(powerKey(controller.get()));
        event.getPlayer().sendMessage("§7Screen switch removed.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (event.getBlock().getType() != Material.LEVER) {
            return;
        }
        Block attached = getLeverAttachedBlock(event.getBlock());
        if (attached == null) {
            return;
        }
        Optional<ControllerStore.Controller> controller = store.get(attached.getLocation());
        if (controller.isEmpty()) {
            return;
        }
        boolean powered = event.getNewCurrent() > 0;
        UUID key = powerKey(controller.get());
        Boolean prev = lastPower.put(key, powered);
        if (prev != null && prev == powered) {
            return;
        }
        applyPower(controller.get(), powered, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeverClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LEVER) {
            return;
        }
        Block attached = getLeverAttachedBlock(block);
        if (attached == null) {
            return;
        }
        Optional<ControllerStore.Controller> controller = store.get(attached.getLocation());
        if (controller.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
                applyLeverState(block, controller.get(), event.getPlayer()));
    }

    private void applyLeverState(Block leverBlock, ControllerStore.Controller controller, Player player) {
        if (!(leverBlock.getBlockData() instanceof Switch sw)) {
            return;
        }
        boolean powered = sw.isPowered();
        UUID key = powerKey(controller);
        Boolean prev = lastPower.put(key, powered);
        if (prev != null && prev == powered) {
            return;
        }
        applyPower(controller, powered, player);
    }

    private void applyPower(ControllerStore.Controller controller, boolean powered, Player player) {
        var loc = controller.location();
        if (loc == null) {
            return;
        }
        double range = plugin.getConfig().getDouble("switch-range", 48.0);
        Optional<DoomScreen> screen = screens.findNearest(loc, range, controller.kind());
        if (screen.isEmpty()) {
            if (player != null) {
                player.sendMessage("§cNo " + controller.kind().name().toLowerCase()
                        + " screen within " + (int) range + " blocks of this switch.");
            }
            return;
        }
        try {
            if (powered) {
                screens.showScreen(screen.get());
                if (player != null) {
                    player.sendMessage("§aScreen shown §7(" + controller.kind().name().toLowerCase() + ")");
                }
            } else {
                screens.hideScreen(screen.get());
                if (player != null) {
                    player.sendMessage("§7Screen hidden §8(air)");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Switch toggle failed: " + e.getMessage());
            if (player != null) {
                player.sendMessage("§c" + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onGiveClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GiveMenuHolder)) {
            return;
        }
        // Only handle clicks in the give menu itself — never touch hotbar / player inv
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof GiveMenuHolder)) {
            event.setCancelled(true); // prevent shift-click dumping menu items oddly
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        if (plugin.getDisplayItems() != null && plugin.getDisplayItems().isTerminal(clicked)) {
            ItemStack give = plugin.getDisplayItems().terminalBlock();
            give.setAmount(1);
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(give);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack ->
                        player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
            player.closeInventory();
            player.sendMessage("§aGot §bDisplay Terminal");
            player.sendMessage("§7Place → right-click → Set text (e.g. Players: {playercount}) → 16:9 screen → lever ON.");
            return;
        }
        ScreenKind kind = items.kindOf(clicked);
        if (kind == null) {
            return;
        }
        ItemStack give = items.forKind(kind);
        give.setAmount(1);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(give);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(stack ->
                    player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        player.closeInventory();
        player.sendMessage("§aGot §f" + (kind == ScreenKind.GOOGLE ? "Google" : "Doom") + " Screen Switch");
        player.sendMessage("§7Place it, put a lever on it — ON shows screen, OFF hides it.");
    }

    @EventHandler
    public void onGiveDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GiveMenuHolder) {
            event.setCancelled(true);
        }
    }

    private static UUID powerKey(ControllerStore.Controller c) {
        return UUID.nameUUIDFromBytes(c.key().getBytes());
    }

    static Block getLeverAttachedBlock(Block leverBlock) {
        BlockData data = leverBlock.getBlockData();
        if (!(data instanceof Switch sw)) {
            return null;
        }
        FaceAttachable.AttachedFace face = sw.getAttachedFace();
        return switch (face) {
            case FLOOR -> leverBlock.getRelative(BlockFace.DOWN);
            case CEILING -> leverBlock.getRelative(BlockFace.UP);
            case WALL -> leverBlock.getRelative(sw.getFacing().getOppositeFace());
        };
    }
}
