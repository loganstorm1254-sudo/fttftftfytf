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
 * OP Hole Filler — fills only real pockets/gaps, never builds cliffs or mountains.
 */
public final class HoleFillerListener implements Listener {
	/** Small on purpose — this is a gap fixer, not a terraformer. */
	private static final int MAX_RADIUS_HOLE = 5;
	private static final int MAX_BLOCKS_HOLE = 200;
	private static final int MAX_RADIUS_WALL = 4;
	private static final int MAX_BLOCKS_WALL = 80;

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

		if (player.isSneaking() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
			NaturalFiller.Mode next = OpItems.cycleHoleFillerMode(item);
			player.sendMessage(Component.text("Hole Filler mode: " + next.name(), NamedTextColor.AQUA));
			player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
			return;
		}

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

		Block startAir;
		BlockFace face = event.getBlockFace() == null ? player.getFacing() : event.getBlockFace();
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
			Block target = player.getTargetBlockExact(6);
			if (target == null) {
				player.sendMessage(Component.text("Look at a small hole or wall gap.", NamedTextColor.RED));
				return;
			}
			startAir = target.getType().isAir() ? target : findNearbyAir(target);
			face = player.getFacing();
		}

		if (startAir == null || !startAir.getType().isAir()) {
			player.sendMessage(Component.text("No gap there. Click air inside a hole or missing wall block.", NamedTextColor.RED));
			return;
		}

		NaturalFiller.Mode mode = OpItems.holeFillerMode(item);
		int radius = mode == NaturalFiller.Mode.WALL ? MAX_RADIUS_WALL : MAX_RADIUS_HOLE;
		int maxBlocks = mode == NaturalFiller.Mode.WALL ? MAX_BLOCKS_WALL : MAX_BLOCKS_HOLE;
		NaturalFiller.Result result = NaturalFiller.fill(mode, startAir, face, radius, maxBlocks);

		if (result.filled() == 0) {
			player.sendMessage(Component.text(
					"Not a fillable gap (need a real pocket / missing wall block — won't build cliffs).",
					NamedTextColor.GRAY
			));
			return;
		}

		lastUndo.put(player.getUniqueId(), result.undo());
		player.sendMessage(Component.text(
				"Filled " + result.filled() + " gap block(s) (" + mode.name().toLowerCase() + "). Left-click undo.",
				NamedTextColor.GREEN
		));
		player.playSound(startAir.getLocation(), Sound.BLOCK_STONE_PLACE, 0.7f, 1.1f);
		startAir.getWorld().spawnParticle(Particle.CLOUD, startAir.getLocation().add(0.5, 0.5, 0.5),
				6, 0.25, 0.25, 0.25, 0.01);
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
