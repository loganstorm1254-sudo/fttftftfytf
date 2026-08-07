package com.minedoom.video;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Finds ffmpeg/ffprobe on PATH, config, or downloads a static Linux binary into the plugin folder
 * (so MineKeep hosts that can't apt-install still work).
 */
public final class FfmpegLocator {

    private final JavaPlugin plugin;
    private Path ffmpeg;
    private Path ffprobe;

    public FfmpegLocator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized Path ffmpeg() throws IOException {
        ensure();
        return ffmpeg;
    }

    public synchronized Path ffprobe() throws IOException {
        ensure();
        return ffprobe;
    }

    public synchronized boolean available() {
        try {
            ensure();
            return ffmpeg != null && Files.isExecutable(ffmpeg);
        } catch (Exception e) {
            return false;
        }
    }

    private void ensure() throws IOException {
        if (ffmpeg != null && Files.isExecutable(ffmpeg)) {
            return;
        }
        String configured = plugin.getConfig().getString("video.ffmpeg-path", "");
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured.trim());
            if (Files.isExecutable(p)) {
                ffmpeg = p;
                ffprobe = guessSibling(p, "ffprobe");
                return;
            }
        }
        Path onPath = which("ffmpeg");
        if (onPath != null) {
            ffmpeg = onPath;
            ffprobe = which("ffprobe");
            if (ffprobe == null) {
                ffprobe = guessSibling(onPath, "ffprobe");
            }
            return;
        }
        Path bundled = plugin.getDataFolder().toPath().resolve("bin").resolve("ffmpeg");
        Path bundledProbe = plugin.getDataFolder().toPath().resolve("bin").resolve("ffprobe");
        if (!Files.isExecutable(bundled)) {
            downloadStatic(bundled.getParent());
        }
        if (!Files.isExecutable(bundled)) {
            throw new IOException("ffmpeg not found. Set video.ffmpeg-path in config.yml");
        }
        ffmpeg = bundled;
        ffprobe = Files.isExecutable(bundledProbe) ? bundledProbe : bundled;
    }

    private static Path guessSibling(Path ffmpeg, String name) {
        Path sib = ffmpeg.getParent() != null ? ffmpeg.getParent().resolve(name) : Path.of(name);
        return Files.isExecutable(sib) ? sib : ffmpeg;
    }

    private void downloadStatic(Path binDir) throws IOException {
        Files.createDirectories(binDir);
        plugin.getLogger().info("Downloading static ffmpeg for video playback (one-time)…");
        String url = plugin.getConfig().getString(
                "video.ffmpeg-download-url",
                "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/ffmpeg-linux-x64");
        String probeUrl = plugin.getConfig().getString(
                "video.ffprobe-download-url",
                "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/ffprobe-linux-x64");

        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        try {
            download(http, url, binDir.resolve("ffmpeg"));
            try {
                download(http, probeUrl, binDir.resolve("ffprobe"));
            } catch (Exception e) {
                plugin.getLogger().warning("ffprobe download skipped: " + e.getMessage());
            }
        } catch (Exception e) {
            // Fallback: johnvansickle tarball
            plugin.getLogger().warning("Static binary download failed (" + e.getMessage() + "), trying tarball…");
            try {
                downloadTarball(http, binDir);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("ffmpeg download interrupted", ie);
            }
        }
        markExecutable(binDir.resolve("ffmpeg"));
        if (Files.exists(binDir.resolve("ffprobe"))) {
            markExecutable(binDir.resolve("ffprobe"));
        }
        plugin.getLogger().info("ffmpeg ready at " + binDir.resolve("ffmpeg"));
    }

    private void downloadTarball(HttpClient http, Path binDir) throws IOException, InterruptedException {
        Path tarXz = binDir.resolve("ffmpeg-static.tar.xz");
        String url = "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "MineDoom/1.0")
                .GET()
                .build();
        HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(tarXz));
        if (res.statusCode() >= 400) {
            throw new IOException("HTTP " + res.statusCode() + " downloading ffmpeg tarball");
        }
        // Extract with tar if available
        Path tmp = binDir.resolve("extract");
        Files.createDirectories(tmp);
        Process p = new ProcessBuilder("tar", "-xJf", tarXz.toString(), "-C", tmp.toString())
                .redirectErrorStream(true)
                .start();
        try {
            if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IOException("tar extract failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("tar interrupted", e);
        }
        Path foundFfmpeg = Files.walk(tmp)
                .filter(x -> x.getFileName().toString().equals("ffmpeg"))
                .findFirst()
                .orElseThrow(() -> new IOException("ffmpeg not in tarball"));
        Files.copy(foundFfmpeg, binDir.resolve("ffmpeg"), StandardCopyOption.REPLACE_EXISTING);
        Files.walk(tmp)
                .filter(x -> x.getFileName().toString().equals("ffprobe"))
                .findFirst()
                .ifPresent(probe -> {
                    try {
                        Files.copy(probe, binDir.resolve("ffprobe"), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                    }
                });
        Files.deleteIfExists(tarXz);
    }

    private static void download(HttpClient http, String url, Path dest) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "MineDoom/1.0")
                .GET()
                .build();
        HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(dest));
        if (res.statusCode() >= 400) {
            Files.deleteIfExists(dest);
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        // Sometimes github releases are zip-wrapped — ignore if raw binary
        markExecutable(dest);
    }

    private static void markExecutable(Path path) throws IOException {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException ignored) {
            path.toFile().setExecutable(true);
        }
    }

    private static Path which(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String exe = os.contains("win") ? name + ".exe" : name;
        for (String dir : path.split(os.contains("win") ? ";" : ":")) {
            Path p = Path.of(dir, exe);
            if (Files.isExecutable(p)) {
                return p;
            }
        }
        return null;
    }
}
