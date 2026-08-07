package com.minedoom.screen;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;

/**
 * Spawns item frames in the air cell on a wall face (never inside the solid block).
 */
public final class FramePlacer {

    private FramePlacer() {}

    /**
     * @param wallBlock solid wall tile from the selection
     * @param outward   direction from the wall toward the viewer (= item frame facing)
     */
    public static ItemFrame spawnOnWallFace(World world, Block wallBlock, BlockFace outward) {
        if (outward != BlockFace.NORTH && outward != BlockFace.SOUTH
                && outward != BlockFace.EAST && outward != BlockFace.WEST) {
            throw new IllegalStateException("Facing must be NORTH/SOUTH/EAST/WEST, got " + outward);
        }

        Block air = wallBlock.getRelative(outward);
        if (air.getType().isSolid()) {
            throw new IllegalStateException(
                    "No air on the " + outward + " side of "
                            + wallBlock.getX() + "," + wallBlock.getY() + "," + wallBlock.getZ()
                            + " (found " + air.getType() + "). Stand on the open side of the wall.");
        }
        if (!air.getType().isAir()) {
            air.setType(Material.AIR);
        }

        Location center = air.getLocation().add(0.5, 0.5, 0.5);
        for (Entity e : world.getNearbyEntities(center, 0.51, 0.51, 0.51)) {
            if (e instanceof ItemFrame) {
                e.remove();
            }
        }

        Location spawnLoc = air.getLocation();
        ItemFrame frame = world.spawn(spawnLoc, ItemFrame.class);
        boolean attached = frame.setFacingDirection(outward, true);
        frame.setVisible(true);
        frame.setFixed(true);
        frame.setInvulnerable(true);
        frame.setSilent(true);
        frame.setGravity(false);

        if (!attached) {
            // Retry once with force already true — if still wrong facing, fail clearly
            frame.setFacingDirection(outward, true);
        }

        if (!frame.isValid()) {
            throw new IllegalStateException("Item frame failed to stay at "
                    + air.getX() + "," + air.getY() + "," + air.getZ()
                    + " facing " + outward);
        }

        if (frame.getFacing() != outward) {
            frame.remove();
            throw new IllegalStateException("Item frame facing became " + frame.getFacing()
                    + " instead of " + outward + " at "
                    + air.getX() + "," + air.getY() + "," + air.getZ());
        }

        return frame;
    }
}
