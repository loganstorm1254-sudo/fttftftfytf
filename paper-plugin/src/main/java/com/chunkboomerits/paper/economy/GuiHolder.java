package com.chunkboomerits.paper.economy;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks plugin-owned GUIs so click handlers don't rely on title text alone.
 */
public final class GuiHolder implements InventoryHolder {
	public enum Kind { AUCTION, SHOP, SHOP_ADMIN, ADMIN_HUB, SCOREBOARD, SELL_PRICES, SELL, RTP }

	private final Kind kind;
	private Inventory inventory;

	public GuiHolder(Kind kind) {
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public void inventory(Inventory inventory) {
		this.inventory = inventory;
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}
}
