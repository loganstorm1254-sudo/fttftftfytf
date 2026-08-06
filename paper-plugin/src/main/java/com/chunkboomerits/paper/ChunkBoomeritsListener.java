package com.chunkboomerits.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public final class ChunkBoomeritsListener implements Listener {
	private final ChunkBoomeritsPlugin plugin;

	public ChunkBoomeritsListener(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		Player player = event.getPlayer();
		ItemStack item = player.getInventory().getItemInMainHand();
		if (!ChunkBoomeritsItems.isChunkBoomerits(item)) {
			return;
		}

		event.setCancelled(true);

		if (!player.hasPermission("chunkboomerits.use")) {
			player.sendMessage(Component.text("Chunk Boomerits is OP-only.", NamedTextColor.RED));
			return;
		}

		Snowball ball = player.launchProjectile(Snowball.class);
		ball.setGravity(true);
		ball.setVelocity(player.getLocation().getDirection().multiply(1.35));
		ball.getPersistentDataContainer().set(ChunkBoomeritsItems.KEY, PersistentDataType.BYTE, (byte) 1);
		ball.setShooter(player);

		player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.7f, 0.5f);

		if (player.getGameMode() != GameMode.CREATIVE) {
			item.setAmount(item.getAmount() - 1);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onHit(ProjectileHitEvent event) {
		if (!(event.getEntity() instanceof Snowball ball)) {
			return;
		}
		if (!ball.getPersistentDataContainer().has(ChunkBoomeritsItems.KEY, PersistentDataType.BYTE)) {
			return;
		}

		ProjectileSource shooter = ball.getShooter();
		if (!(shooter instanceof Player player) || !player.hasPermission("chunkboomerits.use")) {
			ball.remove();
			return;
		}

		Location hit = ball.getLocation();
		if (event.getHitBlock() != null) {
			hit = event.getHitBlock().getLocation();
		}

		ChunkDeleter.deleteChunk(hit.getWorld(), hit.getBlockX() >> 4, hit.getBlockZ() >> 4, hit);
		ball.remove();
	}
}
