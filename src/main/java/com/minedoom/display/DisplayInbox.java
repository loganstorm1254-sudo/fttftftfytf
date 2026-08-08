package com.minedoom.display;

import com.minedoom.MineDoomPlugin;
import com.minedoom.google.GoogleBrowser;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenKind;
import com.minedoom.screen.ScreenManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Keeps powered Display Terminal screens updated:
 * - always re-renders typed text (so {playercount} stays live)
 * - optionally overlays a PNG from plugins/MineDoom/display/ if the terminal text is blank
 */
public final class DisplayInbox {

    private final MineDoomPlugin plugin;
    private final ScreenManager screens;
    private final DisplayTerminalStore terminals;
    private final DisplayTextRenderer textRenderer;
    private final Path inbox;
    private BukkitTask task;
    private FileTime lastSeen;
    private Path lastFile;

    public DisplayInbox(
            MineDoomPlugin plugin,
            ScreenManager screens,
            DisplayTerminalStore terminals,
            DisplayTextRenderer textRenderer
    ) {
        this.plugin = plugin;
        this.screens = screens;
        this.terminals = terminals;
        this.textRenderer = textRenderer;
        this.inbox = plugin.getDataFolder().toPath().resolve("display");
    }

    public Path inboxPath() {
        return inbox;
    }

    public void start() {
        try {
            Files.createDirectories(inbox);
            Path readme = inbox.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        Optional: drop PNG/JPG frames here.
                        
                        Normal use is typing text in the Display Terminal GUI
                        (supports {playercount}, {maxplayers}, {time}, etc).
                        
                        PNGs are only used if a terminal's text is cleared to blank.
                        """);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create display inbox: " + e.getMessage());
        }
        int every = Math.max(1, plugin.getConfig().getInt("display.poll-ticks", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, every, every);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        try {
            // Refresh typed text on every powered terminal (live placeholders)
            for (DisplayTerminalStore.Terminal term : terminals.all()) {
                if (term.linkedScreenId() == null) {
                    continue;
                }
                var loc = term.location();
                if (loc == null) {
                    continue;
                }
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) {
                    continue;
                }
                Optional<DoomScreen> screen = screens.get(term.linkedScreenId());
                if (screen.isEmpty() || screen.get().isHidden() || screen.get().getKind() != ScreenKind.DISPLAY) {
                    continue;
                }
                String raw = term.text();
                if (raw != null && !raw.isBlank()) {
                    textRenderer.pushText(screen.get(), term.safeText(), loc);
                }
            }

            // Optional PNG path only for terminals with blank text
            refreshPngIfNeeded();
        } catch (Exception e) {
            plugin.getLogger().warning("Display refresh failed: " + e.getMessage());
        }
    }

    private void refreshPngIfNeeded() {
        try {
            if (!Files.isDirectory(inbox)) {
                return;
            }
            Optional<Path> newest = Optional.empty();
            FileTime newestTime = null;
            try (Stream<Path> stream = Files.list(inbox)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    String name = p.getFileName().toString().toLowerCase();
                    if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
                        continue;
                    }
                    FileTime t = Files.getLastModifiedTime(p);
                    if (newestTime == null || t.compareTo(newestTime) > 0) {
                        newestTime = t;
                        newest = Optional.of(p);
                    }
                }
            }
            if (newest.isEmpty()) {
                return;
            }
            boolean changed = lastSeen == null || lastFile == null
                    || !lastFile.equals(newest.get())
                    || !lastSeen.equals(newestTime);
            lastSeen = newestTime;
            lastFile = newest.get();
            if (!changed) {
                return;
            }
            BufferedImage img = ImageIO.read(newest.get().toFile());
            if (img == null) {
                return;
            }
            for (DisplayTerminalStore.Terminal term : terminals.all()) {
                if (term.linkedScreenId() == null) {
                    continue;
                }
                if (term.text() != null && !term.text().isBlank()) {
                    continue; // typed text wins
                }
                var loc = term.location();
                if (loc == null) {
                    continue;
                }
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) {
                    continue;
                }
                Optional<DoomScreen> screen = screens.get(term.linkedScreenId());
                if (screen.isEmpty() || screen.get().isHidden() || screen.get().getKind() != ScreenKind.DISPLAY) {
                    continue;
                }
                pushImage(screen.get(), img);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Display PNG tick failed: " + e.getMessage());
        }
    }

    /** Push the latest inbox frame to one screen (blank-text terminals only). */
    public void pushLatest(DoomScreen screen) {
        try {
            if (lastFile == null || !Files.exists(lastFile)) {
                Path frame = inbox.resolve("frame.png");
                if (Files.exists(frame)) {
                    lastFile = frame;
                } else {
                    return;
                }
            }
            BufferedImage img = ImageIO.read(lastFile.toFile());
            if (img != null) {
                pushImage(screen, img);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("pushLatest failed: " + e.getMessage());
        }
    }

    private void pushImage(DoomScreen screen, BufferedImage img) {
        int w = screen.getPixelWidth();
        int h = screen.getPixelHeight();
        byte[] rgb = GoogleBrowser.scaleToRgb(img, w, h);
        screens.pushImage(screen, rgb, w, h);
        var world = Bukkit.getWorld(screen.getWorldName());
        if (world != null) {
            screens.broadcastMaps(screen, screen.getCenter(world), 64);
        }
    }
}
