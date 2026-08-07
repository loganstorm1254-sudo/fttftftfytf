package com.minedoom.screen;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A rectangular wall of item-frame maps that displays the DOOM framebuffer.
 */
public final class DoomScreen {

    private final UUID id;
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final BlockFace facing;
    private final int tilesX;
    private final int tilesY;
    private final List<Integer> mapIds;
    private final List<UUID> frameIds;
    private transient DoomMapRenderer[] renderers;

    public DoomScreen(
            UUID id,
            String worldName,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            BlockFace facing,
            int tilesX,
            int tilesY,
            List<Integer> mapIds,
            List<UUID> frameIds
    ) {
        this.id = id;
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
        this.frameIds = new ArrayList<>(frameIds);
    }

    public UUID getId() {
        return id;
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

    public List<UUID> getFrameIds() {
        return frameIds;
    }

    public Location getCenter(org.bukkit.World world) {
        return new Location(world, (minX + maxX) / 2.0 + 0.5, (minY + maxY) / 2.0 + 0.5, (minZ + maxZ) / 2.0 + 0.5);
    }

    public Location getSeatLocation(org.bukkit.World world) {
        Location center = getCenter(world);
        double dist = 3.0;
        return switch (facing) {
            case NORTH -> center.clone().add(0, -1, dist);
            case SOUTH -> center.clone().add(0, -1, -dist);
            case WEST -> center.clone().add(dist, -1, 0);
            case EAST -> center.clone().add(-dist, -1, 0);
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
            view.getRenderers().forEach(view::removeRenderer);
            int tileX = i % tilesX;
            int tileY = i / tilesX;
            DoomMapRenderer renderer = new DoomMapRenderer(this, tileX, tileY, colorCache);
            view.addRenderer(renderer);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            renderers[i] = renderer;
        }
    }

    public void pushFrame(byte[] doomRgb, int doomW, int doomH) {
        if (renderers == null) {
            return;
        }
        for (DoomMapRenderer renderer : renderers) {
            if (renderer != null) {
                renderer.updateFromDoom(doomRgb, doomW, doomH);
            }
        }
    }

    public void removeFrames(org.bukkit.World world) {
        for (UUID frameId : frameIds) {
            Entity e = world.getEntity(frameId);
            if (e instanceof ItemFrame frame) {
                frame.setItem(null);
                frame.remove();
            }
        }
    }

    public void write(ConfigurationSection section) {
        section.set("id", id.toString());
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
        List<String> frames = new ArrayList<>();
        for (UUID u : frameIds) {
            frames.add(u.toString());
        }
        section.set("frameIds", frames);
    }

    public static DoomScreen read(ConfigurationSection section) {
        List<Integer> maps = section.getIntegerList("mapIds");
        List<UUID> frames = new ArrayList<>();
        for (String s : section.getStringList("frameIds")) {
            frames.add(UUID.fromString(s));
        }
        return new DoomScreen(
                UUID.fromString(section.getString("id")),
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
                frames
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
