package com.minedoom.screen;

import com.minedoom.MineDoomPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placed screen-switch blocks that levers can power.
 */
public final class ControllerStore {

    public record Controller(String world, int x, int y, int z, ScreenKind kind) {
        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }

        public String key() {
            return world + ":" + x + ":" + y + ":" + z;
        }
    }

    private final MineDoomPlugin plugin;
    private final File storeFile;
    private final ConcurrentHashMap<String, Controller> controllers = new ConcurrentHashMap<>();

    public ControllerStore(MineDoomPlugin plugin) {
        this.plugin = plugin;
        this.storeFile = new File(plugin.getDataFolder(), "controllers.yml");
    }

    public Collection<Controller> all() {
        return controllers.values();
    }

    public void put(Controller controller) {
        controllers.put(controller.key(), controller);
        save();
    }

    public Optional<Controller> get(Location loc) {
        if (loc.getWorld() == null) {
            return Optional.empty();
        }
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        return Optional.ofNullable(controllers.get(key));
    }

    public Optional<Controller> remove(Location loc) {
        if (loc.getWorld() == null) {
            return Optional.empty();
        }
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        Controller removed = controllers.remove(key);
        if (removed != null) {
            save();
        }
        return Optional.ofNullable(removed);
    }

    public void load() {
        controllers.clear();
        if (!storeFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storeFile);
        var section = yaml.getConfigurationSection("controllers");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var child = section.getConfigurationSection(key);
            if (child == null) continue;
            try {
                Controller c = new Controller(
                        child.getString("world"),
                        child.getInt("x"),
                        child.getInt("y"),
                        child.getInt("z"),
                        ScreenKind.fromString(child.getString("kind", "DOOM"))
                );
                controllers.put(c.key(), c);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load controller " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + controllers.size() + " screen switch block(s)");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (Controller c : controllers.values()) {
            String path = "controllers." + i;
            yaml.set(path + ".world", c.world());
            yaml.set(path + ".x", c.x());
            yaml.set(path + ".y", c.y());
            yaml.set(path + ".z", c.z());
            yaml.set(path + ".kind", c.kind().name());
            i++;
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(storeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save controllers: " + e.getMessage());
        }
    }
}
