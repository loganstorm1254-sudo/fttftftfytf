package com.chunkboomerits.paper;

import java.time.Duration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
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

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
	public void onBanSwordHit(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player attacker)) {
			return;
		}
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}

		ItemStack weapon = attacker.getInventory().getItemInMainHand();
		if (!OpItems.isBanSword(weapon)) {
			return;
		}

		// Cancel damage so creative / survival both just get banned
		event.setCancelled(true);

		if (!attacker.isOp() && !attacker.hasPermission("chunkboomerits.banhammer")) {
			attacker.sendMessage(Component.text("Ban Sword is OP-only.", NamedTextColor.RED));
			return;
		}

		if (attacker.getUniqueId().equals(victim.getUniqueId())) {
			attacker.sendMessage(Component.text("You can't ban yourself.", NamedTextColor.RED));
			return;
		}

		long millis = OpItems.banSwordDurationMillis(weapon);
		String length = OpItems.banSwordDurationLabel(weapon);
		String reason = "Banned by " + attacker.getName() + " with the Ban Sword (" + length + ")";

		Duration duration = millis < 0 ? null : Duration.ofMillis(millis);
		// kickPlayer=true — works even if the target is in creative
		victim.ban(reason, duration, attacker.getName(), true);

		victim.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.55f, 1.35f);
		attacker.sendMessage(Component.text(
				"Banned " + victim.getName() + " for " + length + ".",
				NamedTextColor.DARK_RED
		));
		ChunkBoomeritsPlugin.get().getLogger().info(
				attacker.getName() + " ban-sworded " + victim.getName() + " for " + length
		);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onKillHammerHit(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player attacker)) {
			return;
		}
		if (!(event.getEntity() instanceof LivingEntity victim)) {
			return;
		}

		ItemStack weapon = attacker.getInventory().getItemInMainHand();
		if (!OpItems.isKillHammer(weapon)) {
			return;
		}

		event.setCancelled(true);

		if (!attacker.hasPermission("chunkboomerits.killhammer")) {
			attacker.sendMessage(Component.text("Insta Kill Hammer is OP-only.", NamedTextColor.RED));
			return;
		}

		if (victim instanceof Player playerVictim && OpItems.isInvincibleHelmet(playerVictim.getInventory().getHelmet())) {
			attacker.sendMessage(Component.text(playerVictim.getName() + " is invincible.", NamedTextColor.YELLOW));
			victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);
			return;
		}

		victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
		victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.6f);
		victim.setHealth(0.0);

		if (victim instanceof Player killed) {
			attacker.sendMessage(Component.text("Insta-killed " + killed.getName() + ".", NamedTextColor.RED));
		}
	}
}
