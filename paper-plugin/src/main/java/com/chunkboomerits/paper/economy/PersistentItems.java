package com.chunkboomerits.paper.economy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Durable ItemStack encode/decode + safe YAML file IO so shop/AH survive plugin jar updates.
 */
public final class PersistentItems {
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private PersistentItems() {
	}

	public static String encode(ItemStack item) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
			out.writeObject(item.clone());
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	public static ItemStack decode(String base64) throws IOException, ClassNotFoundException {
		byte[] data = Base64.getDecoder().decode(base64);
		try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
			Object obj = in.readObject();
			if (!(obj instanceof ItemStack stack)) {
				throw new IOException("Decoded object is not an ItemStack");
			}
			return stack;
		}
	}

	/**
	 * Prefer Base64 blob; fall back to legacy YAML ItemStack for older files.
	 */
	public static ItemStack readItem(ConfigurationSection row, Logger log, String label) {
		if (row == null) {
			return null;
		}
		String b64 = row.getString("item-base64");
		if (b64 != null && !b64.isBlank()) {
			try {
				ItemStack item = decode(b64);
				if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
					return item;
				}
			} catch (Exception ex) {
				log.warning("Failed Base64 item for " + label + ": " + ex.getMessage());
			}
		}
		try {
			ItemStack legacy = row.getItemStack("item");
			if (legacy != null && !legacy.getType().isAir() && legacy.getAmount() > 0) {
				return legacy;
			}
		} catch (Exception ex) {
			log.warning("Failed legacy YAML item for " + label + ": " + ex.getMessage());
		}
		return null;
	}

	public static void writeItem(FileConfiguration data, String path, ItemStack item, Logger log) {
		ItemStack copy = item.clone();
		try {
			data.set(path + ".item-base64", encode(copy));
		} catch (Exception ex) {
			log.warning("Failed to encode item at " + path + ": " + ex.getMessage());
		}
		// Also keep a YAML copy for humans / older loaders
		data.set(path + ".item", copy);
		data.set(path + ".material", copy.getType().name());
		data.set(path + ".amount", copy.getAmount());
	}

	public static void backupIfExists(File file, File dataFolder, Logger log) {
		if (file == null || !file.exists()) {
			return;
		}
		try {
			File backups = new File(dataFolder, "backups");
			backups.mkdirs();
			String name = file.getName().replace(".yml", "") + "-" + BACKUP_TIME.format(LocalDateTime.now()) + ".yml";
			Files.copy(file.toPath(), new File(backups, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
			// Keep folder from growing forever — leave last ~20 backups of this file
			File[] old = backups.listFiles((dir, n) -> n.startsWith(file.getName().replace(".yml", "") + "-") && n.endsWith(".yml"));
			if (old != null && old.length > 20) {
				java.util.Arrays.sort(old, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
				for (int i = 0; i < old.length - 20; i++) {
					old[i].delete();
				}
			}
		} catch (Exception ex) {
			log.warning("Could not backup " + file.getName() + ": " + ex.getMessage());
		}
	}

	public static void saveAtomically(FileConfiguration data, File file, Logger log) {
		try {
			file.getParentFile().mkdirs();
			File temp = new File(file.getParentFile(), file.getName() + ".tmp");
			data.save(temp);
			Path target = file.toPath();
			Path source = temp.toPath();
			try {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			log.severe("Could not save " + file.getName() + ": " + ex.getMessage());
		}
	}

	public static ItemStack simpleFallback(ConfigurationSection row) {
		if (row == null) {
			return null;
		}
		String matName = row.getString("material");
		if (matName == null) {
			return null;
		}
		Material mat = Material.matchMaterial(matName);
		if (mat == null || !mat.isItem()) {
			return null;
		}
		int amount = Math.max(1, row.getInt("amount", 1));
		return new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
	}
}
