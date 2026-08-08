package com.chunkboomerits.paper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * OP Hole Filler wand — fill holes / wall gaps with natural-looking blocks.
 */
public final class HoleFillerListener implements Listener {
	private static final int MAX_RADIUS_HOLE = 14;
	private static final int MAX_BLOCKS_HOLE = 2500;
	private static final int MAX_RADIUS_WALL = 12;
	private static final int MAX_BLOCKS_WALL = 1200;

	private final Map<UUID, List<NaturalFiller.UndoBlock>> lastUndo = new HashMap<>();

	@EventHandler(priority = EventPriority.HIGH)
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		Player player = event.getPlayer();
		ItemStack item = player.getInventory().getItemInMainHand();
		if (!OpItems.isHoleFiller(item)) {
			return;
		}

		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
				&& action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
			return;
		}

		event.setCancelled(true);

		if (!player.isOp() && !player.hasPermission("chunkboomerits.holefiller")) {
			player.sendMessage(Component.text("Hole Filler is OP-only.", NamedTextColor.RED));
			return;
		}

		// Sneak + right-click: cycle mode
		if (player.isSneaking() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
			NaturalFiller.Mode next = OpItems.cycleHoleFillerMode(item);
			player.sendMessage(Component.text("Hole Filler mode: " + next.name(), NamedTextColor.AQUA));
			player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
			return;
		}

		// Left-click: undo last fill
		if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
			List<NaturalFiller.UndoBlock> undo = lastUndo.remove(player.getUniqueId());
			if (undo == null || undo.isEmpty()) {
				player.sendMessage(Component.text("Nothing to undo.", NamedTextColor.GRAY));
				return;
			}
			NaturalFiller.undo(player.getWorld(), undo);
			player.sendMessage(Component.text("Undid last fill (" + undo.size() + " blocks).", NamedTextColor.YELLOW));
			player.playSound(player.getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 0.7f, 0.8f);
			return;
		}

		// Right-click: fill
		Block startAir;
		BlockFace face = event.getBlockFace();
		if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
			Block clicked = event.getClickedBlock();
			Block adjacent = clicked.getRelative(face);
			if (adjacent.getType().isAir()) {
				startAir = adjacent;
			} else if (clicked.getType().isAir()) {
				startAir = clicked;
			} else {
				startAir = findNearbyAir(clicked);
			}
		} else {
			Block target = player.getTargetBlockExact(8);
			if (target == null) {
				player.sendMessage(Component.text("Look at a hole or wall gap to fill.", NamedTextColor.RED));
				return;
			}
			startAir = target.getType().isAir() ? target : findNearbyAir(target);
			face = player.getFacing();
		}

		if (startAir == null || !startAir.getType().isAir()) {
			player.sendMessage(Component.text("No air gap found there.", NamedTextColor.RED));
			return;
		}

		NaturalFiller.Mode mode = OpItems.holeFillerMode(item);
		NaturalFiller.Result result = mode == NaturalFiller.Mode.WALL
				? NaturalFiller.fill(mode, startAir, face, MAX_RADIUS_WALL, MAX_BLOCKS_WALL)
				: NaturalFiller.fill(mode, startAir, face, MAX_RADIUS_HOLE, MAX_BLOCKS_HOLE);

		if (result.filled() == 0) {
			player.sendMessage(Component.text("Nothing to fill.", NamedTextColor.GRAY));
			return;
		}

		lastUndo.put(player.getUniqueId(), result.undo());
		player.sendMessage(Component.text(
				"Filled " + result.filled() + " blocks (" + mode.name().toLowerCase() + "). Left-click to undo.",
				NamedTextColor.GREEN
		));
		player.playSound(startAir.getLocation(), Sound.BLOCK_GRASS_PLACE, 0.8f, 0.9f);
		startAir.getWorld().spawnParticle(Particle.CLOUD, startAir.getLocation().add(0.5, 0.5, 0.5),
				12, 0.6, 0.6, 0.6, 0.01);
	}

	private static Block findNearbyAir(Block origin) {
		for (BlockFace face : BlockFace.values()) {
			if (!face.isCartesian()) {
				continue;
			}
			Block rel = origin.getRelative(face);
			if (rel.getType().isAir()) {
				return rel;
			}
		}
		return null;
	}
}
