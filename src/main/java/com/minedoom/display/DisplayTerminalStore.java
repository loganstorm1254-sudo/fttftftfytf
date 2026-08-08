package com.minedoom.display;

import com.minedoom.MineDoomPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Placed Display Terminals and their linked 16:9 screens. */
public final class DisplayTerminalStore {

    public record Terminal(String world, int x, int y, int z, UUID linkedScreenId) {
        public String key() {
            return world + ":" + x + ":" + y + ":" + z;
        }

        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }

        public Terminal withScreen(UUID screenId) {
            return new Terminal(world, x, y, z, screenId);
        }
    }

    private final MineDoomPlugin plugin;
    private final File storeFile;
    private final ConcurrentHashMap<String, Terminal> terminals = new ConcurrentHashMap<>();

    public DisplayTerminalStore(MineDoomPlugin plugin) {
        this.plugin = plugin;
        this.storeFile = new File(plugin.getDataFolder(), "display-terminals.yml");
    }

    public Collection<Terminal> all() {
        return terminals.values();
    }

    public void put(Terminal t) {
        terminals.put(t.key(), t);
        save();
    }

    public Optional<Terminal> get(Location loc) {
        if (loc.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(terminals.get(
                loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ()));
    }

    public Optional<Terminal> remove(Location loc) {
        if (loc.getWorld() == null) {
            return Optional.empty();
        }
        Terminal removed = terminals.remove(
                loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ());
        if (removed != null) {
            save();
        }
        return Optional.ofNullable(removed);
    }

    public Optional<Terminal> findNearest(Location loc, double maxDist) {
        Terminal best = null;
        double bestD = maxDist * maxDist;
        for (Terminal t : terminals.values()) {
            Location tl = t.location();
            if (tl == null || loc.getWorld() == null || !tl.getWorld().equals(loc.getWorld())) {
                continue;
            }
            double d = tl.distanceSquared(loc);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return Optional.ofNullable(best);
    }

    public void load() {
        terminals.clear();
        if (!storeFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storeFile);
        var section = yaml.getConfigurationSection("terminals");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var c = section.getConfigurationSection(key);
            if (c == null) continue;
            try {
                UUID screen = null;
                String sid = c.getString("screen");
                if (sid != null && !sid.isBlank()) {
                    screen = UUID.fromString(sid);
                }
                Terminal t = new Terminal(
                        c.getString("world"),
                        c.getInt("x"),
                        c.getInt("y"),
                        c.getInt("z"),
                        screen
                );
                terminals.put(t.key(), t);
            } catch (Exception e) {
                plugin.getLogger().warning("Bad display terminal " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + terminals.size() + " display terminal(s)");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (Terminal t : terminals.values()) {
            String p = "terminals." + i;
            yaml.set(p + ".world", t.world());
            yaml.set(p + ".x", t.x());
            yaml.set(p + ".y", t.y());
            yaml.set(p + ".z", t.z());
            if (t.linkedScreenId() != null) {
                yaml.set(p + ".screen", t.linkedScreenId().toString());
            }
            i++;
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(storeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save display terminals: " + e.getMessage());
        }
    }
}
