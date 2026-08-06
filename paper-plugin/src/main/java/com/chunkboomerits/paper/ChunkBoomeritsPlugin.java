package com.chunkboomerits.paper;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkBoomeritsPlugin extends JavaPlugin {
	private static ChunkBoomeritsPlugin instance;

	@Override
	public void onEnable() {
		instance = this;
		OpItems.init(this);

		bind("chunkboomerits", OpGiveCommand.boomerits());
		bind("givechunkboomerits", OpGiveCommand.boomerits());
		bind("kicksword", OpGiveCommand.kickSword());
		bind("killhammer", OpGiveCommand.killHammer());
		bind("invhelmet", OpGiveCommand.invincibleHelmet());
		bind("despacito", OpGiveCommand.despacito());
		bind("moskau", OpGiveCommand.moskau());
		bind("kimjonggoon", OpGiveCommand.kimJongGoon());

		PluginCommand cbgive = getCommand("cbgive");
		if (cbgive != null) {
			cbgive.setExecutor(OpGiveCommand.cbgive());
			cbgive.setTabCompleter(OpGiveCommand.cbgiveTab());
		} else {
			getLogger().severe("Command /cbgive missing from plugin.yml");
		}

		getServer().getPluginManager().registerEvents(new OpToolsListener(), this);
		getServer().getPluginManager().registerEvents(new InvincibleHelmetListener(), this);
		getServer().getPluginManager().registerEvents(new GiveInterceptListener(), this);
		getServer().getPluginManager().registerEvents(new MusicDiscListener(this), this);

		ResourcePackService packs = new ResourcePackService(this);
		packs.setup();
		getServer().getPluginManager().registerEvents(packs, this);

		getLogger().info("Ready. Commands: /killhammer /invhelmet /cbgive killhammer|invhelmet /kicksword /despacito ...");
		getLogger().info("Materials: MACE=" + org.bukkit.Material.MACE + " COPPER_HELMET=" + org.bukkit.Material.matchMaterial("COPPER_HELMET"));
	}

	private void bind(String name, OpGiveCommand command) {
		PluginCommand pluginCommand = getCommand(name);
		if (pluginCommand == null) {
			getLogger().severe("Command /" + name + " is missing from plugin.yml — it will not work!");
			return;
		}
		pluginCommand.setExecutor(command);
		pluginCommand.setTabCompleter(command);
		getLogger().info("Registered /" + name);
	}

	public static ChunkBoomeritsPlugin get() {
		return instance;
	}
}
