package com.minedoom.screen;

import com.minedoom.MineDoomPlugin;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
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
import org.bukkit.util.Vector;

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
    private final MapColorCache colorCache = new MapColorCache();
    private final ConcurrentHashMap<UUID, DoomScreen> screens = new ConcurrentHashMap<>();
    private final File storeFile;

    public ScreenManager(MineDoomPlugin plugin) {
        this.plugin = plugin;
        this.storeFile = new File(plugin.getDataFolder(), "screens.yml");
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

    public DoomScreen placeFromWorldEdit(Player player) throws Exception {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            throw new IllegalStateException("WorldEdit is required. Install WorldEdit, use //wand, select two corners of a flat wall, then /doom place");
        }

        SessionManager manager = WorldEdit.getInstance().getSessionManager();
        LocalSession session = manager.get(BukkitAdapter.adapt(player));
        Region region;
        try {
            region = session.getSelection(session.getSelectionWorld());
        } catch (IncompleteRegionException e) {
            throw new IllegalStateException("Make a WorldEdit selection first (//wand left + right click).");
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int sizeX = max.x() - min.x() + 1;
        int sizeY = max.y() - min.y() + 1;
        int sizeZ = max.z() - min.z() + 1;

        // Selection must be a flat wall (exactly one axis thickness = 1)
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

        BlockFace facing = resolveFacing(player, sizeX, sizeZ);
        int tilesX;
        int tilesY = sizeY;
        if (sizeZ == 1) {
            tilesX = sizeX;
        } else if (sizeX == 1) {
            tilesX = sizeZ;
        } else {
            // corner pillar — use the larger horizontal span
            tilesX = Math.max(sizeX, sizeZ);
        }

        if (tilesX < 1 || tilesY < 1) {
            throw new IllegalStateException("Selection too small.");
        }
        if (tilesX * tilesY > 64) {
            throw new IllegalStateException("Screen too large (max 64 maps). Try a smaller selection, e.g. 3x2.");
        }

        World world = player.getWorld();
        List<Integer> mapIds = new ArrayList<>();
        List<UUID> frameIds = new ArrayList<>();

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                Location blockLoc = tileLocation(world, min, max, sizeX, sizeZ, facing, tx, ty, tilesY);
                // Item frames live in the air block in front of the wall, facing the player
                Location frameLoc = blockLoc.clone().add(facing.getModX(), facing.getModY(), facing.getModZ());
                frameLoc.add(0.5, 0.5, 0.5);
                ItemFrame frame = world.spawn(frameLoc, ItemFrame.class, f -> {
                    f.setFacingDirection(facing, true);
                    f.setVisible(false);
                    f.setFixed(true);
                    f.setInvulnerable(true);
                    f.setSilent(true);
                });

                MapView view = Bukkit.createMap(world);
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                view.getRenderers().forEach(view::removeRenderer);

                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) mapItem.getItemMeta();
                meta.setMapView(view);
                meta.setDisplayName("§cMineDoom Screen");
                mapItem.setItemMeta(meta);
                frame.setItem(mapItem);

                mapIds.add(view.getId());
                frameIds.add(frame.getUniqueId());
            }
        }

        DoomScreen screen = new DoomScreen(
                UUID.randomUUID(),
                world.getName(),
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z(),
                facing,
                tilesX,
                tilesY,
                mapIds,
                frameIds
        );
        screen.attachRenderers(colorCache);
        screens.put(screen.getId(), screen);
        save();

        // Kick a black frame so maps aren't empty
        byte[] black = new byte[plugin.getEngine().getWidth() * plugin.getEngine().getHeight() * 3];
        screen.pushFrame(black, plugin.getEngine().getWidth(), plugin.getEngine().getHeight());

        return screen;
    }

    private static Location tileLocation(
            World world,
            BlockVector3 min,
            BlockVector3 max,
            int sizeX,
            int sizeZ,
            BlockFace facing,
            int tx,
            int ty,
            int tilesY
    ) {
        int y = max.y() - ty; // top row first in map index 0
        int x;
        int z;
        if (sizeZ == 1) {
            // Wall on X/Y plane
            boolean flipX = facing == BlockFace.NORTH;
            x = flipX ? (max.x() - tx) : (min.x() + tx);
            z = min.z();
        } else if (sizeX == 1) {
            boolean flipZ = facing == BlockFace.WEST;
            x = min.x();
            z = flipZ ? (max.z() - tx) : (min.z() + tx);
        } else {
            x = min.x() + tx;
            z = min.z();
        }
        return new Location(world, x, y, z);
    }

    private static BlockFace resolveFacing(Player player, int sizeX, int sizeZ) {
        Vector dir = player.getLocation().getDirection();
        if (sizeZ == 1) {
            return dir.getZ() < 0 ? BlockFace.NORTH : BlockFace.SOUTH;
        }
        if (sizeX == 1) {
            return dir.getX() < 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        // fallback: player's horizontal facing
        float yaw = player.getLocation().getYaw();
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
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
        // leave frames in world; renderers just stop updating
    }

    public MapColorCache getColorCache() {
        return colorCache;
    }
}
