package com.minedoom;

import com.minedoom.command.DoomCommand;
import com.minedoom.doom.DoomEngine;
import com.minedoom.input.DoomInputListener;
import com.minedoom.screen.ScreenManager;
import com.minedoom.util.NativeLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class MineDoomPlugin extends JavaPlugin {

    private DoomEngine engine;
    private ScreenManager screenManager;
    private DoomInputListener inputListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Path data = getDataFolder().toPath();
        try {
            NativeLoader.extractAndLoad(this, data);
        } catch (Exception e) {
            getLogger().severe("Failed to load PureDOOM native library: " + e.getMessage());
            getLogger().severe("MineDoom requires a linux-x86_64 server with glibc.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Path iwad = NativeLoader.ensureIwad(this, data);
        engine = new DoomEngine(this, iwad);
        screenManager = new ScreenManager(this);
        screenManager.load();

        inputListener = new DoomInputListener(this, engine, screenManager);
        getServer().getPluginManager().registerEvents(inputListener, this);

        DoomCommand cmd = new DoomCommand(this, engine, screenManager, inputListener);
        var doom = getCommand("doom");
        if (doom != null) {
            doom.setExecutor(cmd);
            doom.setTabCompleter(cmd);
        }

        getLogger().info("MineDoom enabled — PureDOOM ready. Use /doom help");
    }

    @Override
    public void onDisable() {
        if (inputListener != null) {
            inputListener.stopAll();
        }
        if (screenManager != null) {
            screenManager.save();
            screenManager.shutdown();
        }
        if (engine != null) {
            engine.shutdown();
        }
    }

    public DoomEngine getEngine() {
        return engine;
    }

    public ScreenManager getScreenManager() {
        return screenManager;
    }
}
