package com.chunkboomerits.paper;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import com.chunkboomerits.paper.economy.EconomyCommands;
import com.chunkboomerits.paper.economy.EconomyListener;
import com.chunkboomerits.paper.economy.EconomyScoreboard;
import com.chunkboomerits.paper.economy.EconomyService;
import com.chunkboomerits.paper.economy.ScoreboardEditorGui;
import com.chunkboomerits.paper.economy.ScoreboardShovelListener;

public final class ChunkBoomeritsPlugin extends JavaPlugin {
	private static ChunkBoomeritsPlugin instance;
	private EconomyService economy;
	private EconomyScoreboard economyScoreboard;

	@Override
	public void onEnable() {
		instance = this;
		saveDefaultConfig();
		OpItems.init(this);

		bind("chunkboomerits", OpGiveCommand.boomerits());
		bind("givechunkboomerits", OpGiveCommand.boomerits());
		bind("kicksword", OpGiveCommand.kickSword());
		bind("killhammer", OpGiveCommand.killHammer());
		bind("invhelmet", OpGiveCommand.invincibleHelmet());
		bind("sbshovel", OpGiveCommand.scoreboardShovel());
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

		economy = new EconomyService(this);
		economy.load();
		economyScoreboard = new EconomyScoreboard(this, economy);
		economyScoreboard.load();

		EconomyCommands ecoCmds = new EconomyCommands(economy, economyScoreboard);
		bindEco("bal", ecoCmds);
		bindEco("balance", ecoCmds);
		bindEco("pay", ecoCmds);
		bindEco("eco", ecoCmds);
		bindEco("baltop", ecoCmds);

		ScoreboardEditorGui editorGui = new ScoreboardEditorGui(economyScoreboard);
		getServer().getPluginManager().registerEvents(editorGui, this);
		getServer().getPluginManager().registerEvents(new ScoreboardShovelListener(editorGui), this);
		getServer().getPluginManager().registerEvents(new EconomyListener(economy, economyScoreboard), this);

		getServer().getPluginManager().registerEvents(new OpToolsListener(), this);
		getServer().getPluginManager().registerEvents(new InvincibleHelmetListener(), this);
		getServer().getPluginManager().registerEvents(new GiveInterceptListener(), this);
		getServer().getPluginManager().registerEvents(new MusicDiscListener(this), this);

		ResourcePackService packs = new ResourcePackService(this);
		packs.setup();
		getServer().getPluginManager().registerEvents(packs, this);

		getLogger().info("Economy ready: /bal /pay /eco /baltop — OP shovel: /sbshovel");
	}

	@Override
	public void onDisable() {
		if (economy != null) {
			economy.save();
		}
		if (economyScoreboard != null) {
			economyScoreboard.shutdown();
		}
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

	private void bindEco(String name, EconomyCommands commands) {
		PluginCommand pluginCommand = getCommand(name);
		if (pluginCommand == null) {
			getLogger().severe("Command /" + name + " is missing from plugin.yml");
			return;
		}
		pluginCommand.setExecutor(commands);
		pluginCommand.setTabCompleter(commands);
		getLogger().info("Registered /" + name);
	}

	public EconomyService economy() {
		return economy;
	}

	public EconomyScoreboard economyScoreboard() {
		return economyScoreboard;
	}

	public static ChunkBoomeritsPlugin get() {
		return instance;
	}
}
