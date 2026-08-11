package com.chunkboomerits.paper.economy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * Player buy-orders: escrow money, others deliver items for payment.
 * Survives jar updates via Base64 + backups.
 */
public final class OrderService {
	public static final class Order {
		private final int id;
		private final UUID buyer;
		private final String buyerName;
		private final Material material;
		private final ItemStack display;
		private final int amountWanted;
		private int amountFilled;
		private final double pricePer;
		final List<ItemStack> pendingClaims;

		public Order(int id, UUID buyer, String buyerName, Material material, ItemStack display,
				int amountWanted, int amountFilled, double pricePer, List<ItemStack> pendingClaims) {
			this.id = id;
			this.buyer = buyer;
			this.buyerName = buyerName;
			this.material = material;
			this.display = display.clone();
			this.display.setAmount(1);
			this.amountWanted = amountWanted;
			this.amountFilled = amountFilled;
			this.pricePer = pricePer;
			this.pendingClaims = new ArrayList<>();
			if (pendingClaims != null) {
				for (ItemStack stack : pendingClaims) {
					if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
						this.pendingClaims.add(stack.clone());
					}
				}
			}
		}

		public int id() {
			return id;
		}

		public UUID buyer() {
			return buyer;
		}

		public String buyerName() {
			return buyerName;
		}

		public Material material() {
			return material;
		}

		public ItemStack displayCopy() {
			return display.clone();
		}

		public int amountWanted() {
			return amountWanted;
		}

		public int amountFilled() {
			return amountFilled;
		}

		public int amountRemaining() {
			return Math.max(0, amountWanted - amountFilled);
		}

		public double pricePer() {
			return pricePer;
		}

		public boolean complete() {
			return amountRemaining() <= 0;
		}

		public int pendingClaimItems() {
			int n = 0;
			for (ItemStack stack : pendingClaims) {
				n += stack.getAmount();
			}
			return n;
		}

		public List<ItemStack> pendingClaimsCopy() {
			List<ItemStack> out = new ArrayList<>();
			for (ItemStack stack : pendingClaims) {
				out.add(stack.clone());
			}
			return out;
		}

		public double remainingEscrow() {
			return round(amountRemaining() * pricePer);
		}
	}

	public record FulfillResult(int delivered, double paid) {
	}

	private final ChunkBoomeritsPlugin plugin;
	private final File file;
	private final Map<Integer, Order> orders = new LinkedHashMap<>();
	private int nextId = 1;

	public OrderService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "orders.yml");
	}

	public synchronized void load() {
		orders.clear();
		nextId = 1;
		if (!file.exists()) {
			plugin.getLogger().info("No orders.yml yet (fresh install).");
			return;
		}
		PersistentItems.backupIfExists(file, plugin.getDataFolder(), plugin.getLogger());
		FileConfiguration data = YamlConfiguration.loadConfiguration(file);
		nextId = Math.max(1, data.getInt("next-id", 1));
		ConfigurationSection section = data.getConfigurationSection("orders");
		if (section == null) {
			plugin.getLogger().info("orders.yml loaded (0 orders).");
			return;
		}
		int loaded = 0;
		int skipped = 0;
		for (String key : section.getKeys(false)) {
			ConfigurationSection row = section.getConfigurationSection(key);
			if (row == null) {
				skipped++;
				continue;
			}
			try {
				int id = Integer.parseInt(key);
				UUID buyer = UUID.fromString(row.getString("buyer"));
				String buyerName = row.getString("buyer-name", "Unknown");
				Material material = Material.matchMaterial(row.getString("material", "STONE"));
				if (material == null || !material.isItem()) {
					skipped++;
					continue;
				}
				ItemStack display = PersistentItems.readItem(row, plugin.getLogger(), "order#" + key);
				if (display == null) {
					display = new ItemStack(material);
				}
				display.setAmount(1);
				int wanted = row.getInt("wanted");
				int filled = row.getInt("filled");
				double pricePer = row.getDouble("price-per");
				List<ItemStack> pending = new ArrayList<>();
				ConfigurationSection claims = row.getConfigurationSection("pending-claims");
				if (claims != null) {
					for (String ck : claims.getKeys(false)) {
						ConfigurationSection crow = claims.getConfigurationSection(ck);
						if (crow == null) {
							continue;
						}
						ItemStack stack = PersistentItems.readItem(crow, plugin.getLogger(), "order-claim#" + key + "." + ck);
						if (stack != null) {
							pending.add(stack);
						}
					}
				}
				if (wanted <= 0 || pricePer <= 0) {
					skipped++;
					continue;
				}
				orders.put(id, new Order(id, buyer, buyerName, material, display, wanted, filled, pricePer, pending));
				nextId = Math.max(nextId, id + 1);
				loaded++;
			} catch (Exception ex) {
				skipped++;
				plugin.getLogger().warning("Bad order " + key + ": " + ex.getMessage());
			}
		}
		plugin.getLogger().info("orders.yml loaded: " + loaded + " orders"
				+ (skipped > 0 ? " (" + skipped + " skipped)" : "") + ".");
		if (loaded > 0) {
			save();
		}
	}

	public synchronized void save() {
		FileConfiguration data = new YamlConfiguration();
		data.set("version", 1);
		data.set("next-id", nextId);
		for (Order order : orders.values()) {
			String path = "orders." + order.id();
			data.set(path + ".buyer", order.buyer().toString());
			data.set(path + ".buyer-name", order.buyerName());
			data.set(path + ".material", order.material().name());
			data.set(path + ".wanted", order.amountWanted());
			data.set(path + ".filled", order.amountFilled());
			data.set(path + ".price-per", order.pricePer());
			try {
				data.set(path + ".item-base64", PersistentItems.encode(order.displayCopy()));
			} catch (Exception ex) {
				plugin.getLogger().warning("Could not encode order #" + order.id() + " display: " + ex.getMessage());
			}
			data.set(path + ".material-name", order.material().name());
			int ci = 0;
			for (ItemStack stack : order.pendingClaims) {
				String cpath = path + ".pending-claims." + ci;
				try {
					data.set(cpath + ".item-base64", PersistentItems.encode(stack));
				} catch (Exception ex) {
					plugin.getLogger().warning("Could not encode claim for order #" + order.id() + ": " + ex.getMessage());
				}
				data.set(cpath + ".material-name", stack.getType().name());
				data.set(cpath + ".amount", stack.getAmount());
				ci++;
			}
		}
		try {
			PersistentItems.saveAtomically(data, file, plugin.getLogger());
		} catch (Exception ex) {
			plugin.getLogger().warning("Could not save orders.yml: " + ex.getMessage());
		}
	}

	public synchronized Order create(UUID buyer, String buyerName, ItemStack held, int amountWanted, double pricePer) {
		if (held == null || held.getType().isAir() || amountWanted <= 0 || pricePer <= 0) {
			return null;
		}
		Material material = held.getType();
		ItemStack display = held.clone();
		display.setAmount(1);
		int id = nextId++;
		Order order = new Order(id, buyer, buyerName, material, display, amountWanted, 0, round(pricePer), List.of());
		orders.put(id, order);
		save();
		return order;
	}

	public synchronized Order get(int id) {
		return orders.get(id);
	}

	public synchronized List<Order> allOpen() {
		List<Order> out = new ArrayList<>();
		for (Order order : orders.values()) {
			if (!order.complete()) {
				out.add(order);
			}
		}
		return Collections.unmodifiableList(out);
	}

	public synchronized List<Order> byBuyer(UUID buyer) {
		List<Order> out = new ArrayList<>();
		for (Order order : orders.values()) {
			if (order.buyer().equals(buyer)) {
				out.add(order);
			}
		}
		return Collections.unmodifiableList(out);
	}

	public synchronized int size() {
		return orders.size();
	}

	/**
	 * Deliver up to {@code maxAmount} matching items from {@code stacks} into the order.
	 * Caller must remove items from inventory and pay the fulfiller.
	 */
	public synchronized FulfillResult fulfill(int orderId, List<ItemStack> deliveredStacks) {
		Order order = orders.get(orderId);
		if (order == null || order.complete() || deliveredStacks == null || deliveredStacks.isEmpty()) {
			return new FulfillResult(0, 0);
		}
		int remaining = order.amountRemaining();
		int delivered = 0;
		for (ItemStack stack : deliveredStacks) {
			if (stack == null || stack.getType() != order.material() || stack.getAmount() <= 0) {
				continue;
			}
			int take = Math.min(remaining - delivered, stack.getAmount());
			if (take <= 0) {
				break;
			}
			ItemStack claim = stack.clone();
			claim.setAmount(take);
			order.pendingClaims.add(claim);
			delivered += take;
		}
		if (delivered <= 0) {
			return new FulfillResult(0, 0);
		}
		order.amountFilled += delivered;
		double paid = round(delivered * order.pricePer());
		save();
		return new FulfillResult(delivered, paid);
	}

	public synchronized List<ItemStack> claimPending(int orderId, UUID buyer) {
		Order order = orders.get(orderId);
		if (order == null || !order.buyer().equals(buyer) || order.pendingClaims.isEmpty()) {
			return List.of();
		}
		List<ItemStack> out = order.pendingClaimsCopy();
		order.pendingClaims.clear();
		if (order.complete() && order.pendingClaims.isEmpty()) {
			orders.remove(orderId);
		}
		save();
		return out;
	}

	public synchronized List<ItemStack> claimAllPending(UUID buyer) {
		List<ItemStack> out = new ArrayList<>();
		List<Integer> remove = new ArrayList<>();
		for (Order order : orders.values()) {
			if (!order.buyer().equals(buyer) || order.pendingClaims.isEmpty()) {
				continue;
			}
			out.addAll(order.pendingClaimsCopy());
			order.pendingClaims.clear();
			if (order.complete()) {
				remove.add(order.id());
			}
		}
		for (int id : remove) {
			orders.remove(id);
		}
		if (!out.isEmpty()) {
			save();
		}
		return out;
	}

	/**
	 * Cancel order: returns remaining escrow amount to refund. Pending claims returned to buyer.
	 */
	public synchronized CancelResult cancel(int orderId, UUID buyer) {
		Order order = orders.get(orderId);
		if (order == null || !order.buyer().equals(buyer)) {
			return null;
		}
		double refund = order.remainingEscrow();
		List<ItemStack> claims = order.pendingClaimsCopy();
		orders.remove(orderId);
		save();
		return new CancelResult(refund, claims, order.amountFilled());
	}

	public record CancelResult(double refund, List<ItemStack> pendingClaims, int alreadyFilled) {
	}

	private static double round(double amount) {
		return Math.round(amount * 100.0) / 100.0;
	}
}
