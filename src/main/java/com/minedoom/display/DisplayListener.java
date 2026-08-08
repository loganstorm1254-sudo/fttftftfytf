package com.minedoom.display;

import com.minedoom.MineDoomPlugin;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenKind;
import com.minedoom.screen.ScreenManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Display Terminal GUI + 16:9 screen placement + lever show/hide + typed text.
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
    private final DisplayTextRenderer textRenderer;
    private final Map<UUID, Boolean> lastPower = new HashMap<>();
    /** player UUID → terminal key waiting for chat text */
    private final Map<UUID, String> pendingText = new ConcurrentHashMap<>();

    public DisplayListener(
            MineDoomPlugin plugin,
            ScreenManager screens,
            DisplayItems items,
            DisplayTerminalStore store,
            DisplayTextRenderer textRenderer
    ) {
        this.plugin = plugin;
        this.screens = screens;
        this.items = items;
        this.store = store;
        this.textRenderer = textRenderer;
    }

    public void openGui(Player player, DisplayTerminalStore.Terminal terminal) {
        TerminalGuiHolder holder = new TerminalGuiHolder(terminal);
        Inventory inv = Bukkit.createInventory(holder, 27, "Display Terminal");
        holder.setInventory(inv);

        inv.setItem(10, textButton(terminal));
        inv.setItem(12, items.screen169());
        inv.setItem(14, linkButton(terminal));
        inv.setItem(16, statusButton(terminal));
        inv.setItem(22, tipButton());

        player.openInventory(inv);
    }

    private ItemStack textButton(DisplayTerminalStore.Terminal terminal) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eSet screen text");
        List<String> lore = new ArrayList<>();
        lore.add("§7Click, then type in chat.");
        lore.add("§7Current:");
        String shown = terminal.safeText();
        for (String line : wrapLore(shown, 40)) {
            lore.add("§f" + line);
        }
        lore.add("§8");
        lore.add("§7Preview: §a" + DisplayTextRenderer.applyPlaceholders(shown, terminal.location()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack tipButton() {
        ItemStack tip = new ItemStack(Material.PAPER);
        ItemMeta meta = tip.getItemMeta();
        meta.setDisplayName("§eHow to use");
        meta.setLore(List.of(
                "§71. Set text (name tag button)",
                "§72. Take §f16:9 Screen§7 → place on wall",
                "§73. Lever on terminal §aON§7 = show",
                "§7",
                "§bVariables (live):",
                "§f{playercount} §7online players",
                "§f{maxplayers} §7server max",
                "§f{world} §7world name",
                "§f{time} §7HH:mm",
                "§f{date} §7yyyy-MM-dd",
                "§f{tps} §7server TPS",
                "§7Use §f|§7 for a new line"
        ));
        tip.setItemMeta(meta);
        return tip;
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

    private static List<String> wrapLore(String text, int max) {
        List<String> out = new ArrayList<>();
        String flat = text.replace('\n', ' ').replace('|', ' ');
        while (flat.length() > max) {
            out.add(flat.substring(0, max));
            flat = flat.substring(max);
            if (out.size() >= 4) {
                out.add(flat.isEmpty() ? "" : flat.substring(0, Math.min(max, flat.length())) + "…");
                return out;
            }
        }
        if (!flat.isEmpty() || out.isEmpty()) {
            out.add(flat);
        }
        return out;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!items.isTerminal(hand)) {
            return;
        }
        Block b = event.getBlockPlaced();
        store.put(new DisplayTerminalStore.Terminal(
                b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), null,
                DisplayTerminalStore.DEFAULT_TEXT));
        event.getPlayer().sendMessage("§aDisplay Terminal placed. §7Right-click → Set screen text.");
    }

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
                player.sendMessage("§a16:9 screen placed & linked §7(" + tx + "×" + ty + ")");
                player.sendMessage("§7Flick the lever §aON§7 to show your text.");
            } else {
                player.sendMessage("§a16:9 screen placed §7(no terminal nearby — GUI → Link)");
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
        if (items.isScreenItem(event.getItem())) {
            return;
        }
        Optional<DisplayTerminalStore.Terminal> term = store.get(block.getLocation());
        if (term.isEmpty()) {
            return;
        }
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
        // Always re-fetch latest terminal (text may have changed)
        Optional<DisplayTerminalStore.Terminal> fresh = store.getByKey(terminal.key());
        DisplayTerminalStore.Terminal t = fresh.orElse(terminal);

        boolean powered = isPowered(t).powered();
        UUID key = powerKey(t);
        Boolean prev = lastPower.put(key, powered);
        if (prev != null && prev == powered) {
            // Still refresh text when already on
            if (powered) {
                pushTerminalText(t);
            }
            return;
        }
        if (t.linkedScreenId() == null) {
            if (player != null) {
                player.sendMessage("§cNo screen linked. Open the terminal GUI → Link nearest screen.");
            }
            return;
        }
        Optional<DoomScreen> screen = screens.get(t.linkedScreenId());
        if (screen.isEmpty() || screen.get().getKind() != ScreenKind.DISPLAY) {
            if (player != null) {
                player.sendMessage("§cLinked screen missing. Place a 16:9 screen again.");
            }
            return;
        }
        try {
            if (powered) {
                screens.showScreen(screen.get());
                pushTerminalText(t);
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

    void pushTerminalText(DisplayTerminalStore.Terminal terminal) {
        if (terminal.linkedScreenId() == null) {
            return;
        }
        Optional<DoomScreen> screen = screens.get(terminal.linkedScreenId());
        if (screen.isEmpty() || screen.get().isHidden()) {
            return;
        }
        textRenderer.pushText(screen.get(), terminal.safeText(), terminal.location());
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
        if (slot == 10) {
            player.closeInventory();
            pendingText.put(player.getUniqueId(), holder.terminal().key());
            player.sendMessage("§eType the text for the screen in chat.");
            player.sendMessage("§7Example: §fHello! Players online: {playercount}");
            player.sendMessage("§7Use §f|§7 for a new line · type §ccancel§7 to abort.");
            return;
        }
        if (slot == 12 && items.isScreenItem(clicked)) {
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(items.screen169());
            if (!left.isEmpty()) {
                left.values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
            }
            player.sendMessage("§aGot §b16:9 Display Screen§a — look at a wall and place it.");
            return;
        }
        if (slot == 14) {
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
        if (slot == 16) {
            Optional<DisplayTerminalStore.Terminal> fresh = store.get(holder.terminal().location());
            player.closeInventory();
            fresh.ifPresent(t -> openGui(player, t));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String terminalKey = pendingText.remove(event.getPlayer().getUniqueId());
        if (terminalKey == null) {
            return;
        }
        event.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage("§7Cancelled.");
                return;
            }
            if (msg.isEmpty()) {
                player.sendMessage("§cEmpty text ignored.");
                return;
            }
            Optional<DisplayTerminalStore.Terminal> term = store.getByKey(terminalKey);
            if (term.isEmpty()) {
                player.sendMessage("§cTerminal gone.");
                return;
            }
            DisplayTerminalStore.Terminal updated = term.get().withText(msg);
            store.put(updated);
            player.sendMessage("§aScreen text set:");
            player.sendMessage("§f" + msg);
            player.sendMessage("§7Live preview: §a" + DisplayTextRenderer.applyPlaceholders(msg, updated.location()));
            if (isPowered(updated).powered()) {
                pushTerminalText(updated);
            } else {
                player.sendMessage("§7Flick the lever §aON§7 to show it on the screen.");
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingText.remove(event.getPlayer().getUniqueId());
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
