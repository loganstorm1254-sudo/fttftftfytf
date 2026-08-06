package com.chunkboomerits.paper;

import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkBoomeritsPlugin extends JavaPlugin {
	private static ChunkBoomeritsPlugin instance;

	@Override
	public void onEnable() {
		instance = this;
		ChunkBoomeritsItems.init(this);

		ChunkBoomeritsCommand command = new ChunkBoomeritsCommand();
		getCommand("chunkboomerits").setExecutor(command);
		getCommand("chunkboomerits").setTabCompleter(command);
		getCommand("givechunkboomerits").setExecutor(command);
		getCommand("givechunkboomerits").setTabCompleter(command);

		getServer().getPluginManager().registerEvents(new ChunkBoomeritsListener(this), this);
		getServer().getPluginManager().registerEvents(new GiveInterceptListener(), this);
		getLogger().info("Chunk Boomerits enabled — put this jar in plugins/. OP: /chunkboomerits or /give @s chunkboomerits");
	}

	public static ChunkBoomeritsPlugin get() {
		return instance;
	}
}
