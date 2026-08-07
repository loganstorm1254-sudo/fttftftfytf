package com.minedoom.google;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Captures real Google pages with headless Chrome and returns RGB frames for map screens.
 */
public final class GoogleBrowser {

    private final JavaPlugin plugin;
    private final Path workDir;
    private volatile String currentUrl = "https://www.google.com/";

    public GoogleBrowser(JavaPlugin plugin) {
        this.plugin = plugin;
        this.workDir = plugin.getDataFolder().toPath().resolve("chrome");
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String chromeBinary() {
        String configured = plugin.getConfig().getString("google.chrome-path", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        for (String candidate : List.of("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "chrome")) {
            Path path = which(candidate);
            if (path != null) {
                return path.toString();
            }
        }
        return null;
    }

    public boolean isAvailable() {
        return chromeBinary() != null;
    }

    public String homeUrl() {
        return "https://www.google.com/";
    }

    public String searchUrl(String query) {
        return "https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&hl=en";
    }

    /**
     * Capture a page to an RGB byte array sized to {@code outW}x{@code outH}.
     */
    public byte[] captureRgb(String url, int outW, int outH) throws IOException, InterruptedException {
        String chrome = chromeBinary();
        if (chrome == null) {
            throw new IOException("Chrome/Chromium not found. Install google-chrome or set google.chrome-path in config.yml");
        }

        Files.createDirectories(workDir);
        Path profile = workDir.resolve("profile");
        Path shot = workDir.resolve("shot-" + Thread.currentThread().threadId() + ".png");
        Files.deleteIfExists(shot);

        int winW = Math.max(640, Math.min(1280, outW));
        int winH = Math.max(360, Math.min(720, outH));
        // Keep aspect close to the wall
        if (outW > 0 && outH > 0) {
            double aspect = (double) outW / (double) outH;
            winH = (int) Math.round(winW / aspect);
            winH = Math.max(360, Math.min(900, winH));
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(chrome);
        cmd.add("--headless=new");
        cmd.add("--disable-gpu");
        cmd.add("--no-sandbox");
        cmd.add("--disable-dev-shm-usage");
        cmd.add("--hide-scrollbars");
        cmd.add("--user-data-dir=" + profile.toAbsolutePath());
        cmd.add("--window-size=" + winW + "," + winH);
        cmd.add("--virtual-time-budget=" + plugin.getConfig().getInt("google.virtual-time-ms", 5000));
        cmd.add("--screenshot=" + shot.toAbsolutePath());
        cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(workDir.toFile());
        Process proc = pb.start();
        boolean finished = proc.waitFor(plugin.getConfig().getInt("google.timeout-seconds", 45), TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new IOException("Chrome timed out loading " + url);
        }
        if (!Files.exists(shot) || Files.size(shot) < 100) {
            throw new IOException("Chrome did not write a screenshot for " + url + " (exit " + proc.exitValue() + ")");
        }

        BufferedImage img = ImageIO.read(shot.toFile());
        Files.deleteIfExists(shot);
        if (img == null) {
            throw new IOException("Failed to read Chrome screenshot");
        }

        currentUrl = url;
        return scaleToRgb(img, outW, outH);
    }

    public static byte[] scaleToRgb(BufferedImage src, int outW, int outH) {
        BufferedImage scaled = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, outW, outH, null);
        g.dispose();

        byte[] rgb = new byte[outW * outH * 3];
        int i = 0;
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                int p = scaled.getRGB(x, y);
                rgb[i++] = (byte) ((p >> 16) & 0xFF);
                rgb[i++] = (byte) ((p >> 8) & 0xFF);
                rgb[i++] = (byte) (p & 0xFF);
            }
        }
        return rgb;
    }

    /** Simple offline Google homepage placeholder if Chrome is missing. */
    public byte[] renderOfflineHome(int outW, int outH, String message) {
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, outW, outH);
        g.setColor(new java.awt.Color(66, 133, 244));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.max(18, outH / 10)));
        String title = "Google";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (outW - tw) / 2, outH / 3);
        g.setColor(java.awt.Color.DARK_GRAY);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, Math.max(10, outH / 22)));
        drawWrapped(g, message, 16, outH / 2, outW - 32);
        g.setColor(new java.awt.Color(228, 228, 228));
        int boxW = Math.min(outW - 40, 400);
        int boxH = Math.max(24, outH / 14);
        g.fillRoundRect((outW - boxW) / 2, outH / 2 + 40, boxW, boxH, 12, 12);
        g.setColor(java.awt.Color.GRAY);
        g.drawRoundRect((outW - boxW) / 2, outH / 2 + 40, boxW, boxH, 12, 12);
        g.dispose();
        return scaleToRgb(img, outW, outH);
    }

    private static void drawWrapped(java.awt.Graphics2D g, String text, int x, int y, int maxW) {
        var fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (String word : text.split(" ")) {
            String trial = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(trial) > maxW) {
                g.drawString(line.toString(), x, yy);
                yy += fm.getHeight();
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, yy);
        }
    }

    private static Path which(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(":")) {
            Path p = Path.of(dir, name);
            if (Files.isExecutable(p)) {
                return p;
            }
        }
        return null;
    }
}
