package com.minedoom.screen;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;

/**
 * Spawns item frames on a wall face without the Bukkit hanging-collision bug.
 *
 * <p>Bukkit's ItemFrame spawner searches neighboring blocks for attachment and
 * aborts when another hanging entity is nearby — that left only one corner tile.
 * We spawn in empty sky first, then teleport onto the wall face.
 */
public final class FramePlacer {

    private FramePlacer() {}

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

        // Clear anything already in this air cell
        Location airCenter = air.getLocation().add(0.5, 0.5, 0.5);
        for (Entity e : world.getNearbyEntities(airCenter, 0.45, 0.45, 0.45)) {
            if (e instanceof ItemFrame || e instanceof ItemDisplay) {
                e.remove();
            }
        }

        // Spawn far above so createHanging won't collide with sibling frames
        Location sky = air.getLocation().clone().add(0, 80 + (air.getY() % 7), 0);
        ItemFrame frame = world.spawn(sky, ItemFrame.class, f -> {
            f.setVisible(true);
            f.setFixed(true);
            f.setInvulnerable(true);
            f.setSilent(true);
            f.setGravity(false);
            f.setItemDropChance(0f);
            f.addScoreboardTag("minedoom_screen");
        });

        // Move onto the wall face and force facing toward the viewer
        Location seat = air.getLocation(); // block origin — Bukkit hanging pos
        frame.teleport(seat);
        boolean ok = frame.setFacingDirection(outward, true);
        if (!ok) {
            frame.setFacingDirection(outward, true);
        }

        // If still inside the wall somehow, nudge back to air and re-face
        if (frame.getLocation().getBlock().equals(wallBlock)) {
            frame.teleport(seat);
            frame.setFacingDirection(outward, true);
        }

        if (!frame.isValid()) {
            throw new IllegalStateException("Item frame despawned at "
                    + air.getX() + "," + air.getY() + "," + air.getZ());
        }

        frame.setFixed(true);
        frame.setInvulnerable(true);
        return frame;
    }
}
