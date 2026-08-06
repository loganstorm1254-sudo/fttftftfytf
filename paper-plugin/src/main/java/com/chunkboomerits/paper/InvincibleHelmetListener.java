package com.chunkboomerits.paper;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Copper helmet that cancels all damage / fatal effects while worn.
 */
public final class InvincibleHelmetListener implements Listener {

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		if (!wearingInvincibleHelmet(player)) {
			return;
		}

		event.setCancelled(true);

		if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
			rescueFromVoid(player);
		}

		// Keep the player topped off if something still shaved HP somehow.
		if (player.getHealth() < player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		if (!wearingInvincibleHelmet(player)) {
			return;
		}
		event.setCancelled(true);
		player.setHealth(player.getMaxHealth());
		player.setFoodLevel(20);
		player.setSaturation(20f);
		player.setFireTicks(0);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHarmfulPotion(EntityPotionEffectEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		if (!wearingInvincibleHelmet(player)) {
			return;
		}
		if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
				&& event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
			return;
		}
		if (event.getNewEffect() == null) {
			return;
		}
		PotionEffectType type = event.getNewEffect().getType();
		if (isHarmful(type)) {
			event.setCancelled(true);
		}
	}

	private static boolean wearingInvincibleHelmet(Player player) {
		ItemStack helmet = player.getInventory().getHelmet();
		return OpItems.isInvincibleHelmet(helmet);
	}

	private static boolean isHarmful(PotionEffectType type) {
		return type.equals(PotionEffectType.POISON)
				|| type.equals(PotionEffectType.WITHER)
				|| type.equals(PotionEffectType.INSTANT_DAMAGE)
				|| type.equals(PotionEffectType.SLOWNESS)
				|| type.equals(PotionEffectType.MINING_FATIGUE)
				|| type.equals(PotionEffectType.BLINDNESS)
				|| type.equals(PotionEffectType.HUNGER)
				|| type.equals(PotionEffectType.WEAKNESS)
				|| type.equals(PotionEffectType.LEVITATION)
				|| type.equals(PotionEffectType.UNLUCK)
				|| type.equals(PotionEffectType.DARKNESS)
				|| type.equals(PotionEffectType.WIND_CHARGED)
				|| type.equals(PotionEffectType.WEAVING)
				|| type.equals(PotionEffectType.OOZING)
				|| type.equals(PotionEffectType.INFESTED);
	}

	private static void rescueFromVoid(Player player) {
		World world = player.getWorld();
		Location spawn = world.getSpawnLocation();
		Location safe = spawn.clone();
		safe.setY(Math.max(spawn.getY(), world.getHighestBlockYAt(spawn) + 1.0));
		player.teleport(safe);
		player.setFallDistance(0f);
		player.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
	}
}
