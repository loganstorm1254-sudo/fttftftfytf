package com.minedoom.screen;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Places a filled map as a flat {@link ItemDisplay} on a wall face.
 * <p>
 * ItemFrames were unreliable: Bukkit's hanging spawner attaches to neighboring
 * wall blocks and adjacent frames collide, leaving a single tile.
 */
public final class FramePlacer {

    private FramePlacer() {}

    public static ItemDisplay spawnMapDisplay(World world, Block wallBlock, BlockFace outward, ItemStack mapItem) {
        if (outward != BlockFace.NORTH && outward != BlockFace.SOUTH
                && outward != BlockFace.EAST && outward != BlockFace.WEST) {
            throw new IllegalStateException("Facing must be NORTH/SOUTH/EAST/WEST, got " + outward);
        }

        Location loc = wallBlock.getLocation().add(0.5, 0.5, 0.5);
        loc.add(outward.getDirection().multiply(0.51));
        loc.setDirection(outward.getDirection());

        world.getNearbyEntities(loc, 0.45, 0.45, 0.45).forEach(e -> {
            if (e instanceof ItemDisplay || e instanceof org.bukkit.entity.ItemFrame) {
                e.remove();
            }
        });

        // Tip the flat map item upright (item model lies flat by default), then entity yaw faces outward
        AxisAngle4f tipUp = new AxisAngle4f((float) (-Math.PI / 2.0), 1f, 0f, 0f);
        Transformation transform = new Transformation(
                new Vector3f(0f, 0f, 0f),
                tipUp,
                new Vector3f(1.0f, 1.0f, 1.0f),
                new AxisAngle4f(0f, 0f, 0f, 1f)
        );

        ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(mapItem.clone());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(transform);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setDisplayWidth(1.0f);
            d.setDisplayHeight(1.0f);
            d.setTeleportDuration(0);
            d.setInterpolationDuration(0);
            d.setPersistent(true);
            d.setInvulnerable(true);
            d.addScoreboardTag("minedoom_screen");
        });

        if (!display.isValid()) {
            throw new IllegalStateException("Failed to spawn map display on "
                    + wallBlock.getX() + "," + wallBlock.getY() + "," + wallBlock.getZ());
        }
        return display;
    }
}
