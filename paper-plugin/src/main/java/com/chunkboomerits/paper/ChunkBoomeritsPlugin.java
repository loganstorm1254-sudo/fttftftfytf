package com.chunkboomerits.paper;

import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkBoomeritsPlugin extends JavaPlugin {
	private static ChunkBoomeritsPlugin instance;

	@Override
	public void onEnable() {
		instance = this;
		OpItems.init(this);

		OpGiveCommand boomerits = OpGiveCommand.boomerits();
		getCommand("chunkboomerits").setExecutor(boomerits);
		getCommand("chunkboomerits").setTabCompleter(boomerits);
		getCommand("givechunkboomerits").setExecutor(boomerits);
		getCommand("givechunkboomerits").setTabCompleter(boomerits);

		OpGiveCommand kickSword = OpGiveCommand.kickSword();
		getCommand("kicksword").setExecutor(kickSword);
		getCommand("kicksword").setTabCompleter(kickSword);

		OpGiveCommand killHammer = OpGiveCommand.killHammer();
		getCommand("killhammer").setExecutor(killHammer);
		getCommand("killhammer").setTabCompleter(killHammer);

		OpGiveCommand invHelmet = OpGiveCommand.invincibleHelmet();
		getCommand("invhelmet").setExecutor(invHelmet);
		getCommand("invhelmet").setTabCompleter(invHelmet);

		OpGiveCommand despacito = OpGiveCommand.despacito();
		getCommand("despacito").setExecutor(despacito);
		getCommand("despacito").setTabCompleter(despacito);

		OpGiveCommand moskau = OpGiveCommand.moskau();
		getCommand("moskau").setExecutor(moskau);
		getCommand("moskau").setTabCompleter(moskau);

		OpGiveCommand kimJongGoon = OpGiveCommand.kimJongGoon();
		getCommand("kimjonggoon").setExecutor(kimJongGoon);
		getCommand("kimjonggoon").setTabCompleter(kimJongGoon);

		getServer().getPluginManager().registerEvents(new OpToolsListener(), this);
		getServer().getPluginManager().registerEvents(new InvincibleHelmetListener(), this);
		getServer().getPluginManager().registerEvents(new GiveInterceptListener(), this);
		getServer().getPluginManager().registerEvents(new MusicDiscListener(this), this);

		ResourcePackService packs = new ResourcePackService(this);
		packs.setup();
		getServer().getPluginManager().registerEvents(packs, this);

		getLogger().info("Ready: /chunkboomerits, /kicksword, /killhammer, /invhelmet, /despacito, /moskau, /kimjonggoon");
	}

	public static ChunkBoomeritsPlugin get() {
		return instance;
	}
}
