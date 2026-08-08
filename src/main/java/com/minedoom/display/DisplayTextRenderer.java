package com.minedoom.display;

import com.minedoom.google.GoogleBrowser;
import com.minedoom.screen.DoomScreen;
import com.minedoom.screen.ScreenManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Renders terminal display text (with placeholders) onto a map screen. */
public final class DisplayTextRenderer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ScreenManager screens;

    public DisplayTextRenderer(ScreenManager screens) {
        this.screens = screens;
    }

    public static String applyPlaceholders(String raw, Location context) {
        if (raw == null) {
            raw = "";
        }
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        LocalDateTime now = LocalDateTime.now();
        String world = context != null && context.getWorld() != null
                ? context.getWorld().getName()
                : (Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().getFirst().getName());

        String tps = "?";
        try {
            double[] arr = Bukkit.getTPS();
            if (arr != null && arr.length > 0) {
                tps = String.format("%.1f", Math.min(20.0, arr[0]));
            }
        } catch (Throwable ignored) {
            // non-Paper
        }

        return raw
                .replace("{playercount}", String.valueOf(online))
                .replace("{online}", String.valueOf(online))
                .replace("{players}", String.valueOf(online))
                .replace("{maxplayers}", String.valueOf(max))
                .replace("{world}", world)
                .replace("{time}", now.format(TIME_FMT))
                .replace("{date}", now.format(DATE_FMT))
                .replace("{tps}", tps)
                .replace("\\n", "\n")
                .replace("|", "\n");
    }

    public void pushText(DoomScreen screen, String template, Location context) {
        if (screen == null || screen.isHidden()) {
            return;
        }
        String text = applyPlaceholders(template, context);
        if (text.isBlank()) {
            text = " ";
        }
        int w = screen.getPixelWidth();
        int h = screen.getPixelHeight();
        BufferedImage img = drawCentered(text, w, h);
        byte[] rgb = GoogleBrowser.scaleToRgb(img, w, h);
        screens.pushImage(screen, rgb, w, h);
        World world = Bukkit.getWorld(screen.getWorldName());
        if (world != null) {
            screens.broadcastMaps(screen, screen.getCenter(world), 64);
        }
    }

    static BufferedImage drawCentered(String text, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(10, 14, 28));
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            String[] paragraphs = text.split("\n", -1);
            int fontSize = Math.max(18, Math.min(width, height) / 8);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();

            int maxLineWidth = (int) (width * 0.88);
            // Shrink font if needed to fit longest word / wrap reasonably
            while (fontSize > 14) {
                boolean tooWide = false;
                for (String p : paragraphs) {
                    if (fm.stringWidth(p) > maxLineWidth * 3) {
                        tooWide = true;
                        break;
                    }
                }
                List<String> trial = wrapAll(paragraphs, fm, maxLineWidth);
                int totalH = trial.size() * fm.getHeight();
                if (!tooWide && totalH <= height * 0.85) {
                    break;
                }
                fontSize -= 2;
                font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
                g.setFont(font);
                fm = g.getFontMetrics();
            }

            List<String> lines = wrapAll(paragraphs, fm, maxLineWidth);
            int lineH = fm.getHeight();
            int blockH = lines.size() * lineH;
            int y = Math.max(fm.getAscent(), (height - blockH) / 2 + fm.getAscent());

            g.setColor(new Color(0, 255, 180));
            for (String line : lines) {
                int x = (width - fm.stringWidth(line)) / 2;
                g.drawString(line, Math.max(4, x), y);
                y += lineH;
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private static List<String> wrapAll(String[] paragraphs, FontMetrics fm, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String p : paragraphs) {
            if (p.isEmpty()) {
                out.add("");
                continue;
            }
            out.addAll(wrapLine(p, fm, maxWidth));
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private static List<String> wrapLine(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String trial = cur.isEmpty() ? word : cur + " " + word;
            if (fm.stringWidth(trial) <= maxWidth) {
                cur = new StringBuilder(trial);
            } else {
                if (!cur.isEmpty()) {
                    lines.add(cur.toString());
                }
                if (fm.stringWidth(word) > maxWidth) {
                    // hard-break long tokens
                    String rest = word;
                    while (fm.stringWidth(rest) > maxWidth && rest.length() > 1) {
                        int cut = Math.max(1, rest.length() / 2);
                        while (cut > 1 && fm.stringWidth(rest.substring(0, cut)) > maxWidth) {
                            cut--;
                        }
                        lines.add(rest.substring(0, cut));
                        rest = rest.substring(cut);
                    }
                    cur = new StringBuilder(rest);
                } else {
                    cur = new StringBuilder(word);
                }
            }
        }
        if (!cur.isEmpty()) {
            lines.add(cur.toString());
        }
        return lines;
    }
}
