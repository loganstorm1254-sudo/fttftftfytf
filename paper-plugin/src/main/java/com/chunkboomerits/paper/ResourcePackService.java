package com.chunkboomerits.paper;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Extracts and optionally pushes the Despacito resource pack to players.
 */
public final class ResourcePackService implements Listener {
	private final ChunkBoomeritsPlugin plugin;
	private final File packFile;
	private String url;
	private byte[] hash;
	private boolean pushOnJoin;

	public ResourcePackService(ChunkBoomeritsPlugin plugin) {
		this.plugin = plugin;
		this.packFile = new File(plugin.getDataFolder(), "ChunkBoomerits-RP.zip");
	}

	public void setup() {
		plugin.saveDefaultConfig();
		FileConfiguration config = plugin.getConfig();
		this.url = config.getString("resource-pack.url", "");
		this.pushOnJoin = config.getBoolean("resource-pack.push-on-join", true);

		extractBundledPack();
		computeHash();

		if (url == null || url.isBlank()) {
			plugin.getLogger().warning("No resource-pack.url set. Players need ChunkBoomerits-RP.zip for Despacito audio.");
			plugin.getLogger().warning("Pack file: " + packFile.getAbsolutePath());
		} else {
			plugin.getLogger().info("Resource pack URL: " + url);
		}
	}

	private void extractBundledPack() {
		try {
			plugin.getDataFolder().mkdirs();
			try (InputStream in = plugin.getResource("ChunkBoomerits-RP.zip")) {
				if (in == null) {
					plugin.getLogger().warning("Bundled ChunkBoomerits-RP.zip missing from plugin jar.");
					return;
				}
				Files.copy(in, packFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception ex) {
			plugin.getLogger().warning("Could not extract resource pack: " + ex.getMessage());
		}
	}

	private void computeHash() {
		try {
			if (!packFile.exists()) {
				return;
			}
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			byte[] data = Files.readAllBytes(packFile.toPath());
			this.hash = sha1.digest(data);
		} catch (Exception ex) {
			plugin.getLogger().warning("Could not hash resource pack: " + ex.getMessage());
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (!pushOnJoin || url == null || url.isBlank() || hash == null) {
			return;
		}
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (player.isOnline()) {
				player.setResourcePack(url, hash, "Chunk Boomerits music disc (Despacito)", true);
			}
		}, 40L);
	}
}
