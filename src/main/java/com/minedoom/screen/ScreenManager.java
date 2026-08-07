package com.minedoom.screen;

import com.minedoom.MineDoomPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
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
        DoomScreen best = null;
        double bestDist = maxDist * maxDist;
        for (DoomScreen screen : screens.values()) {
            if (!screen.getWorldName().equals(loc.getWorld().getName())) {
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

        // Place on the side the player is standing on
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

        plugin.getEngine().ensureStarted();

        World world = player.getWorld();
        List<Integer> mapIds = new ArrayList<>();
        List<UUID> frameIds = new ArrayList<>();

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                Location wallBlock = tileLocation(world, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeZ, facing, tx, ty);

                // Item frames attach TO the wall block and hang on the `facing` side
                // (Bukkit setFacingDirection moves the entity onto that face)
                Location attachAt = wallBlock.getBlock().getLocation();

                attachAt.getWorld().getNearbyEntities(attachAt.clone().add(0.5, 0.5, 0.5), 0.75, 0.75, 0.75)
                        .stream()
                        .filter(e -> e instanceof ItemFrame)
                        .forEach(org.bukkit.entity.Entity::remove);

                ItemFrame frame = world.spawn(attachAt, org.bukkit.entity.GlowItemFrame.class, f -> {
                    f.setFacingDirection(facing, true);
                    f.setVisible(true);
                    f.setFixed(true);
                    f.setInvulnerable(true);
                    f.setSilent(true);
                    f.setGravity(false);
                });

                if (!frame.isValid()) {
                    throw new IllegalStateException("Failed to spawn item frame on wall at "
                            + attachAt.getBlockX() + "," + attachAt.getBlockY() + "," + attachAt.getBlockZ()
                            + " facing " + facing);
                }

                MapView view = Bukkit.createMap(world);
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                view.setLocked(true);
                view.getRenderers().clear();

                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) mapItem.getItemMeta();
                meta.setMapView(view);
                meta.setDisplayName("§cMineDoom Screen");
                mapItem.setItemMeta(meta);
                frame.setItem(mapItem, false);
                player.sendMap(view);

                mapIds.add(view.getId());
                frameIds.add(frame.getUniqueId());
            }
        }

        DoomScreen screen = new DoomScreen(
                UUID.randomUUID(),
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

        byte[] placeholder = new byte[plugin.getEngine().getWidth() * plugin.getEngine().getHeight() * 3];
        for (int i = 0; i < placeholder.length; i += 3) {
            placeholder[i] = 20;
            placeholder[i + 1] = 20;
            placeholder[i + 2] = 24;
        }
        screen.pushFrame(placeholder, plugin.getEngine().getWidth(), plugin.getEngine().getHeight());

        for (int mapId : mapIds) {
            MapView view = Bukkit.getMap(mapId);
            if (view != null) {
                player.sendMap(view);
            }
        }

        plugin.getLogger().info("Placed Doom screen " + screen.getId() + " "
                + tilesX + "x" + tilesY + " facing " + facing + " maps=" + mapIds);

        return screen;
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

    /**
     * Pick the wall face closest to the player (the side they are standing on).
     */
    private static BlockFace resolveFacingFromPlayerPosition(
            Player player,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int sizeX,
            int sizeZ
    ) {
        double cx = (minX + maxX) / 2.0 + 0.5;
        double cy = (minY + maxY) / 2.0 + 0.5;
        double cz = (minZ + maxZ) / 2.0 + 0.5;
        Location eye = player.getEyeLocation();
        double dx = eye.getX() - cx;
        double dz = eye.getZ() - cz;

        if (sizeZ == 1) {
            // Thin in Z — choose north vs south by where the player stands
            return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        if (sizeX == 1) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        // Fallback: largest horizontal offset
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
    }

    public boolean remove(DoomScreen screen) {
        screens.remove(screen.getId());
        World world = Bukkit.getWorld(screen.getWorldName());
        if (world != null) {
            screen.removeFrames(world);
        }
        save();
        return true;
    }

    public void pushFrameToAll(byte[] rgb, int w, int h) {
        for (DoomScreen screen : screens.values()) {
            screen.pushFrame(rgb, w, h);
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
