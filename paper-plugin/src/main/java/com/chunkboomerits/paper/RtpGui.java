package com.chunkboomerits.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.chunkboomerits.paper.economy.GuiHolder;

/**
 * RTP distance picker — choose how many blocks away to teleport.
 */
public final class RtpGui implements Listener {
	private static final String TITLE = "Random Teleport";

	/** slot -> radius */
	private static final Map<Integer, Integer> OPTIONS = new LinkedHashMap<>();

	static {
		OPTIONS.put(10, 500);
		OPTIONS.put(11, 1_000);
		OPTIONS.put(12, 2_500);
		OPTIONS.put(13, 5_000);
		OPTIONS.put(14, 10_000);
		OPTIONS.put(15, 25_000);
	}

	private final RtpService rtp;

	public RtpGui(RtpService rtp) {
		this.rtp = rtp;
	}

	public void open(Player player) {
		GuiHolder holder = new GuiHolder(GuiHolder.Kind.RTP);
		Inventory inv = Bukkit.createInventory(holder, 27, Component.text(TITLE, NamedTextColor.DARK_AQUA));
		holder.inventory(inv);

		ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			inv.setItem(i, pane);
		}

		inv.setItem(4, button(Material.ENDER_PEARL, "How far away?", NamedTextColor.AQUA,
				"Pick a distance — you'll teleport",
				"randomly about that many blocks away",
				"from where you are standing."));

		inv.setItem(10, distanceButton(Material.LIME_DYE, 500, NamedTextColor.GREEN));
		inv.setItem(11, distanceButton(Material.YELLOW_DYE, 1_000, NamedTextColor.YELLOW));
		inv.setItem(12, distanceButton(Material.ORANGE_DYE, 2_500, NamedTextColor.GOLD));
		inv.setItem(13, distanceButton(Material.RED_DYE, 5_000, NamedTextColor.RED));
		inv.setItem(14, distanceButton(Material.PURPLE_DYE, 10_000, NamedTextColor.LIGHT_PURPLE));
		inv.setItem(15, distanceButton(Material.BLACK_DYE, 25_000, NamedTextColor.DARK_PURPLE));

		long cd = rtp.cooldownRemainingMs(player);
		if (cd > 0 && !player.isOp()) {
			inv.setItem(22, button(Material.CLOCK, "Cooldown", NamedTextColor.GRAY,
					((cd + 999) / 1000) + "s remaining"));
		} else {
			inv.setItem(22, button(Material.BARRIER, "Close", NamedTextColor.RED));
		}

		player.openInventory(inv);
	}

	private static ItemStack distanceButton(Material material, int blocks, NamedTextColor color) {
		return button(material, formatBlocks(blocks), color,
				"Teleport ~" + formatBlocks(blocks).toLowerCase() + " away",
				"Click to RTP");
	}

	private static String formatBlocks(int blocks) {
		if (blocks >= 1000) {
			int k = blocks / 1000;
			return k + ",000 blocks";
		}
		return blocks + " blocks";
	}

	private static ItemStack pane(Material material, String name) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack button(Material material, String name, NamedTextColor color, String... loreLines) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
		List<Component> lore = new ArrayList<>();
		for (String line : loreLines) {
			lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
		}
		meta.lore(lore);
		stack.setItemMeta(meta);
		return stack;
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)
				|| holder.kind() != GuiHolder.Kind.RTP) {
			return;
		}
		event.setCancelled(true);
		if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
			return;
		}

		int slot = event.getRawSlot();
		if (slot == 22) {
			player.closeInventory();
			return;
		}
		Integer radius = OPTIONS.get(slot);
		if (radius == null) {
			return;
		}
		player.closeInventory();
		Bukkit.getScheduler().runTask(ChunkBoomeritsPlugin.get(), () -> rtp.teleport(player, radius));
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder
				&& holder.kind() == GuiHolder.Kind.RTP) {
			event.setCancelled(true);
		}
	}
}
