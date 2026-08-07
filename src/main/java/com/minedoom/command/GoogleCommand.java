package com.minedoom.command;

import com.minedoom.MineDoomPlugin;
import com.minedoom.google.GoogleBrowser;
import com.minedoom.screen.ControllerListener;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenKind;
import com.minedoom.screen.ScreenManager;
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

    public GoogleCommand(
            MineDoomPlugin plugin,
            ScreenManager screens,
            GoogleBrowser browser,
            ControllerListener controllers
    ) {
        this.plugin = plugin;
        this.screens = screens;
        this.browser = browser;
        this.controllers = controllers;
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
                    loadUrl(player, screen, browser.homeUrl(), "Google Home");
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
                loadUrl(player, screen.get(), browser.homeUrl(), "Google Home");
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
                loadUrl(player, screen.get(), browser.searchUrl(query), "Search: " + query);
            }
            case "go", "open", "url" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /google go <url>");
                    return true;
                }
                Optional<DoomScreen> screen = screens.findNearestVisible(player.getLocation(), 24, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Google screen nearby.");
                    return true;
                }
                String url = args[1];
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                // Keep it Google-scoped unless they explicitly go elsewhere
                loadUrl(player, screen.get(), url, url);
            }
            case "refresh", "reload" -> {
                Optional<DoomScreen> screen = screens.findNearestVisible(player.getLocation(), 24, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Google screen nearby.");
                    return true;
                }
                loadUrl(player, screen.get(), browser.getCurrentUrl(), "Refresh");
            }
            case "remove", "delete" -> {
                Optional<DoomScreen> screen = screens.findNearest(player.getLocation(), 16, ScreenKind.GOOGLE);
                if (screen.isEmpty()) {
                    // fall back to any screen
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
                        + " mode=§ahttp §7url=§f" + browser.getCurrentUrl());
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void loadUrl(Player player, DoomScreen screen, String url, String label) {
        player.sendMessage("§7Loading §f" + label + "§7…");
        int w = screen.getPixelWidth();
        int h = screen.getPixelHeight();

        CompletableFuture.supplyAsync(() -> {
            try {
                return browser.captureRgb(url, w, h);
            } catch (Exception e) {
                plugin.getLogger().warning("Google capture failed: " + e.getMessage());
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
        player.sendMessage("§e/google home §7— show google.com");
        player.sendMessage("§e/google search <query> §7— search Google");
        player.sendMessage("§e/google go <url> §7— open a URL");
        player.sendMessage("§e/google refresh §7— reload current page");
        player.sendMessage("§e/google give §7— open switch blocks (lever show/hide)");
        player.sendMessage("§e/google remove §7— remove nearest Google screen");
        player.sendMessage("§8No Chrome needed — works on MineKeep / shared hosts.");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = Arrays.asList("wand", "place", "home", "search", "go", "refresh", "remove", "give", "status", "help");
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
