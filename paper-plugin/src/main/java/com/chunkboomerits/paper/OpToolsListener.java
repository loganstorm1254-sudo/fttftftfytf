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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public final class OpToolsListener implements Listener {
	@EventHandler(priority = EventPriority.HIGH)
	public void onBoomeritsUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		Player player = event.getPlayer();
		ItemStack item = player.getInventory().getItemInMainHand();
		if (!OpItems.isBoomerits(item)) {
			return;
		}

		event.setCancelled(true);

		if (!player.hasPermission("chunkboomerits.use")) {
			player.sendMessage(Component.text("Chunk Boomerits is OP-only.", NamedTextColor.RED));
			return;
		}

		Snowball ball = player.launchProjectile(Snowball.class);
		ball.setGravity(true);
		ball.setVelocity(player.getLocation().getDirection().multiply(1.45));
		ball.getPersistentDataContainer().set(OpItems.BOOMERITS_KEY, PersistentDataType.BYTE, (byte) 1);
		ball.setShooter(player);

		player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.7f, 0.45f);

		if (player.getGameMode() != GameMode.CREATIVE) {
			item.setAmount(item.getAmount() - 1);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onBoomeritsHit(ProjectileHitEvent event) {
		if (!(event.getEntity() instanceof Snowball ball)) {
			return;
		}
		if (!ball.getPersistentDataContainer().has(OpItems.BOOMERITS_KEY, PersistentDataType.BYTE)) {
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

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onKickSwordHit(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player attacker)) {
			return;
		}
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}

		ItemStack weapon = attacker.getInventory().getItemInMainHand();
		if (!OpItems.isKickSword(weapon)) {
			return;
		}

		event.setCancelled(true);

		if (!attacker.hasPermission("chunkboomerits.kicksword")) {
			attacker.sendMessage(Component.text("Kick Sword is OP-only.", NamedTextColor.RED));
			return;
		}

		if (attacker.getUniqueId().equals(victim.getUniqueId())) {
			attacker.sendMessage(Component.text("You can't kick yourself.", NamedTextColor.RED));
			return;
		}

		String reason = "Kicked by " + attacker.getName() + " with the Kick Sword";
		victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
		victim.kick(Component.text(reason, NamedTextColor.RED));

		attacker.sendMessage(Component.text("Kicked " + victim.getName() + " from the server.", NamedTextColor.LIGHT_PURPLE));
		ChunkBoomeritsPlugin.get().getLogger().info(attacker.getName() + " kick-sworded " + victim.getName());
	}
}
