package com.minedoom;

import com.minedoom.command.DoomCommand;
import com.minedoom.command.GoogleCommand;
import com.minedoom.doom.DoomEngine;
import com.minedoom.google.GoogleBrowser;
import com.minedoom.input.DoomInputListener;
import com.minedoom.input.WandListener;
import com.minedoom.screen.ScreenManager;
import com.minedoom.screen.SelectionService;
import com.minedoom.util.NativeLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class MineDoomPlugin extends JavaPlugin {

    private DoomEngine engine;
    private ScreenManager screenManager;
    private DoomInputListener inputListener;
    private GoogleBrowser googleBrowser;
    private boolean doomAvailable;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Path data = getDataFolder().toPath();
        doomAvailable = false;
        try {
            NativeLoader.extractAndLoad(this, data);
            Path iwad = NativeLoader.ensureIwad(this, data);
            engine = new DoomEngine(this, iwad);
            doomAvailable = true;
        } catch (Exception e) {
            getLogger().severe("Failed to load PureDOOM native library: " + e.getMessage());
            getLogger().warning("DOOM disabled — Google screens still available via /google");
            engine = null;
        }

        SelectionService selectionService = new SelectionService();
        screenManager = new ScreenManager(this, selectionService);
        screenManager.load();

        googleBrowser = new GoogleBrowser(this);

        getServer().getPluginManager().registerEvents(new WandListener(selectionService), this);

        if (doomAvailable) {
            inputListener = new DoomInputListener(this, engine, screenManager);
            getServer().getPluginManager().registerEvents(inputListener, this);

            DoomCommand cmd = new DoomCommand(this, engine, screenManager, inputListener);
            var doom = getCommand("doom");
            if (doom != null) {
                doom.setExecutor(cmd);
                doom.setTabCompleter(cmd);
            }
        }

        GoogleCommand googleCmd = new GoogleCommand(this, screenManager, googleBrowser);
        var google = getCommand("google");
        if (google != null) {
            google.setExecutor(googleCmd);
            google.setTabCompleter(googleCmd);
        }

        getLogger().info("MineDoom enabled — "
                + (doomAvailable ? "PureDOOM ready (/doom)" : "DOOM unavailable")
                + " · Google screens via /google");
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

    public GoogleBrowser getGoogleBrowser() {
        return googleBrowser;
    }

    public boolean isDoomAvailable() {
        return doomAvailable;
    }
}
