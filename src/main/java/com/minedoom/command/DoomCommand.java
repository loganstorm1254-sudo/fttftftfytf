package com.minedoom.command;

import com.minedoom.MineDoomPlugin;
import com.minedoom.doom.DoomEngine;
import com.minedoom.doom.PureDoomNative;
import com.minedoom.input.DoomInputListener;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DoomCommand implements CommandExecutor, TabCompleter {

    private final MineDoomPlugin plugin;
    private final DoomEngine engine;
    private final ScreenManager screens;
    private final DoomInputListener input;

    public DoomCommand(MineDoomPlugin plugin, DoomEngine engine, ScreenManager screens, DoomInputListener input) {
        this.plugin = plugin;
        this.engine = engine;
        this.screens = screens;
        this.input = input;
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
                player.performCommand("/wand");
                player.sendMessage("§eWorldEdit wand given (if WorldEdit is installed).");
                player.sendMessage("§7Select a §fflat vertical wall§7 (one block thick), then §a/doom place");
            }
            case "place", "create", "screen" -> {
                try {
                    DoomScreen screen = screens.placeFromWorldEdit(player);
                    engine.ensureStarted();
                    player.sendMessage("§aDoom screen placed §7(" + screen.getTilesX() + "x" + screen.getTilesY()
                            + " maps, " + screen.getPixelWidth() + "x" + screen.getPixelHeight() + " px)");
                    player.sendMessage("§7Stand in front and run §a/doom play");
                } catch (Exception e) {
                    player.sendMessage("§c" + e.getMessage());
                }
            }
            case "play", "start" -> {
                Optional<DoomScreen> screen = screens.findNearest(player.getLocation(), 16);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo Doom screen nearby. §7Select a wall with the WorldEdit axe and §a/doom place");
                    return true;
                }
                input.startPlaying(player, screen.get());
            }
            case "stop", "quit", "exit" -> {
                if (!input.isPlaying(player)) {
                    player.sendMessage("§7You are not playing.");
                    return true;
                }
                input.stopPlaying(player);
            }
            case "remove", "delete" -> {
                Optional<DoomScreen> screen = screens.findNearest(player.getLocation(), 16);
                if (screen.isEmpty()) {
                    player.sendMessage("§cNo screen nearby.");
                    return true;
                }
                if (input.isPlaying(player)) {
                    input.stopPlaying(player);
                }
                screens.remove(screen.get());
                player.sendMessage("§aDoom screen removed.");
            }
            case "enter" -> {
                // helper to press Enter on menus / title
                if (!input.isPlaying(player)) {
                    player.sendMessage("§cStart with /doom play first.");
                    return true;
                }
                engine.keyDown(PureDoomNative.KEY_ENTER);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> engine.keyUp(PureDoomNative.KEY_ENTER), 2L);
                player.sendMessage("§7Sent ENTER");
            }
            case "esc", "escape" -> {
                if (!input.isPlaying(player)) {
                    player.sendMessage("§cStart with /doom play first.");
                    return true;
                }
                engine.keyDown(PureDoomNative.KEY_ESCAPE);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> engine.keyUp(PureDoomNative.KEY_ESCAPE), 2L);
                player.sendMessage("§7Sent ESC");
            }
            case "status" -> {
                player.sendMessage("§cMineDoom §7screens=" + screens.getScreens().size()
                        + " engine=" + (engine.isRunning() ? "§arunning" : "§8idle")
                        + " §7playing=" + (input.isPlaying(player) ? "§ayes" : "§8no"));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§c§lMineDoom §8— DOOM in Minecraft");
        player.sendMessage("§e/doom wand §7— get WorldEdit axe");
        player.sendMessage("§e/doom place §7— turn WE selection into a Doom screen");
        player.sendMessage("§e/doom play §7— sit down and play with keyboard & mouse");
        player.sendMessage("§e/doom stop §7— exit play mode");
        player.sendMessage("§e/doom remove §7— remove nearest screen");
        player.sendMessage("§e/doom enter §7— press Enter (menus)");
        player.sendMessage("§e/doom esc §7— press Escape");
        player.sendMessage("§8Controls: WASD · mouse look · LMB fire · RMB use · Shift run · hotbar weapons");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = Arrays.asList("wand", "place", "play", "stop", "remove", "enter", "esc", "status", "help");
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
