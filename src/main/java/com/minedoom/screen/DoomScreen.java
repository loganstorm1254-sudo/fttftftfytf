package com.minedoom.screen;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A rectangular wall of map displays (DOOM framebuffer or Google browser).
 */
public final class DoomScreen {

    private final UUID id;
    private final ScreenKind kind;
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final BlockFace facing;
    private final int tilesX;
    private final int tilesY;
    private final List<Integer> mapIds;
    private final List<UUID> displayIds;
    private boolean hidden;
    private transient DoomMapRenderer[] renderers;

    public DoomScreen(
            UUID id,
            ScreenKind kind,
            String worldName,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            BlockFace facing,
            int tilesX,
            int tilesY,
            List<Integer> mapIds,
            List<UUID> displayIds
    ) {
        this(id, kind, worldName, minX, minY, minZ, maxX, maxY, maxZ, facing, tilesX, tilesY, mapIds, displayIds, false);
    }

    public DoomScreen(
            UUID id,
            ScreenKind kind,
            String worldName,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            BlockFace facing,
            int tilesX,
            int tilesY,
            List<Integer> mapIds,
            List<UUID> displayIds,
            boolean hidden
    ) {
        this.id = id;
        this.kind = kind == null ? ScreenKind.DOOM : kind;
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.facing = facing;
        this.tilesX = tilesX;
        this.tilesY = tilesY;
        this.mapIds = new ArrayList<>(mapIds);
        this.displayIds = new ArrayList<>(displayIds);
        this.hidden = hidden;
    }

    public UUID getId() {
        return id;
    }

    public ScreenKind getKind() {
        return kind;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getTilesX() {
        return tilesX;
    }

    public int getTilesY() {
        return tilesY;
    }

    public int getPixelWidth() {
        return tilesX * 128;
    }

    public int getPixelHeight() {
        return tilesY * 128;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public List<Integer> getMapIds() {
        return mapIds;
    }

    /** @deprecated use {@link #getDisplayIds()} */
    public List<UUID> getFrameIds() {
        return displayIds;
    }

    public List<UUID> getDisplayIds() {
        return displayIds;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public int getSizeX() {
        return maxX - minX + 1;
    }

    public int getSizeZ() {
        return maxZ - minZ + 1;
    }

    public void replaceDisplayIds(List<UUID> newIds) {
        displayIds.clear();
        displayIds.addAll(newIds);
    }

    public Location getCenter(org.bukkit.World world) {
        return new Location(world, (minX + maxX) / 2.0 + 0.5, (minY + maxY) / 2.0 + 0.5, (minZ + maxZ) / 2.0 + 0.5);
    }

    public Location getSeatLocation(org.bukkit.World world) {
        Location center = getCenter(world);
        double dist = 3.0;
        return switch (facing) {
            case NORTH -> center.clone().add(0, -1, -dist);
            case SOUTH -> center.clone().add(0, -1, dist);
            case WEST -> center.clone().add(-dist, -1, 0);
            case EAST -> center.clone().add(dist, -1, 0);
            default -> center.clone().add(0, -1, dist);
        };
    }

    public void attachRenderers(MapColorCache colorCache) {
        renderers = new DoomMapRenderer[mapIds.size()];
        for (int i = 0; i < mapIds.size(); i++) {
            int mapId = mapIds.get(i);
            MapView view = org.bukkit.Bukkit.getMap(mapId);
            if (view == null) {
                continue;
            }
            for (var r : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(r);
            }
            int tileX = i % tilesX;
            int tileY = i / tilesX;
            DoomMapRenderer renderer = new DoomMapRenderer(this, tileX, tileY, colorCache);
            view.addRenderer(renderer);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(true);
            renderers[i] = renderer;
        }
    }

    public void pushFrame(byte[] doomRgb, int doomW, int doomH) {
        if (hidden || renderers == null) {
            return;
        }
        for (DoomMapRenderer renderer : renderers) {
            if (renderer != null) {
                renderer.updateFromDoom(doomRgb, doomW, doomH);
            }
        }
    }

    public void removeDisplays(org.bukkit.World world) {
        for (UUID displayId : displayIds) {
            Entity e = world.getEntity(displayId);
            if (e != null) {
                if (e instanceof org.bukkit.entity.ItemFrame frame) {
                    frame.setItem(null);
                }
                e.remove();
            }
        }
        Location c = getCenter(world);
        double radius = Math.max(tilesX, tilesY) + 2;
        for (Entity e : world.getNearbyEntities(c, radius, radius, radius)) {
            if (e.getScoreboardTags().contains("minedoom_screen")) {
                e.remove();
            }
        }
    }

    public void write(ConfigurationSection section) {
        section.set("id", id.toString());
        section.set("kind", kind.name());
        section.set("world", worldName);
        section.set("minX", minX);
        section.set("minY", minY);
        section.set("minZ", minZ);
        section.set("maxX", maxX);
        section.set("maxY", maxY);
        section.set("maxZ", maxZ);
        section.set("facing", facing.name());
        section.set("tilesX", tilesX);
        section.set("tilesY", tilesY);
        section.set("mapIds", mapIds);
        List<String> ids = new ArrayList<>();
        for (UUID u : displayIds) {
            ids.add(u.toString());
        }
        section.set("displayIds", ids);
        section.set("frameIds", ids); // back-compat
        section.set("hidden", hidden);
    }

    public static DoomScreen read(ConfigurationSection section) {
        List<Integer> maps = section.getIntegerList("mapIds");
        List<UUID> displays = new ArrayList<>();
        List<String> raw = section.getStringList("displayIds");
        if (raw.isEmpty()) {
            raw = section.getStringList("frameIds");
        }
        for (String s : raw) {
            displays.add(UUID.fromString(s));
        }
        return new DoomScreen(
                UUID.fromString(section.getString("id")),
                ScreenKind.fromString(section.getString("kind", "DOOM")),
                section.getString("world"),
                section.getInt("minX"),
                section.getInt("minY"),
                section.getInt("minZ"),
                section.getInt("maxX"),
                section.getInt("maxY"),
                section.getInt("maxZ"),
                BlockFace.valueOf(section.getString("facing", "NORTH")),
                section.getInt("tilesX"),
                section.getInt("tilesY"),
                maps,
                displays,
                section.getBoolean("hidden", false)
        );
    }

    public static void saveAll(YamlConfiguration yaml, List<DoomScreen> screens) {
        yaml.set("screens", null);
        int i = 0;
        for (DoomScreen screen : screens) {
            screen.write(yaml.createSection("screens." + i));
            i++;
        }
    }
}
