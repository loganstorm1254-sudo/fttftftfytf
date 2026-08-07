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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real video on map screens: ffmpeg RGB frames + optional OGG audio via resource pack.
 * Video starts immediately; audio attaches when/if the pack loads (never blocks on preview).
 */
public final class VideoPlayer implements Listener {

    private static final Pattern ARCHIVE_DETAILS = Pattern.compile(
            "archive\\.org/details/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_FILE = Pattern.compile(
            "\"name\"\\s*:\\s*\"([^\"]+\\.(?:mp4|webm|ogv|mkv))\"", Pattern.CASE_INSENSITIVE);

    private final MineDoomPlugin plugin;
    private final ScreenManager screens;
    private final FfmpegLocator ffmpeg;
    private final AudioPackService packs;
    private final GoogleBrowser browser;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile Session session;

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
    }

    public void play(Player starter, DoomScreen screen, String rawUrl) {
        if (!busy.compareAndSet(false, true)) {
            stop();
            if (!busy.compareAndSet(false, true)) {
                starter.sendMessage("§cCould not stop the previous video. Try again.");
                return;
            }
        }

        String url;
        try {
            url = browser.normalizeUrl(rawUrl);
        } catch (Exception e) {
            busy.set(false);
            starter.sendMessage("§cBad URL: §7" + e.getMessage());
            return;
        }

        // Show loading on the wall immediately (not a fake player card)
        paintStatus(screen, "Loading video…", url);
        starter.sendMessage("§e▶ Starting real playback…");

        int maxW = plugin.getConfig().getInt("video.max-width", 512);
        int fps = plugin.getConfig().getInt("video.fps", 12);
        int outW = Math.min(screen.getPixelWidth(), maxW);
        int outH = Math.max(64, outW * screen.getPixelHeight() / Math.max(1, screen.getPixelWidth()));
        outH = Math.min(outH, screen.getPixelHeight());

        Session s = new Session(starter.getUniqueId(), screen.getId(), url, outW, outH, fps);
        session = s;

        // 1) Resolve + start video frames ASAP (do not wait for audio pack)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ffmpeg.available()) {
                    throw new IllegalStateException("ffmpeg unavailable — check server logs / video.ffmpeg-path");
                }
                String playUrl = resolvePlayableUrl(url);
                s.url = playUrl;
                plugin.getLogger().info("Video play URL: " + playUrl);
                Bukkit.getScheduler().runTask(plugin, () -> startVideoFrames(s));
            } catch (Exception e) {
                plugin.getLogger().warning("Video start failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (session == s) {
                        stop();
                        paintStatus(screen, "Video failed", e.getMessage());
                        starter.sendMessage("§cVideo failed: §7" + e.getMessage());
                    }
                });
            }
        });

        // 2) Audio pack in parallel — never blocks video
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> prepareAudio(s, starter, screen));
    }

    private void prepareAudio(Session s, Player starter, DoomScreen screen) {
        try {
            Path work = plugin.getDataFolder().toPath().resolve("video");
            Files.createDirectories(work);
            String id = "v" + Integer.toHexString(s.url.hashCode() & 0x7fffffff);
            Path ogg = work.resolve(id + ".ogg");
            if (!extractAudio(s.url, ogg)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        starter.sendMessage("§7No audio track (or extract failed) — video is silent."));
                return;
            }
            AudioPackService.Pack pack = packs.buildAndHost(ogg, id);
            s.pack = pack;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (session != s) {
                    return;
                }
                starter.sendMessage("§aSound pack ready — §eaccept the resource pack§a for audio.");
                offerPackToNearby(screen, pack);
                // If video already running, try playing sound now for anyone who already has packs off
                // (real sound starts on SUCCESSFULLY_LOADED)
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Audio pack failed (video still plays silent): " + e.getMessage());
            Bukkit.getScheduler().runTask(plugin, () ->
                    starter.sendMessage("§7Audio unavailable (§f" + e.getMessage() + "§7) — video still playing."));
        }
    }

    private String resolvePlayableUrl(String url) throws Exception {
        Matcher m = ARCHIVE_DETAILS.matcher(url);
        if (m.find()) {
            String id = m.group(1);
            String metaUrl = "https://archive.org/metadata/" + id;
            HttpRequest req = HttpRequest.newBuilder(URI.create(metaUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "MineDoom/1.0")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 400) {
                String best = pickArchiveFile(res.body());
                if (best != null) {
                    return "https://archive.org/download/" + id + "/" + best;
                }
            }
            // ffmpeg can often open the details page / derivative playlist
            return "https://archive.org/download/" + id + "/" + id + ".mp4";
        }
        return url;
    }

    private static String pickArchiveFile(String json) {
        String bestMp4 = null;
        int bestScore = -1;
        Matcher m = META_FILE.matcher(json);
        while (m.find()) {
            String name = m.group(1);
            String lower = name.toLowerCase();
            if (lower.contains("thumb") || lower.contains("sprite")) {
                continue;
            }
            int score = 0;
            if (lower.endsWith(".mp4")) score += 10;
            if (lower.endsWith(".webm")) score += 8;
            if (lower.endsWith(".ogv")) score += 6;
            if (lower.contains("1080")) score += 3;
            if (lower.contains("720")) score += 2;
            if (lower.contains("480")) score += 1;
            if (score > bestScore) {
                bestScore = score;
                bestMp4 = name;
            }
        }
        return bestMp4;
    }

    private void startVideoFrames(Session s) {
        if (session != s) {
            return;
        }
        DoomScreen screen = screens.get(s.screenId).orElse(null);
        if (screen == null || screen.isHidden()) {
            stop();
            return;
        }
        if (!s.framesStarted.compareAndSet(false, true)) {
            return;
        }
        Player starter = Bukkit.getPlayer(s.starterId);
        if (starter != null) {
            starter.sendMessage("§a▶ Video playing on the wall §7· /google stop to end");
        }
        s.decodeThread = new Thread(() -> decodeLoop(s), "MineDoom-Video");
        s.decodeThread.setDaemon(true);
        s.decodeThread.start();
    }

    private void offerPackToNearby(DoomScreen screen, AudioPackService.Pack pack) {
        var world = Bukkit.getWorld(screen.getWorldName());
        if (world == null) {
            return;
        }
        Location center = screen.getCenter(world);
        byte[] hash = HexFormat.of().parseHex(pack.sha1Hex());
        String prompt = "MineDoom video sound — accept for audio";
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
        if (s == null || s.pack == null) {
            return;
        }
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            playSoundFor(event.getPlayer(), s);
            event.getPlayer().sendMessage("§aSound enabled for video.");
        }
    }

    private void playSoundFor(Player p, Session s) {
        if (s.pack == null) {
            return;
        }
        DoomScreen screen = screens.get(s.screenId).orElse(null);
        if (screen == null) {
            return;
        }
        var world = Bukkit.getWorld(screen.getWorldName());
        if (world == null) {
            return;
        }
        Location center = screen.getCenter(world);
        float vol = (float) plugin.getConfig().getDouble("video.volume", 1.0);
        try {
            p.stopSound(s.pack.soundKey(), SoundCategory.RECORDS);
            p.stopSound("minecraft:" + s.pack.soundKey(), SoundCategory.RECORDS);
            p.playSound(center, s.pack.soundKey(), SoundCategory.RECORDS, vol, 1f);
            p.playSound(center, "minecraft:" + s.pack.soundKey(), SoundCategory.RECORDS, vol, 1f);
        } catch (Exception e) {
            plugin.getLogger().warning("playSound failed: " + e.getMessage());
        }
    }

    private void decodeLoop(Session s) {
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
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            proc = pb.start();
            s.process = proc;

            // Drain stderr so ffmpeg can't block
            Process finalProc = proc;
            Thread errDrain = new Thread(() -> {
                try (InputStream err = finalProc.getErrorStream()) {
                    err.readAllBytes();
                } catch (Exception ignored) {
                }
            }, "MineDoom-Video-err");
            errDrain.setDaemon(true);
            errDrain.start();

            int frameBytes = s.width * s.height * 3;
            byte[] buf = new byte[frameBytes];
            InputStream in = new BufferedInputStream(proc.getInputStream(), frameBytes * 2);
            long frameIntervalNs = 1_000_000_000L / Math.max(1, s.fps);
            long next = System.nanoTime();
            int frames = 0;

            while (session == s && !Thread.currentThread().isInterrupted()) {
                int read = 0;
                while (read < frameBytes) {
                    int n = in.read(buf, read, frameBytes - read);
                    if (n < 0) {
                        finish(s, frames == 0
                                ? "§cVideo ended with no frames — URL may be blocked or not a video file."
                                : "§aVideo finished.");
                        return;
                    }
                    read += n;
                }
                frames++;
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

    private void paintStatus(DoomScreen screen, String title, String detail) {
        try {
            int w = screen.getPixelWidth();
            int h = screen.getPixelHeight();
            byte[] rgb = browser.renderOfflineHome(w, h, title + " — " + (detail == null ? "" : detail));
            screens.pushImage(screen, rgb, w, h);
            var world = Bukkit.getWorld(screen.getWorldName());
            if (world != null) {
                screens.broadcastMaps(screen, screen.getCenter(world), 64);
            }
        } catch (Exception ignored) {
        }
    }

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
            return false;
        }
        return p.exitValue() == 0 && Files.exists(ogg) && Files.size(ogg) > 500;
    }

    private static final class Session {
        final UUID starterId;
        final UUID screenId;
        volatile String url;
        final int width;
        final int height;
        final int fps;
        volatile AudioPackService.Pack pack;
        final AtomicBoolean framesStarted = new AtomicBoolean(false);
        volatile Process process;
        volatile Thread decodeThread;
        int broadcastTick;

        Session(UUID starterId, UUID screenId, String url, int w, int h, int fps) {
            this.starterId = starterId;
            this.screenId = screenId;
            this.url = url;
            this.width = w;
            this.height = h;
            this.fps = fps;
        }

        void stop() {
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
