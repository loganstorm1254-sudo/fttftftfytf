package com.minedoom.video;

import com.minedoom.MineDoomPlugin;
import com.minedoom.google.GoogleBrowser;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays real video onto a Google map screen (ffmpeg RGB frames) with synced OGG audio
 * via a temporary resource pack.
 */
public final class VideoPlayer implements Listener {

    private final MineDoomPlugin plugin;
    private final ScreenManager screens;
    private final FfmpegLocator ffmpeg;
    private final AudioPackService packs;
    private final GoogleBrowser browser;

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile Session session;
    private final Map<UUID, Boolean> packReady = new ConcurrentHashMap<>();

    public VideoPlayer(
            MineDoomPlugin plugin,
            ScreenManager screens,
            FfmpegLocator ffmpeg,
            AudioPackService packs,
            GoogleBrowser browser
    ) {
        this.plugin = plugin;
        this.screens = screens;
        this.ffmpeg = ffmpeg;
        this.packs = packs;
        this.browser = browser;
    }

    public boolean isPlaying() {
        return busy.get();
    }

    public void stop() {
        Session s = session;
        session = null;
        busy.set(false);
        if (s != null) {
            s.stop();
        }
        packReady.clear();
    }

    public void play(Player starter, DoomScreen screen, String rawUrl) {
        if (!busy.compareAndSet(false, true)) {
            starter.sendMessage("§cAlready playing a video. §7/google stop first.");
            return;
        }
        String url;
        try {
            url = browser.normalizeUrl(rawUrl);
        } catch (Exception e) {
            busy.set(false);
            starter.sendMessage("§cBad URL: §7" + e.getMessage());
            return;
        }

        starter.sendMessage("§ePreparing video… §7(download + audio pack, may take a bit)");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ffmpeg.available()) {
                    throw new IllegalStateException("ffmpeg unavailable");
                }
                Path work = plugin.getDataFolder().toPath().resolve("video");
                Files.createDirectories(work);
                String id = "v" + Integer.toHexString(url.hashCode() & 0x7fffffff);

                // Extract audio first (needed for resource pack)
                Path ogg = work.resolve(id + ".ogg");
                boolean hasAudio = extractAudio(url, ogg);
                final AudioPackService.Pack pack = hasAudio ? packs.buildAndHost(ogg, id) : null;

                int maxW = plugin.getConfig().getInt("video.max-width", 512);
                int fps = plugin.getConfig().getInt("video.fps", 12);
                int outW = Math.min(screen.getPixelWidth(), maxW);
                // keep aspect roughly matching the screen
                int outH = Math.max(64, outW * screen.getPixelHeight() / Math.max(1, screen.getPixelWidth()));
                outH = Math.min(outH, screen.getPixelHeight());

                Session s = new Session(starter.getUniqueId(), screen.getId(), url, outW, outH, fps, pack);
                session = s;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (pack != null) {
                        starter.sendMessage("§aAudio pack ready — accept the resource pack prompt for sound.");
                        starter.sendMessage("§7Video starts when the pack loads (or shortly without sound).");
                        offerPackToNearby(screen, pack);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (session == s && !s.started.get()) {
                                startPlayback(s, false);
                            }
                        }, 20L * plugin.getConfig().getInt("video.pack-wait-seconds", 8));
                    } else {
                        starter.sendMessage("§eNo audio track — playing video silently.");
                        startPlayback(s, false);
                    }
                });
            } catch (Exception e) {
                busy.set(false);
                session = null;
                plugin.getLogger().warning("Video prepare failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        starter.sendMessage("§cVideo failed: §7" + e.getMessage()));
            }
        });
    }

    private void offerPackToNearby(DoomScreen screen, AudioPackService.Pack pack) {
        var world = Bukkit.getWorld(screen.getWorldName());
        if (world == null) {
            return;
        }
        Location center = screen.getCenter(world);
        byte[] hash = HexFormat.of().parseHex(pack.sha1Hex());
        String prompt = "MineDoom video audio — required for sound";
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= 64 * 64) {
                try {
                    p.setResourcePack(pack.url(), hash, prompt, false);
                } catch (NoSuchMethodError e) {
                    p.setResourcePack(pack.url(), pack.sha1Hex());
                } catch (Exception e) {
                    plugin.getLogger().warning("setResourcePack failed for " + p.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent event) {
        Session s = session;
        if (s == null) {
            return;
        }
        PlayerResourcePackStatusEvent.Status st = event.getStatus();
        if (st == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
                || st == PlayerResourcePackStatusEvent.Status.ACCEPTED) {
            packReady.put(event.getPlayer().getUniqueId(), true);
            if (st == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED && !s.started.get()) {
                // Start as soon as the requesting player (or anyone nearby) loads the pack
                startPlayback(s, true);
            }
        }
    }

    private synchronized void startPlayback(Session s, boolean withSoundIntent) {
        if (!s.started.compareAndSet(false, true)) {
            return;
        }
        if (session != s) {
            return;
        }
        DoomScreen screen = screens.get(s.screenId).orElse(null);
        if (screen == null || screen.isHidden()) {
            stop();
            return;
        }

        Player starter = Bukkit.getPlayer(s.starterId);
        if (starter != null) {
            starter.sendMessage("§a▶ Playing video"
                    + (withSoundIntent ? " §7(with sound)" : " §8(no pack — silent)")
                    + " §7· /google stop to end");
        }

        // Play audio for everyone nearby if we have a pack
        if (s.pack != null) {
            var world = Bukkit.getWorld(screen.getWorldName());
            if (world != null) {
                Location center = screen.getCenter(world);
                float vol = (float) plugin.getConfig().getDouble("video.volume", 1.0);
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(center) > 64 * 64) {
                        continue;
                    }
                    try {
                        p.playSound(center, s.pack.soundKey(), SoundCategory.RECORDS, vol, 1f);
                        p.playSound(center, "minecraft:" + s.pack.soundKey(), SoundCategory.RECORDS, vol, 1f);
                    } catch (Exception e) {
                        plugin.getLogger().warning("playSound failed: " + e.getMessage());
                    }
                }
            }
        }

        s.decodeThread = new Thread(() -> decodeLoop(s, screen), "MineDoom-Video");
        s.decodeThread.setDaemon(true);
        s.decodeThread.start();
    }

    private void decodeLoop(Session s, DoomScreen screen) {
        Process proc = null;
        try {
            Path ff = ffmpeg.ffmpeg();
            ProcessBuilder pb = new ProcessBuilder(
                    ff.toString(),
                    "-hide_banner", "-loglevel", "error",
                    "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                    "-i", s.url,
                    "-an",
                    "-vf", "scale=" + s.width + ":" + s.height + ":force_original_aspect_ratio=decrease,"
                            + "pad=" + s.width + ":" + s.height + ":(ow-iw)/2:(oh-ih)/2:black",
                    "-r", String.valueOf(s.fps),
                    "-f", "rawvideo",
                    "-pix_fmt", "rgb24",
                    "pipe:1"
            );
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            proc = pb.start();
            s.process = proc;

            int frameBytes = s.width * s.height * 3;
            byte[] buf = new byte[frameBytes];
            InputStream in = new BufferedInputStream(proc.getInputStream(), frameBytes * 2);
            long frameIntervalNs = 1_000_000_000L / Math.max(1, s.fps);
            long next = System.nanoTime();

            while (session == s && !Thread.currentThread().isInterrupted()) {
                int read = 0;
                while (read < frameBytes) {
                    int n = in.read(buf, read, frameBytes - read);
                    if (n < 0) {
                        finish(s, "§aVideo finished.");
                        return;
                    }
                    read += n;
                }
                byte[] frame = buf.clone();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (session != s) {
                        return;
                    }
                    DoomScreen live = screens.get(s.screenId).orElse(null);
                    if (live == null || live.isHidden()) {
                        return;
                    }
                    screens.pushImage(live, frame, s.width, s.height);
                    var w = Bukkit.getWorld(live.getWorldName());
                    if (w != null && (s.broadcastTick++ % Math.max(1, s.fps)) == 0) {
                        screens.broadcastMaps(live, live.getCenter(w), 64);
                    }
                });
                next += frameIntervalNs;
                long sleep = next - System.nanoTime();
                if (sleep > 0) {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                } else {
                    next = System.nanoTime();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().warning("Video decode error: " + e.getMessage());
            finish(s, "§cVideo error: §7" + e.getMessage());
        } finally {
            if (proc != null) {
                proc.destroyForcibly();
            }
        }
    }

    private void finish(Session s, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session == s) {
                stop();
                Player p = Bukkit.getPlayer(s.starterId);
                if (p != null) {
                    p.sendMessage(message);
                }
            }
        });
    }

    /** @return true if an ogg with audio was written */
    private boolean extractAudio(String url, Path ogg) throws Exception {
        Files.deleteIfExists(ogg);
        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg.ffmpeg().toString(),
                "-hide_banner", "-loglevel", "error",
                "-y",
                "-i", url,
                "-vn",
                "-c:a", "libvorbis",
                "-q:a", "4",
                ogg.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (InputStream in = p.getInputStream()) {
            in.readAllBytes();
        }
        boolean ok = p.waitFor(plugin.getConfig().getInt("video.prepare-timeout-seconds", 180),
                java.util.concurrent.TimeUnit.SECONDS);
        if (!ok) {
            p.destroyForcibly();
            plugin.getLogger().warning("Audio extract timed out");
            return false;
        }
        return p.exitValue() == 0 && Files.exists(ogg) && Files.size(ogg) > 500;
    }

    private static final class Session {
        final UUID starterId;
        final UUID screenId;
        final String url;
        final int width;
        final int height;
        final int fps;
        final AudioPackService.Pack pack; // nullable
        final AtomicBoolean started = new AtomicBoolean(false);
        volatile Process process;
        volatile Thread decodeThread;
        int broadcastTick;

        Session(UUID starterId, UUID screenId, String url, int w, int h, int fps, AudioPackService.Pack pack) {
            this.starterId = starterId;
            this.screenId = screenId;
            this.url = url;
            this.width = w;
            this.height = h;
            this.fps = fps;
            this.pack = pack;
        }

        void stop() {
            started.set(true);
            if (decodeThread != null) {
                decodeThread.interrupt();
            }
            if (process != null) {
                process.destroyForcibly();
            }
            if (pack != null) {
                try {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.stopSound(pack.soundKey(), SoundCategory.RECORDS);
                        p.stopSound("minecraft:" + pack.soundKey(), SoundCategory.RECORDS);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
