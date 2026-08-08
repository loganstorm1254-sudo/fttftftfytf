package com.minedoom.command;

import com.minedoom.MineDoomPlugin;
import com.minedoom.google.GoogleBrowser;
import com.minedoom.screen.ControllerListener;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenKind;
import com.minedoom.screen.ScreenManager;
import com.minedoom.video.VideoPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class GoogleCommand implements CommandExecutor, TabCompleter {

    private final MineDoomPlugin plugin;
    private final ScreenManager screens;
    private final GoogleBrowser browser;
    private final ControllerListener controllers;
    private final VideoPlayer video;

    public GoogleCommand(
            MineDoomPlugin plugin,
            ScreenManager screens,
            GoogleBrowser browser,
            ControllerListener controllers,
            VideoPlayer video
    ) {
        this.plugin = plugin;
        this.screens = screens;
        this.browser = browser;
        this.controllers = controllers;
        this.video = video;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("minedoom.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(player);
            case "wand" -> {
                player.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
                if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
                    player.performCommand("wand");
                }
                player.sendMessage("§eGoogle wand ready.");
                player.sendMessage("§7Left-click pos1 · right-click pos2 on a flat wall, then §a/google place");
            }
            case "place", "create", "screen" -> {
                try {
                    DoomScreen screen = screens.placeFromSelection(player, ScreenKind.GOOGLE);
                    player.sendMessage("§aGoogle screen placed §7(" + screen.getTilesX() + "x" + screen.getTilesY()
                            + " · face §f" + screen.getFacing() + "§7)");
                    showPainted(player, screen, browser.homeUrl(), "Google Home");
                } catch (Exception e) {
                    player.sendMessage("§c" + e.getMessage());
                }
            }
            case "home" -> {
                Optional<DoomScreen> screen = screens.findNearestVisible(player.getLocation(), 24, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Google screen nearby. §7/google wand → /google place");
                    return true;
                }
                showPainted(player, screen.get(), browser.homeUrl(), "Google Home");
            }
            case "search" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /google search <query>");
                    return true;
                }
                Optional<DoomScreen> screen = screens.findNearestVisible(player.getLocation(), 24, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Google screen nearby. §7/google wand → /google place");
                    return true;
                }
                String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                showPainted(player, screen.get(), browser.searchUrl(query), "Search: " + query);
            }
            case "go", "open", "url", "play", "watch", "video" -> {
                // go + play both mean REAL video playback — never thum.io screenshots
                Optional<DoomScreen> screen = screens.findNearestVisible(player.getLocation(), 24, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Google screen nearby.");
                    return true;
                }
                String url = resolveUrlArg(player, args);
                if (url == null) {
                    if (sub.equals("play") || sub.equals("watch") || sub.equals("video")) {
                        url = browser.getCurrentUrl();
                        if (url == null || url.isBlank() || url.contains("google.com/")) {
                            player.sendMessage("§cUsage: /google play <video-url>");
                            player.sendMessage("§7Long links: put URL in a written book, hold it, §a/google play");
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
                try {
                    url = browser.normalizeUrl(url);
                } catch (Exception e) {
                    player.sendMessage("§cBad URL: §7" + e.getMessage());
                    return true;
                }
                player.sendMessage("§eStarting §freal video playback§e (not a preview)…");
                video.play(player, screen.get(), url);
            }
            case "preview", "shot", "screenshot" -> {
                // Explicit opt-in for page screenshots only
                Optional<DoomScreen> screen = screens.findNearestVisible(player.getLocation(), 24, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Google screen nearby.");
                    return true;
                }
                String url = resolveUrlArg(player, args);
                if (url == null) {
                    return true;
                }
                try {
                    url = browser.normalizeUrl(url);
                } catch (Exception e) {
                    player.sendMessage("§cBad URL: §7" + e.getMessage());
                    return true;
                }
                player.sendMessage("§7Loading page screenshot preview…");
                showPainted(player, screen.get(), url, "Preview: " + truncateLabel(url));
            }
            case "stop", "pause" -> {
                if (!video.isPlaying()) {
                    player.sendMessage("§7No video playing.");
                    return true;
                }
                video.stop();
                player.sendMessage("§7Stopped video.");
            }
            case "refresh", "reload" -> {
                Optional<DoomScreen> screen = screens.findNearestVisible(player.getLocation(), 24, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Google screen nearby.");
                    return true;
                }
                String cur = browser.getCurrentUrl();
                if (GoogleBrowser.looksLikeVideoUrl(cur)) {
                    video.play(player, screen.get(), cur);
                } else {
                    showPainted(player, screen.get(), cur, "Refresh");
                }
            }
            case "remove", "delete" -> {
                if (video.isPlaying()) {
                    video.stop();
                }
                Optional<DoomScreen> screen = screens.findNearest(player.getLocation(), 16, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    screen = screens.findNearest(player.getLocation(), 16);
                }
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo screen nearby.");
                    return true;
                }
                screens.remove(screen.get());
                player.sendMessage("§aGoogle screen removed.");
            }
            case "give", "blocks", "switch", "switches" -> controllers.openGiveMenu(player);
            case "status" -> {
                long googleScreens = screens.getScreens().stream().filter(s -> s.getKind() == ScreenKind.GOOGLE).count();
                player.sendMessage("§eGoogle §7screens=" + googleScreens
                        + " video=" + (video.isPlaying() ? "§aplaying" : "§8idle")
                        + " §7url=§f" + truncateLabel(browser.getCurrentUrl()));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    /** null if usage was printed */
    private String resolveUrlArg(Player player, String[] args) {
        if (args.length >= 2) {
            return String.join("", Arrays.copyOfRange(args, 1, args.length)).trim();
        }
        String fromBook = browser.readUrlFromHeldBook(player);
        if (fromBook != null) {
            return fromBook;
        }
        player.sendMessage("§cUsage: /google " + args[0] + " <url>");
        player.sendMessage("§7Long video links break in chat (256 char limit).");
        player.sendMessage("§7Put the full URL in a §fwritten book§7, hold it, then run the command.");
        return null;
    }

    /** Painted Google UI / optional screenshot — never used for /google go|play. */
    private void showPainted(Player player, DoomScreen screen, String url, String label) {
        int w = screen.getPixelWidth();
        int h = screen.getPixelHeight();
        boolean wantShot = label.startsWith("Preview:");

        CompletableFuture.supplyAsync(() -> {
            try {
                if (wantShot) {
                    return browser.captureRgb(url, w, h, true);
                }
                return browser.captureRgb(url, w, h, false);
            } catch (Exception e) {
                plugin.getLogger().warning("Google render failed: " + e.getMessage());
                return browser.renderOfflineHome(w, h, "Failed: " + e.getMessage());
            }
        }).thenAccept(rgb -> Bukkit.getScheduler().runTask(plugin, () -> {
            screens.pushImage(screen, rgb, w, h);
            screens.broadcastMaps(screen, player.getLocation(), 64);
            player.sendMessage("§aGoogle screen updated §7(" + label + ")");
        }));
    }

    private void sendHelp(Player player) {
        player.sendMessage("§e§lGoogle Screen §8— separate from /doom");
        player.sendMessage("§e/google wand §7— wooden axe to select a wall");
        player.sendMessage("§e/google place §7— place a Google map screen");
        player.sendMessage("§e/google home §7— painted Google homepage");
        player.sendMessage("§e/google search <query> §7— search results");
        player.sendMessage("§e/google go <url> §7— §fREAL video playback§7 (not a preview)");
        player.sendMessage("§e/google play <url> §7— same as go — real video + sound");
        player.sendMessage("§e/google stop §7— stop video");
        player.sendMessage("§e/google preview <url> §7— page screenshot only (optional)");
        player.sendMessage("§7  long links: URL in a book → hold → /google play");
        player.sendMessage("§e/google give §7— switches + Display Terminal (16:9 / Python)");
        player.sendMessage("§8Accept the resource-pack prompt for audio.");
    }

    private static String truncateLabel(String url) {
        if (url == null) {
            return "";
        }
        return url.length() > 48 ? url.substring(0, 45) + "…" : url;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = Arrays.asList(
                    "wand", "place", "home", "search", "go", "play", "stop", "preview", "refresh", "remove", "give", "status", "help");
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String o : opts) {
                if (o.startsWith(p)) out.add(o);
            }
            return out;
        }
        return List.of();
    }
}
