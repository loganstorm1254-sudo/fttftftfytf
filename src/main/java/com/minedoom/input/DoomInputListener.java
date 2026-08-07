package com.minedoom.input;

import com.minedoom.MineDoomPlugin;
import com.minedoom.doom.DoomEngine;
import com.minedoom.doom.PureDoomNative;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges PC keyboard/mouse (via Minecraft client controls) into PureDOOM.
 *
 * <pre>
 * W/A/S/D     move / strafe
 * Mouse       look (yaw/pitch)
 * Left click  fire
 * Right click use / open
 * Space/Jump  use
 * Sprint      run
 * Hotbar 1-7  weapons
 * F (swap)    automap (TAB)
 * /doom stop  exit
 * </pre>
 */
public final class DoomInputListener implements Listener {

    private final MineDoomPlugin plugin;
    private final DoomEngine engine;
    private final ScreenManager screens;
    private final Map<UUID, PlaySession> sessions = new ConcurrentHashMap<>();
    private BukkitTask inputTask;
    private BukkitTask renderTask;

    public DoomInputListener(MineDoomPlugin plugin, DoomEngine engine, ScreenManager screens) {
        this.plugin = plugin;
        this.engine = engine;
        this.screens = screens;
        startTasks();
    }

    private void startTasks() {
        int interval = Math.max(1, plugin.getConfig().getInt("render-interval-ticks", 1));
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            byte[] frame = engine.getLatestFrame();
            if (frame != null) {
                screens.pushFrameToAll(frame, engine.getWidth(), engine.getHeight());
            }
        }, 1L, interval);

        inputTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollInputs, 1L, 1L);
        engine.setFrameListener(rgb -> screens.pushFrameToAll(rgb, engine.getWidth(), engine.getHeight()));
    }

    public boolean isPlaying(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public Optional<PlaySession> startPlaying(Player player, DoomScreen screen) {
        if (sessions.containsKey(player.getUniqueId())) {
            stopPlaying(player);
        }
        engine.ensureStarted();

        WorldSeat seat = WorldSeat.create(player, screen);
        PlaySession session = new PlaySession(
                player.getUniqueId(),
                screen.getId(),
                seat,
                player.getGameMode(),
                player.getLocation().clone(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );
        sessions.put(player.getUniqueId(), session);

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        seat.mount(player);

        player.sendMessage("§c§lDOOM §7— keyboard & mouse armed · E1M1");
        player.sendMessage("§8WASD move · mouse look · LMB fire · RMB/Jump use · Sprint run · hotbar 1-7 weapons");
        player.sendMessage("§8/doom stop §7to exit · /doom esc §7menu");
        return Optional.of(session);
    }

    public void stopPlaying(Player player) {
        PlaySession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        releaseAllKeys(session);
        session.seat.dismount(player);
        session.seat.remove();
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setAllowFlight(session.previousGameMode == GameMode.CREATIVE
                || session.previousGameMode == GameMode.SPECTATOR);
        player.setFlying(false);
        player.setGameMode(session.previousGameMode);
        if (session.returnLocation != null) {
            player.teleport(session.returnLocation);
        }
        player.sendMessage("§7Left the DOOM screen.");
    }

    public void stopPlayingOnScreen(UUID screenId) {
        for (UUID playerId : new HashSet<>(sessions.keySet())) {
            PlaySession session = sessions.get(playerId);
            if (session != null && session.screenId.equals(screenId)) {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null) {
                    stopPlaying(p);
                } else {
                    sessions.remove(playerId);
                    session.seat.remove();
                }
            }
        }
    }

    public void stopAll() {
        for (UUID id : new HashSet<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                stopPlaying(p);
            } else {
                PlaySession s = sessions.remove(id);
                if (s != null) {
                    s.seat.remove();
                }
            }
        }
        if (inputTask != null) {
            inputTask.cancel();
        }
        if (renderTask != null) {
            renderTask.cancel();
        }
    }

    private void pollInputs() {
        double sens = plugin.getConfig().getDouble("mouse-sensitivity", 8.0);
        boolean invertY = plugin.getConfig().getBoolean("invert-mouse-y", false);

        for (PlaySession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            session.seat.keepSeated(player);

            var input = player.getCurrentInput();
            syncKey(session, PureDoomNative.KEY_W, input.isForward());
            syncKey(session, PureDoomNative.KEY_S, input.isBackward());
            syncKey(session, PureDoomNative.KEY_A, input.isLeft());
            syncKey(session, PureDoomNative.KEY_D, input.isRight());
            syncKey(session, PureDoomNative.KEY_SHIFT, input.isSprint());
            syncKey(session, PureDoomNative.KEY_E, input.isJump());

            Location loc = player.getLocation();
            float yaw = loc.getYaw();
            float pitch = loc.getPitch();
            float dyaw = yaw - session.lastYaw;
            float dpitch = pitch - session.lastPitch;
            if (dyaw > 180) {
                dyaw -= 360;
            }
            if (dyaw < -180) {
                dyaw += 360;
            }
            session.lastYaw = yaw;
            session.lastPitch = pitch;

            int mdx = Math.round(dyaw * (float) sens);
            int mdy = Math.round(dpitch * (float) sens);
            if (invertY) {
                mdy = -mdy;
            }
            if (mdx != 0 || mdy != 0) {
                engine.mouseMove(mdx, mdy);
            }
        }
    }

    private void syncKey(PlaySession session, int key, boolean down) {
        if (down && !session.heldKeys.contains(key)) {
            session.heldKeys.add(key);
            engine.keyDown(key);
        } else if (!down && session.heldKeys.contains(key)) {
            session.heldKeys.remove(key);
            engine.keyUp(key);
        }
    }

    private void releaseAllKeys(PlaySession session) {
        for (int key : session.heldKeys) {
            engine.keyUp(key);
        }
        session.heldKeys.clear();
        if (session.firing) {
            engine.buttonUp(PureDoomNative.BUTTON_LEFT);
            session.firing = false;
        }
        if (session.using) {
            engine.buttonUp(PureDoomNative.BUTTON_RIGHT);
            session.using = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlaySession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (!session.firing) {
                session.firing = true;
                engine.buttonDown(PureDoomNative.BUTTON_LEFT);
                engine.keyDown(PureDoomNative.KEY_CTRL);
                session.heldKeys.add(PureDoomNative.KEY_CTRL);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                PlaySession s = sessions.get(player.getUniqueId());
                if (s != null && s.firing) {
                    s.firing = false;
                    engine.buttonUp(PureDoomNative.BUTTON_LEFT);
                    if (s.heldKeys.remove(PureDoomNative.KEY_CTRL)) {
                        engine.keyUp(PureDoomNative.KEY_CTRL);
                    }
                }
            }, 3L);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            engine.keyDown(PureDoomNative.KEY_E);
            engine.buttonDown(PureDoomNative.BUTTON_RIGHT);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                engine.keyUp(PureDoomNative.KEY_E);
                engine.buttonUp(PureDoomNative.BUTTON_RIGHT);
            }, 3L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        PlaySession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) {
            return;
        }
        if (to.getX() != from.getX() || to.getY() != from.getY() || to.getZ() != from.getZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (!sessions.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        int slot = event.getNewSlot();
        if (slot >= 0 && slot <= 6) {
            int key = PureDoomNative.KEY_1 + slot;
            engine.keyDown(key);
            Bukkit.getScheduler().runTaskLater(plugin, () -> engine.keyUp(key), 2L);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!sessions.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        engine.keyDown(PureDoomNative.KEY_TAB);
        Bukkit.getScheduler().runTaskLater(plugin, () -> engine.keyUp(PureDoomNative.KEY_TAB), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            stopPlaying(event.getPlayer());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && sessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public static final class PlaySession {
        final UUID playerId;
        final UUID screenId;
        final WorldSeat seat;
        final GameMode previousGameMode;
        final Location returnLocation;
        final Set<Integer> heldKeys = ConcurrentHashMap.newKeySet();
        float lastYaw;
        float lastPitch;
        boolean firing;
        boolean using;

        PlaySession(UUID playerId, UUID screenId, WorldSeat seat, GameMode previousGameMode,
                    Location returnLocation, float lastYaw, float lastPitch) {
            this.playerId = playerId;
            this.screenId = screenId;
            this.seat = seat;
            this.previousGameMode = previousGameMode;
            this.returnLocation = returnLocation;
            this.lastYaw = lastYaw;
            this.lastPitch = lastPitch;
        }
    }

    public static final class WorldSeat {
        private final ArmorStand stand;

        private WorldSeat(ArmorStand stand) {
            this.stand = stand;
        }

        static WorldSeat create(Player player, DoomScreen screen) {
            Location seatLoc = screen.getSeatLocation(player.getWorld());
            Location center = screen.getCenter(player.getWorld());
            Vector look = center.toVector().subtract(seatLoc.toVector());
            if (look.lengthSquared() > 0.001) {
                seatLoc.setDirection(look.normalize());
            }

            ArmorStand stand = player.getWorld().spawn(seatLoc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setMarker(true);
                as.setSmall(true);
                as.setBasePlate(false);
                as.setArms(false);
                as.setCollidable(false);
                as.setPersistent(false);
                as.addScoreboardTag("minedoom_seat");
            });
            return new WorldSeat(stand);
        }

        void mount(Player player) {
            stand.addPassenger(player);
        }

        void keepSeated(Player player) {
            if (!stand.getPassengers().contains(player)) {
                stand.addPassenger(player);
            }
        }

        void dismount(Player player) {
            stand.removePassenger(player);
        }

        void remove() {
            if (stand.isValid()) {
                for (Entity p : new java.util.ArrayList<>(stand.getPassengers())) {
                    stand.removePassenger(p);
                }
                stand.remove();
            }
        }
    }
}
