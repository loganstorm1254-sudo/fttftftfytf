package com.minedoom.screen;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selection source for Doom screens: WorldEdit when present, otherwise built-in axe corners.
 */
public final class SelectionService {

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }
    }

    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc.getBlock().getLocation());
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc.getBlock().getLocation());
    }

    public boolean hasBuiltinSelection(Player player) {
        return pos1.containsKey(player.getUniqueId()) && pos2.containsKey(player.getUniqueId());
    }

    public Bounds requireBounds(Player player) throws Exception {
        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            try {
                return boundsFromWorldEdit(player);
            } catch (IllegalStateException incomplete) {
                if (hasBuiltinSelection(player)) {
                    return boundsFromBuiltin(player);
                }
                throw incomplete;
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // fall through to builtin
            }
        }
        if (!hasBuiltinSelection(player)) {
            throw new IllegalStateException(
                    "No selection. Use WorldEdit //wand, or left/right-click two corners with a wooden axe, then /doom place");
        }
        return boundsFromBuiltin(player);
    }

    private Bounds boundsFromBuiltin(Player player) {
        Location a = pos1.get(player.getUniqueId());
        Location b = pos2.get(player.getUniqueId());
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            throw new IllegalStateException("Invalid selection — both corners must be in the same world.");
        }
        return new Bounds(
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ())
        );
    }

    /**
     * Reflective WorldEdit access keeps WE types out of other classes' constant pools
     * so MineDoom can enable even if WorldEdit failed to load.
     */
    private Bounds boundsFromWorldEdit(Player player) throws Exception {
        Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit");
        Object we = weClass.getMethod("getInstance").invoke(null);
        Object sessionManager = weClass.getMethod("getSessionManager").invoke(we);

        Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object actor = adapter.getMethod("adapt", Player.class).invoke(null, player);

        Method getSession = findGetSession(sessionManager.getClass());
        Object session = getSession.invoke(sessionManager, actor);

        Object selectionWorld = session.getClass().getMethod("getSelectionWorld").invoke(session);
        Object region;
        try {
            region = session.getClass()
                    .getMethod("getSelection", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(session, selectionWorld);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c != null && c.getClass().getSimpleName().contains("IncompleteRegion")) {
                throw new IllegalStateException("Make a WorldEdit selection first (//wand left + right click).");
            }
            throw e;
        }

        Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
        Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
        return new Bounds(coord(min, "x"), coord(min, "y"), coord(min, "z"),
                coord(max, "x"), coord(max, "y"), coord(max, "z"));
    }

    private static Method findGetSession(Class<?> sessionManagerClass) throws NoSuchMethodException {
        for (Method m : sessionManagerClass.getMethods()) {
            if (!m.getName().equals("get") || m.getParameterCount() != 1) {
                continue;
            }
            Class<?> p = m.getParameterTypes()[0];
            if (p.getName().contains("Actor") || p.getName().contains("SessionOwner") || p.getName().contains("Player")) {
                return m;
            }
        }
        throw new NoSuchMethodException("WorldEdit SessionManager#get(Actor) not found");
    }

    private static int coord(Object vec, String name) throws Exception {
        try {
            return ((Number) vec.getClass().getMethod(name).invoke(vec)).intValue();
        } catch (NoSuchMethodException e) {
            String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return ((Number) vec.getClass().getMethod(getter).invoke(vec)).intValue();
        }
    }
}
