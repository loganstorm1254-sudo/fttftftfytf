package com.minedoom.screen;

import com.minedoom.MineDoomPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScreenManager {

    private final MineDoomPlugin plugin;
    private final SelectionService selectionService;
    private final MapColorCache colorCache = new MapColorCache();
    private final ConcurrentHashMap<UUID, DoomScreen> screens = new ConcurrentHashMap<>();
    private final File storeFile;

    public ScreenManager(MineDoomPlugin plugin, SelectionService selectionService) {
        this.plugin = plugin;
        this.selectionService = selectionService;
        this.storeFile = new File(plugin.getDataFolder(), "screens.yml");
    }

    public SelectionService getSelectionService() {
        return selectionService;
    }

    public Collection<DoomScreen> getScreens() {
        return screens.values();
    }

    public Optional<DoomScreen> get(UUID id) {
        return Optional.ofNullable(screens.get(id));
    }

    public Optional<DoomScreen> findNearest(Location loc, double maxDist) {
        return findNearest(loc, maxDist, null);
    }

    public Optional<DoomScreen> findNearest(Location loc, double maxDist, ScreenKind kind) {
        DoomScreen best = null;
        double bestDist = maxDist * maxDist;
        for (DoomScreen screen : screens.values()) {
            if (kind != null && screen.getKind() != kind) {
                continue;
            }
            if (loc.getWorld() == null || !screen.getWorldName().equals(loc.getWorld().getName())) {
                continue;
            }
            Location c = screen.getCenter(loc.getWorld());
            double d = c.distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = screen;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Nearest matching screen for lever switches — includes hidden ones. */
    public Optional<DoomScreen> findNearestIncludingHidden(Location loc, double maxDist, ScreenKind kind) {
        return findNearest(loc, maxDist, kind);
    }

    /** Nearest visible screen (for play / browse). */
    public Optional<DoomScreen> findNearestVisible(Location loc, double maxDist, ScreenKind kind) {
        DoomScreen best = null;
        double bestDist = maxDist * maxDist;
        for (DoomScreen screen : screens.values()) {
            if (screen.isHidden()) {
                continue;
            }
            if (kind != null && screen.getKind() != kind) {
                continue;
            }
            if (loc.getWorld() == null || !screen.getWorldName().equals(loc.getWorld().getName())) {
                continue;
            }
            Location c = screen.getCenter(loc.getWorld());
            double d = c.distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = screen;
            }
        }
        return Optional.ofNullable(best);
    }

    public DoomScreen placeFromSelection(Player player) throws Exception {
        return placeFromSelection(player, ScreenKind.DOOM);
    }

    public DoomScreen placeFromSelection(Player player, ScreenKind kind) throws Exception {
        SelectionService.Bounds bounds = selectionService.requireBounds(player);
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();
        int sizeX = bounds.sizeX();
        int sizeY = bounds.sizeY();
        int sizeZ = bounds.sizeZ();

        int thinAxes = 0;
        if (sizeX == 1) thinAxes++;
        if (sizeY == 1) thinAxes++;
        if (sizeZ == 1) thinAxes++;
        if (thinAxes < 1) {
            throw new IllegalStateException("Selection must be a flat wall one block thick (one of X/Y/Z size must be 1).");
        }
        if (sizeY == 1 && sizeX > 1 && sizeZ > 1) {
            throw new IllegalStateException("Floor/ceiling selections are not supported — select a vertical wall.");
        }

        BlockFace facing = resolveFacingFromPlayerPosition(player, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeZ);
        int tilesX;
        int tilesY = sizeY;
        if (sizeZ == 1) {
            tilesX = sizeX;
        } else if (sizeX == 1) {
            tilesX = sizeZ;
        } else {
            tilesX = Math.max(sizeX, sizeZ);
        }

        if (tilesX < 1 || tilesY < 1) {
            throw new IllegalStateException("Selection too small.");
        }
        if (tilesX * tilesY > 64) {
            throw new IllegalStateException("Screen too large (max 64 maps). Try a smaller selection, e.g. 3x2.");
        }

        return buildScreen(player, kind, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeZ, facing, tilesX, tilesY);
    }

    /**
     * Place a 1×1 screen on the wall face the player is looking at.
     */
    public DoomScreen placeOnTargetBlock(Player player) throws Exception {
        return placeOnTargetBlock(player, ScreenKind.DOOM);
    }

    public DoomScreen placeOnTargetBlock(Player player, ScreenKind kind) throws Exception {
        var hit = player.rayTraceBlocks(8);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            throw new IllegalStateException("Look at a solid wall within 8 blocks, then /doom here");
        }
        Block wall = hit.getHitBlock();
        BlockFace face = hit.getHitBlockFace();
        if (face != BlockFace.NORTH && face != BlockFace.SOUTH
                && face != BlockFace.EAST && face != BlockFace.WEST) {
            throw new IllegalStateException("Look at a vertical wall face (not floor/ceiling).");
        }
        if (!wall.getType().isSolid()) {
            throw new IllegalStateException("Target block is not solid.");
        }
        return buildScreen(
                player,
                kind,
                wall.getX(), wall.getY(), wall.getZ(),
                wall.getX(), wall.getY(), wall.getZ(),
                1, 1,
                face,
                1, 1
        );
    }

    private DoomScreen buildScreen(
            Player player,
            ScreenKind kind,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int sizeX, int sizeZ,
            BlockFace facing,
            int tilesX, int tilesY
    ) throws Exception {
        if (kind == ScreenKind.DOOM) {
            if (plugin.getEngine() == null) {
                throw new IllegalStateException("PureDOOM is not available on this server.");
            }
            plugin.getEngine().ensureStarted();
        }

        World world = player.getWorld();

        // Clear previous MineDoom entities in this region
        Location sweep = new Location(world, (minX + maxX) / 2.0 + 0.5, (minY + maxY) / 2.0 + 0.5, (minZ + maxZ) / 2.0 + 0.5);
        double radius = Math.max(Math.max(tilesX, tilesY), 2) + 3;
        world.getNearbyEntities(sweep, radius, radius, radius).forEach(e -> {
            if (e.getScoreboardTags().contains("minedoom_screen")
                    || e instanceof org.bukkit.entity.ItemFrame
                    || e instanceof org.bukkit.entity.ItemDisplay) {
                Location el = e.getLocation();
                if (el.getBlockX() >= minX - 1 && el.getBlockX() <= maxX + 1
                        && el.getBlockY() >= minY - 1 && el.getBlockY() <= maxY + 1
                        && el.getBlockZ() >= minZ - 1 && el.getBlockZ() <= maxZ + 1) {
                    e.remove();
                }
            }
        });

        List<Integer> mapIds = new ArrayList<>();
        List<UUID> frameIds = new ArrayList<>();
        java.util.Set<String> usedBlocks = new java.util.HashSet<>();
        List<org.bukkit.entity.ItemFrame> frames = new ArrayList<>();
        String mapLabel = kind == ScreenKind.GOOGLE ? "§eGoogle" : "§cMineDoom";

        // 1) Spawn every frame first (sky→teleport trick), no maps yet
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                Location wallLoc = tileLocation(world, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeZ, facing, tx, ty);
                Block wallBlock = wallLoc.getBlock();
                String key = wallBlock.getX() + "," + wallBlock.getY() + "," + wallBlock.getZ();
                if (!usedBlocks.add(key)) {
                    throw new IllegalStateException("Internal grid bug: duplicate tile at " + key);
                }
                if (!wallBlock.getType().isSolid()) {
                    throw new IllegalStateException("Selection block at " + key
                            + " is not solid (" + wallBlock.getType() + "). Select solid wall blocks.");
                }

                org.bukkit.entity.ItemFrame frame = FramePlacer.spawnOnWallFace(world, wallBlock, facing);
                frames.add(frame);
                frameIds.add(frame.getUniqueId());
            }
        }

        // 2) Attach unique maps to each frame
        for (int i = 0; i < frames.size(); i++) {
            org.bukkit.entity.ItemFrame frame = frames.get(i);
            MapView view = Bukkit.createMap(world);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(true);
            for (var r : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(r);
            }

            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            meta.setMapView(view);
            meta.setDisplayName(mapLabel);
            mapItem.setItemMeta(meta);
            frame.setItem(mapItem, false);
            player.sendMap(view);
            mapIds.add(view.getId());
        }

        if (mapIds.size() != tilesX * tilesY) {
            throw new IllegalStateException("Expected " + (tilesX * tilesY) + " tiles but placed " + mapIds.size());
        }

        DoomScreen screen = new DoomScreen(
                UUID.randomUUID(),
                kind,
                world.getName(),
                minX, minY, minZ,
                maxX, maxY, maxZ,
                facing,
                tilesX,
                tilesY,
                mapIds,
                frameIds
        );
        screen.attachRenderers(colorCache);
        screens.put(screen.getId(), screen);
        save();

        int pw = screen.getPixelWidth();
        int ph = screen.getPixelHeight();
        byte[] placeholder = new byte[pw * ph * 3];
        for (int i = 0; i < placeholder.length; i += 3) {
            if (kind == ScreenKind.GOOGLE) {
                placeholder[i] = (byte) 255;
                placeholder[i + 1] = (byte) 255;
                placeholder[i + 2] = (byte) 255;
            } else {
                placeholder[i] = 40;
                placeholder[i + 1] = 40;
                placeholder[i + 2] = 48;
            }
        }
        screen.pushFrame(placeholder, pw, ph);
        broadcastMaps(screen, player.getLocation(), 48);

        player.sendMessage("§7Placed §f" + mapIds.size() + "§7 map frames (" + tilesX + "×" + tilesY
                + ") facing §f" + facing);

        plugin.getLogger().info("Placed " + kind + " screen " + screen.getId() + " "
                + tilesX + "x" + tilesY + " facing " + facing + " maps=" + mapIds);

        return screen;
    }

    public void pushImage(DoomScreen screen, byte[] rgb, int w, int h) {
        if (screen.isHidden()) {
            return;
        }
        screen.pushFrame(rgb, w, h);
    }

    /** Remove item frames so the wall face is plain air; keeps screen data for later show. */
    public void hideScreen(DoomScreen screen) {
        if (screen.isHidden()) {
            return;
        }
        var input = plugin.getInputListener();
        if (input != null) {
            input.stopPlayingOnScreen(screen.getId());
        }
        World world = Bukkit.getWorld(screen.getWorldName());
        if (world != null) {
            screen.removeDisplays(world);
            screen.replaceDisplayIds(List.of());
        }
        screen.setHidden(true);
        save();
        plugin.getLogger().info("Hid " + screen.getKind() + " screen " + screen.getId());
    }

    /** Respawn map frames for a previously hidden screen. */
    public void showScreen(DoomScreen screen) throws Exception {
        if (!screen.isHidden()) {
            return;
        }
        World world = Bukkit.getWorld(screen.getWorldName());
        if (world == null) {
            throw new IllegalStateException("World not loaded: " + screen.getWorldName());
        }

        int minX = screen.getMinX();
        int minY = screen.getMinY();
        int minZ = screen.getMinZ();
        int maxX = screen.getMaxX();
        int maxY = screen.getMaxY();
        int maxZ = screen.getMaxZ();
        int sizeX = screen.getSizeX();
        int sizeZ = screen.getSizeZ();
        BlockFace facing = screen.getFacing();
        int tilesX = screen.getTilesX();
        int tilesY = screen.getTilesY();

        // Clear any leftover entities in the region
        Location sweep = screen.getCenter(world);
        double radius = Math.max(Math.max(tilesX, tilesY), 2) + 3;
        world.getNearbyEntities(sweep, radius, radius, radius).forEach(e -> {
            if (e.getScoreboardTags().contains("minedoom_screen")
                    || e instanceof org.bukkit.entity.ItemFrame
                    || e instanceof org.bukkit.entity.ItemDisplay) {
                Location el = e.getLocation();
                if (el.getBlockX() >= minX - 1 && el.getBlockX() <= maxX + 1
                        && el.getBlockY() >= minY - 1 && el.getBlockY() <= maxY + 1
                        && el.getBlockZ() >= minZ - 1 && el.getBlockZ() <= maxZ + 1) {
                    e.remove();
                }
            }
        });

        List<UUID> frameIds = new ArrayList<>();
        List<org.bukkit.entity.ItemFrame> frames = new ArrayList<>();
        String mapLabel = screen.getKind() == ScreenKind.GOOGLE ? "§eGoogle" : "§cMineDoom";

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                Location wallLoc = tileLocation(world, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeZ, facing, tx, ty);
                Block wallBlock = wallLoc.getBlock();
                if (!wallBlock.getType().isSolid()) {
                    throw new IllegalStateException("Cannot show screen — wall block missing at "
                            + wallBlock.getX() + "," + wallBlock.getY() + "," + wallBlock.getZ());
                }
                org.bukkit.entity.ItemFrame frame = FramePlacer.spawnOnWallFace(world, wallBlock, facing);
                frames.add(frame);
                frameIds.add(frame.getUniqueId());
            }
        }

        List<Integer> mapIds = screen.getMapIds();
        for (int i = 0; i < frames.size(); i++) {
            org.bukkit.entity.ItemFrame frame = frames.get(i);
            MapView view = null;
            if (i < mapIds.size()) {
                view = Bukkit.getMap(mapIds.get(i));
            }
            if (view == null) {
                view = Bukkit.createMap(world);
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                view.setLocked(true);
                for (var r : new ArrayList<>(view.getRenderers())) {
                    view.removeRenderer(r);
                }
                if (i < mapIds.size()) {
                    mapIds.set(i, view.getId());
                } else {
                    mapIds.add(view.getId());
                }
            }
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            meta.setMapView(view);
            meta.setDisplayName(mapLabel);
            mapItem.setItemMeta(meta);
            frame.setItem(mapItem, false);
        }

        screen.replaceDisplayIds(frameIds);
        screen.setHidden(false);
        screen.attachRenderers(colorCache);
        save();
        broadcastMaps(screen, sweep, 64);
        plugin.getLogger().info("Showed " + screen.getKind() + " screen " + screen.getId());
    }

    public void broadcastMaps(DoomScreen screen, Location around, double radius) {
        if (around.getWorld() == null) {
            return;
        }
        for (Player p : around.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(around) <= radius * radius) {
                for (int mapId : screen.getMapIds()) {
                    MapView view = Bukkit.getMap(mapId);
                    if (view != null) {
                        p.sendMap(view);
                    }
                }
            }
        }
    }

    private static Location tileLocation(
            World world,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int sizeX,
            int sizeZ,
            BlockFace facing,
            int tx,
            int ty
    ) {
        int y = maxY - ty;
        int x;
        int z;
        if (sizeZ == 1) {
            boolean flipX = facing == BlockFace.NORTH;
            x = flipX ? (maxX - tx) : (minX + tx);
            z = minZ;
        } else if (sizeX == 1) {
            boolean flipZ = facing == BlockFace.WEST;
            x = minX;
            z = flipZ ? (maxZ - tx) : (minZ + tx);
        } else {
            x = minX + tx;
            z = minZ;
        }
        return new Location(world, x, y, z);
    }

    private static BlockFace resolveFacingFromPlayerPosition(
            Player player,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int sizeX,
            int sizeZ
    ) {
        double cx = (minX + maxX) / 2.0 + 0.5;
        double cz = (minZ + maxZ) / 2.0 + 0.5;
        Location eye = player.getEyeLocation();
        double dx = eye.getX() - cx;
        double dz = eye.getZ() - cz;

        if (sizeZ == 1) {
            return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        if (sizeX == 1) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
    }

    public boolean remove(DoomScreen screen) {
        screens.remove(screen.getId());
        World world = Bukkit.getWorld(screen.getWorldName());
        if (world != null) {
            screen.removeDisplays(world);
        }
        save();
        return true;
    }

    private int broadcastTick = 0;

    public void pushFrameToAll(byte[] rgb, int w, int h) {
        broadcastTick++;
        // Item frames watch maps themselves; occasional sendMap helps first paint
        boolean send = (broadcastTick % 20) == 0;
        for (DoomScreen screen : screens.values()) {
            if (screen.getKind() != ScreenKind.DOOM || screen.isHidden()) {
                continue;
            }
            screen.pushFrame(rgb, w, h);
            if (send) {
                World world = Bukkit.getWorld(screen.getWorldName());
                if (world != null) {
                    broadcastMaps(screen, screen.getCenter(world), 64);
                }
            }
        }
    }

    public void load() {
        if (!storeFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storeFile);
        var section = yaml.getConfigurationSection("screens");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var child = section.getConfigurationSection(key);
            if (child == null) continue;
            try {
                DoomScreen screen = DoomScreen.read(child);
                screen.attachRenderers(colorCache);
                screens.put(screen.getId(), screen);
                if (screen.isHidden()) {
                    World world = Bukkit.getWorld(screen.getWorldName());
                    if (world != null) {
                        screen.removeDisplays(world);
                        screen.replaceDisplayIds(List.of());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load screen " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + screens.size() + " Doom screen(s)");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        DoomScreen.saveAll(yaml, new ArrayList<>(screens.values()));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(storeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save screens: " + e.getMessage());
        }
    }

    public void shutdown() {
        // leave frames in world
    }

    public MapColorCache getColorCache() {
        return colorCache;
    }
}
