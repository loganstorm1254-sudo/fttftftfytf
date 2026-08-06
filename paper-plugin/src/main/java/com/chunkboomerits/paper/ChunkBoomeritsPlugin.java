package com.chunkboomerits.paper;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import com.chunkboomerits.paper.economy.AdminHubGui;
import com.chunkboomerits.paper.economy.AuctionCommands;
import com.chunkboomerits.paper.economy.AuctionGui;
import com.chunkboomerits.paper.economy.AuctionService;
import com.chunkboomerits.paper.economy.EconomyCommands;
import com.chunkboomerits.paper.economy.EconomyListener;
import com.chunkboomerits.paper.economy.EconomyScoreboard;
import com.chunkboomerits.paper.economy.EconomyService;
import com.chunkboomerits.paper.economy.MarketCommandIntercept;
import com.chunkboomerits.paper.economy.ScoreboardEditorGui;
import com.chunkboomerits.paper.economy.ScoreboardShovelListener;
import com.chunkboomerits.paper.economy.ShopAddCommand;
import com.chunkboomerits.paper.economy.ShopAdminGui;
import com.chunkboomerits.paper.economy.ShopCommands;
import com.chunkboomerits.paper.economy.ShopGui;
import com.chunkboomerits.paper.economy.ShopService;

public final class ChunkBoomeritsPlugin extends JavaPlugin {
	private static ChunkBoomeritsPlugin instance;
	private EconomyService economy;
	private EconomyScoreboard economyScoreboard;
	private AuctionService auctions;
	private ShopService shop;
	private ShopGui shopGui;

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

		auctions = new AuctionService(this);
		auctions.load();
		shop = new ShopService(this);
		shop.load();

		EconomyCommands ecoCmds = new EconomyCommands(economy, economyScoreboard);
		bindEco("bal", ecoCmds);
		bindEco("balance", ecoCmds);
		bindEco("pay", ecoCmds);
		bindEco("eco", ecoCmds);
		bindEco("baltop", ecoCmds);

		AuctionGui auctionGui = new AuctionGui(auctions, economy, economyScoreboard);
		AuctionCommands auctionCmds = new AuctionCommands(auctions, auctionGui, economy);
		bindMarket("ah", auctionCmds);
		bindMarket("cbah", auctionCmds);

		shopGui = new ShopGui(shop, economy, economyScoreboard);
		ShopCommands shopCmds = new ShopCommands(shopGui);
		PluginCommand shopCmd = getCommand("shop");
		if (shopCmd != null) {
			shopCmd.setExecutor(shopCmds);
			getLogger().info("Registered /shop");
		} else {
			getLogger().severe("Command /shop missing from plugin.yml");
		}
		PluginCommand cbshop = getCommand("cbshop");
		if (cbshop != null) {
			cbshop.setExecutor(shopCmds);
			getLogger().info("Registered /cbshop");
		}

		ShopAddCommand shopAdd = new ShopAddCommand();
		PluginCommand shopadd = getCommand("shopadd");
		if (shopadd != null) {
			shopadd.setExecutor(shopAdd);
			getLogger().info("Registered /shopadd");
		}

		ScoreboardEditorGui editorGui = new ScoreboardEditorGui(economyScoreboard);
		ShopAdminGui shopAdminGui = new ShopAdminGui(shop, economy);
		AdminHubGui hub = new AdminHubGui(editorGui, shopAdminGui);

		getServer().getPluginManager().registerEvents(editorGui, this);
		getServer().getPluginManager().registerEvents(shopAdminGui, this);
		getServer().getPluginManager().registerEvents(hub, this);
		getServer().getPluginManager().registerEvents(auctionGui, this);
		getServer().getPluginManager().registerEvents(shopGui, this);
		getServer().getPluginManager().registerEvents(new ScoreboardShovelListener(hub), this);
		getServer().getPluginManager().registerEvents(new EconomyListener(economy, economyScoreboard), this);
		getServer().getPluginManager().registerEvents(new MarketCommandIntercept(auctionCmds, shopGui), this);

		getServer().getPluginManager().registerEvents(new OpToolsListener(), this);
		getServer().getPluginManager().registerEvents(new InvincibleHelmetListener(), this);
		getServer().getPluginManager().registerEvents(new GiveInterceptListener(), this);
		getServer().getPluginManager().registerEvents(new MusicDiscListener(this), this);

		ResourcePackService packs = new ResourcePackService(this);
		packs.setup();
		getServer().getPluginManager().registerEvents(packs, this);

		getLogger().info("Market ready: /ah /cbah /shop /cbshop /shopadd — OP shovel: /sbshovel");
	}

	@Override
	public void onDisable() {
		if (economy != null) {
			economy.save();
		}
		if (auctions != null) {
			auctions.save();
		}
		if (shop != null) {
			shop.save();
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

	private void bindMarket(String name, AuctionCommands commands) {
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

	public ShopService shop() {
		return shop;
	}

	public ShopGui shopGui() {
		return shopGui;
	}

	public static ChunkBoomeritsPlugin get() {
		return instance;
	}
}
