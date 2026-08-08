package com.minedoom.display;

import com.minedoom.MineDoomPlugin;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenKind;
import com.minedoom.screen.ScreenManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Display Terminal GUI + 16:9 screen placement + lever show/hide.
 */
public final class DisplayListener implements Listener {

    public static final class TerminalGuiHolder implements InventoryHolder {
        private final DisplayTerminalStore.Terminal terminal;
        private Inventory inventory;

        TerminalGuiHolder(DisplayTerminalStore.Terminal terminal) {
            this.terminal = terminal;
        }

        DisplayTerminalStore.Terminal terminal() {
            return terminal;
        }

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
    private final DisplayItems items;
    private final DisplayTerminalStore store;
    private final Map<UUID, Boolean> lastPower = new HashMap<>();

    public DisplayListener(
            MineDoomPlugin plugin,
            ScreenManager screens,
            DisplayItems items,
            DisplayTerminalStore store
    ) {
        this.plugin = plugin;
        this.screens = screens;
        this.items = items;
        this.store = store;
    }

    public void openGui(Player player, DisplayTerminalStore.Terminal terminal) {
        TerminalGuiHolder holder = new TerminalGuiHolder(terminal);
        Inventory inv = Bukkit.createInventory(holder, 27, "Display Terminal");
        holder.setInventory(inv);

        inv.setItem(11, items.screen169());
        inv.setItem(13, linkButton(terminal));
        inv.setItem(15, statusButton(terminal));

        ItemStack tip = new ItemStack(Material.PAPER);
        ItemMeta meta = tip.getItemMeta();
        meta.setDisplayName("§eHow to use");
        meta.setLore(List.of(
                "§71. Take the §f16:9 Screen§7 item",
                "§72. Look at a wall and place it",
                "§73. Put a §flever§7 on this terminal",
                "§74. Lever §aON§7 → screen appears",
                "§75. Lever §cOFF§7 → screen hides (air)",
                "§7",
                "§bPython display:",
                "§7Save a PNG to",
                "§fplugins/MineDoom/display/frame.png",
                "§7It shows while the lever is ON"
        ));
        tip.setItemMeta(meta);
        inv.setItem(22, tip);

        player.openInventory(inv);
    }

    private ItemStack linkButton(DisplayTerminalStore.Terminal terminal) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§aLink nearest 16:9 screen");
        String linked = terminal.linkedScreenId() == null ? "§cnone" : "§a" + shortId(terminal.linkedScreenId());
        meta.setLore(List.of(
                "§7Currently linked: " + linked,
                "§7Click to link the nearest DISPLAY screen"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack statusButton(DisplayTerminalStore.Terminal terminal) {
        ItemStack item = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Terminal status");
        LocationPower powered = isPowered(terminal);
        meta.setLore(List.of(
                "§7Pos: §f" + terminal.x() + " " + terminal.y() + " " + terminal.z(),
                "§7Power: " + (powered.powered() ? "§aON" : "§cOFF"),
                "§7Screen: " + (terminal.linkedScreenId() == null ? "§cnot linked" : "§a" + shortId(terminal.linkedScreenId()))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private record LocationPower(boolean powered) {}

    private LocationPower isPowered(DisplayTerminalStore.Terminal terminal) {
        var loc = terminal.location();
        if (loc == null) {
            return new LocationPower(false);
        }
        Block b = loc.getBlock();
        return new LocationPower(b.isBlockPowered() || b.isBlockIndirectlyPowered());
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!items.isTerminal(hand)) {
            return;
        }
        Block b = event.getBlockPlaced();
        store.put(new DisplayTerminalStore.Terminal(
                b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), null));
        event.getPlayer().sendMessage("§aDisplay Terminal placed. §7Right-click it for the GUI.");
    }

    /**
     * Item frames are entities — place via right-click, not BlockPlaceEvent.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlaceScreenItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!items.isScreenItem(hand)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        try {
            int tx = plugin.getConfig().getInt("display.tiles-x", 8);
            int ty = plugin.getConfig().getInt("display.tiles-y", 5);
            DoomScreen screen = screens.placeWidescreen(player, ScreenKind.DISPLAY, tx, ty);
            screens.hideScreen(screen);
            Optional<DisplayTerminalStore.Terminal> term = store.findNearest(player.getLocation(), 48);
            if (term.isPresent()) {
                DisplayTerminalStore.Terminal linked = term.get().withScreen(screen.getId());
                store.put(linked);
                player.sendMessage("§a16:9 screen placed & linked to terminal §7(" + tx + "×" + ty + ")");
                player.sendMessage("§7Flick the lever on the terminal §aON§7 to show it.");
            } else {
                player.sendMessage("§a16:9 screen placed §7(no terminal nearby — open a terminal GUI → Link)");
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }
        } catch (Exception e) {
            player.sendMessage("§c" + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<DisplayTerminalStore.Terminal> term = store.remove(event.getBlock().getLocation());
        if (term.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5), items.terminalBlock());
        }
        lastPower.remove(powerKey(term.get()));
        event.getPlayer().sendMessage("§7Display Terminal removed.");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // Screen item placement is handled separately
        if (items.isScreenItem(event.getItem())) {
            return;
        }
        Optional<DisplayTerminalStore.Terminal> term = store.get(block.getLocation());
        if (term.isEmpty()) {
            return;
        }
        // Don't steal lever placement / lever toggles
        if (event.getItem() != null && event.getItem().getType() == Material.LEVER) {
            return;
        }
        if (block.getType() == Material.LEVER) {
            return;
        }
        event.setCancelled(true);
        openGui(event.getPlayer(), term.get());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        // Lever attached to our terminal, or power into the terminal block
        Block block = event.getBlock();
        Optional<DisplayTerminalStore.Terminal> term = Optional.empty();

        if (block.getType() == Material.LEVER && block.getBlockData() instanceof Switch sw) {
            Block attached = switch (sw.getAttachedFace()) {
                case FLOOR -> block.getRelative(BlockFace.DOWN);
                case CEILING -> block.getRelative(BlockFace.UP);
                case WALL -> block.getRelative(sw.getFacing().getOppositeFace());
            };
            term = store.get(attached.getLocation());
        }
        if (term.isEmpty()) {
            term = store.get(block.getLocation());
        }
        if (term.isEmpty()) {
            // Check neighbors for terminal receiving power
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                Optional<DisplayTerminalStore.Terminal> t = store.get(block.getRelative(face).getLocation());
                if (t.isPresent()) {
                    term = t;
                    break;
                }
            }
        }
        if (term.isEmpty()) {
            return;
        }
        DisplayTerminalStore.Terminal t = term.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> applyPower(t, null));
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
        if (!(block.getBlockData() instanceof Switch sw)) {
            return;
        }
        Block attached = switch (sw.getAttachedFace()) {
            case FLOOR -> block.getRelative(BlockFace.DOWN);
            case CEILING -> block.getRelative(BlockFace.UP);
            case WALL -> block.getRelative(sw.getFacing().getOppositeFace());
        };
        Optional<DisplayTerminalStore.Terminal> term = store.get(attached.getLocation());
        if (term.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> applyPower(term.get(), event.getPlayer()));
    }

    void applyPower(DisplayTerminalStore.Terminal terminal, Player player) {
        boolean powered = isPowered(terminal).powered();
        UUID key = powerKey(terminal);
        Boolean prev = lastPower.put(key, powered);
        if (prev != null && prev == powered) {
            return;
        }
        if (terminal.linkedScreenId() == null) {
            if (player != null) {
                player.sendMessage("§cNo screen linked. Open the terminal GUI → Link nearest screen.");
            }
            return;
        }
        Optional<DoomScreen> screen = screens.get(terminal.linkedScreenId());
        if (screen.isEmpty() || screen.get().getKind() != ScreenKind.DISPLAY) {
            if (player != null) {
                player.sendMessage("§cLinked screen missing. Place a 16:9 screen again.");
            }
            return;
        }
        try {
            if (powered) {
                screens.showScreen(screen.get());
                // push last python frame if any
                plugin.getDisplayInbox().pushLatest(screen.get());
                if (player != null) {
                    player.sendMessage("§aDisplay screen ON");
                }
            } else {
                screens.hideScreen(screen.get());
                if (player != null) {
                    player.sendMessage("§7Display screen OFF §8(air)");
                }
            }
        } catch (Exception e) {
            if (player != null) {
                player.sendMessage("§c" + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TerminalGuiHolder holder)) {
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
        int slot = event.getRawSlot();
        if (slot == 11 && items.isScreenItem(clicked)) {
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(items.screen169());
            if (!left.isEmpty()) {
                left.values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
            }
            player.sendMessage("§aGot §b16:9 Display Screen§a — look at a wall and place it.");
            return;
        }
        if (slot == 13) {
            Optional<DoomScreen> nearest = screens.findNearest(player.getLocation(), 48, ScreenKind.DISPLAY);
            if (nearest.isEmpty()) {
                player.sendMessage("§cNo 16:9 display screen nearby. Place one first.");
                return;
            }
            DisplayTerminalStore.Terminal updated = holder.terminal().withScreen(nearest.get().getId());
            store.put(updated);
            player.sendMessage("§aLinked screen §f" + shortId(nearest.get().getId()));
            player.closeInventory();
            openGui(player, updated);
            return;
        }
        if (slot == 15) {
            // refresh status
            Optional<DisplayTerminalStore.Terminal> fresh = store.get(holder.terminal().location());
            player.closeInventory();
            fresh.ifPresent(t -> openGui(player, t));
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TerminalGuiHolder) {
            event.setCancelled(true);
        }
    }

    private static UUID powerKey(DisplayTerminalStore.Terminal t) {
        return UUID.nameUUIDFromBytes(t.key().getBytes());
    }
}
