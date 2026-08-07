package com.minedoom.screen;

import com.minedoom.MineDoomPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

        return buildScreen(player, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeZ, facing, tilesX, tilesY);
    }

    /**
     * Place a 1×1 screen on the wall face the player is looking at.
     */
    public DoomScreen placeOnTargetBlock(Player player) throws Exception {
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
                wall.getX(), wall.getY(), wall.getZ(),
                wall.getX(), wall.getY(), wall.getZ(),
                1, 1,
                face,
                1, 1
        );
    }

    private DoomScreen buildScreen(
            Player player,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int sizeX, int sizeZ,
            BlockFace facing,
            int tilesX, int tilesY
    ) throws Exception {
        plugin.getEngine().ensureStarted();

        World world = player.getWorld();
        List<Integer> mapIds = new ArrayList<>();
        List<UUID> frameIds = new ArrayList<>();

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                Location wallLoc = tileLocation(world, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeZ, facing, tx, ty);
                Block wallBlock = wallLoc.getBlock();
                if (!wallBlock.getType().isSolid()) {
                    throw new IllegalStateException("Selection block at "
                            + wallBlock.getX() + "," + wallBlock.getY() + "," + wallBlock.getZ()
                            + " is not solid (" + wallBlock.getType() + "). Select solid wall blocks.");
                }

                // Spawn in AIR on the player's side — never inside the wall
                ItemFrame frame = FramePlacer.spawnOnWallFace(world, wallBlock, facing);

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
                meta.setDisplayName("§cMineDoom Screen");
                mapItem.setItemMeta(meta);
                frame.setItem(mapItem, false);
                player.sendMap(view);

                mapIds.add(view.getId());
                frameIds.add(frame.getUniqueId());

                plugin.getLogger().info("Frame at " + frame.getLocation().getBlockX() + ","
                        + frame.getLocation().getBlockY() + "," + frame.getLocation().getBlockZ()
                        + " facing=" + frame.getFacing() + " (wanted " + facing + ")");
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
            placeholder[i] = 40;
            placeholder[i + 1] = 40;
            placeholder[i + 2] = 48;
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
