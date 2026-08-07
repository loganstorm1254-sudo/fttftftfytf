package com.minedoom;

import com.minedoom.command.DoomCommand;
import com.minedoom.command.GoogleCommand;
import com.minedoom.doom.DoomEngine;
import com.minedoom.google.GoogleBrowser;
import com.minedoom.input.DoomInputListener;
import com.minedoom.input.WandListener;
import com.minedoom.screen.ControllerItems;
import com.minedoom.screen.ControllerListener;
import com.minedoom.screen.ControllerStore;
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
    private ControllerItems controllerItems;
    private ControllerStore controllerStore;
    private ControllerListener controllerListener;
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
        controllerItems = new ControllerItems(this);
        controllerStore = new ControllerStore(this);
        controllerStore.load();
        controllerListener = new ControllerListener(this, screenManager, controllerStore, controllerItems);
        getServer().getPluginManager().registerEvents(controllerListener, this);
        getServer().getPluginManager().registerEvents(new WandListener(selectionService), this);

        if (doomAvailable) {
            inputListener = new DoomInputListener(this, engine, screenManager);
            getServer().getPluginManager().registerEvents(inputListener, this);

            DoomCommand cmd = new DoomCommand(this, engine, screenManager, inputListener, controllerListener);
            var doom = getCommand("doom");
            if (doom != null) {
                doom.setExecutor(cmd);
                doom.setTabCompleter(cmd);
            }
        }

        GoogleCommand googleCmd = new GoogleCommand(this, screenManager, googleBrowser, controllerListener);
        var google = getCommand("google");
        if (google != null) {
            google.setExecutor(googleCmd);
            google.setTabCompleter(googleCmd);
        }

        getLogger().info("MineDoom enabled — "
                + (doomAvailable ? "PureDOOM ready (/doom)" : "DOOM unavailable")
                + " · Google screens via /google · switches via /doom give");
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
        if (controllerStore != null) {
            controllerStore.save();
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

    public DoomInputListener getInputListener() {
        return inputListener;
    }

    public GoogleBrowser getGoogleBrowser() {
        return googleBrowser;
    }

    public ControllerItems getControllerItems() {
        return controllerItems;
    }

    public ControllerStore getControllerStore() {
        return controllerStore;
    }

    public boolean isDoomAvailable() {
        return doomAvailable;
    }
}
