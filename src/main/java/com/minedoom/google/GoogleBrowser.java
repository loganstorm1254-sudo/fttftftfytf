package com.minedoom.google;

import org.bukkit.plugin.java.JavaPlugin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders Google pages onto map screens without Chrome — HTTP fetch + AWT paint.
 * Works on shared hosts (MineKeep, etc.) that cannot install Chromium.
 */
public final class GoogleBrowser {

    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "<a[^>]*href=\"(/url\\?q=([^\"&]+)[^\"]*|https?://[^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_DESC = Pattern.compile(
            "<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRIP_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JavaPlugin plugin;
    private final HttpClient http;
    private volatile String currentUrl = "https://www.google.com/";
    private volatile String lastQuery = "";

    public GoogleBrowser(JavaPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    /** Always available — no Chrome required. */
    public boolean isAvailable() {
        return true;
    }

    public String homeUrl() {
        return "https://www.google.com/";
    }

    public String searchUrl(String query) {
        return "https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&hl=en&gbv=1";
    }

    /**
     * Capture / render a page to RGB for the map wall.
     */
    public byte[] captureRgb(String url, int outW, int outH) throws IOException, InterruptedException {
        currentUrl = url;
        String lower = url.toLowerCase();

        if (lower.contains("google.com/") && !lower.contains("/search")) {
            return renderHome(outW, outH);
        }
        if (lower.contains("google.com/search") || lower.contains("q=")) {
            String query = extractQuery(url);
            lastQuery = query;
            return renderSearch(query, outW, outH);
        }
        return renderExternalPage(url, outW, outH);
    }

    public byte[] renderOfflineHome(int outW, int outH, String message) {
        return renderMessagePage(outW, outH, "Google", message);
    }

    private byte[] renderHome(int outW, int outH) {
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableNice(g);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, outW, outH);

        // Multicolor Google logo
        drawGoogleLogo(g, outW / 2, outH / 3, Math.max(28, outH / 7));

        // Search box
        int boxW = Math.min(outW - 40, Math.max(200, outW * 2 / 3));
        int boxH = Math.max(22, outH / 12);
        int boxX = (outW - boxW) / 2;
        int boxY = outH / 2;
        g.setColor(new Color(223, 225, 229));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(boxX, boxY, boxW, boxH, boxH, boxH);
        g.setColor(new Color(117, 117, 117));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, boxH / 2)));
        g.drawString("Search Google or type a URL", boxX + 14, boxY + boxH * 2 / 3);

        // Buttons
        int btnY = boxY + boxH + Math.max(12, outH / 20);
        drawButton(g, outW / 2 - 90, btnY, "Google Search");
        drawButton(g, outW / 2 + 10, btnY, "I'm Feeling Lucky");

        g.setColor(new Color(90, 90, 90));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, outH / 28)));
        String hint = "/google search <query>";
        int hw = g.getFontMetrics().stringWidth(hint);
        g.drawString(hint, (outW - hw) / 2, outH - Math.max(14, outH / 18));
        g.dispose();
        return scaleToRgb(img, outW, outH);
    }

    private byte[] renderSearch(String query, int outW, int outH) throws IOException, InterruptedException {
        List<SearchResult> results = fetchGoogleResults(query);
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableNice(g);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, outW, outH);

        // Top bar
        g.setColor(new Color(248, 249, 250));
        g.fillRect(0, 0, outW, Math.max(36, outH / 8));
        drawGoogleLogo(g, Math.max(40, outW / 12), Math.max(22, outH / 14), Math.max(14, outH / 16));

        int boxH = Math.max(18, outH / 16);
        int boxX = Math.max(70, outW / 6);
        int boxW = outW - boxX - 16;
        int boxY = Math.max(10, outH / 32);
        g.setColor(Color.WHITE);
        g.fillRoundRect(boxX, boxY, boxW, boxH, boxH, boxH);
        g.setColor(new Color(223, 225, 229));
        g.drawRoundRect(boxX, boxY, boxW, boxH, boxH, boxH);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, boxH / 2)));
        g.drawString(truncate(query, g, boxW - 20), boxX + 10, boxY + boxH * 2 / 3);

        g.setColor(new Color(232, 234, 237));
        int dividerY = Math.max(36, outH / 8);
        g.drawLine(0, dividerY, outW, dividerY);

        int y = dividerY + Math.max(16, outH / 24);
        int titleSize = Math.max(12, outH / 22);
        int bodySize = Math.max(9, outH / 28);
        int gap = Math.max(18, outH / 14);

        if (results.isEmpty()) {
            g.setColor(new Color(95, 99, 104));
            g.setFont(new Font("SansSerif", Font.PLAIN, bodySize));
            g.drawString("No results (network blocked or Google challenged the request).", 16, y);
            g.drawString("Try again, or use /google home", 16, y + bodySize + 4);
        } else {
            for (SearchResult r : results) {
                if (y > outH - gap) {
                    break;
                }
                g.setColor(new Color(26, 13, 171));
                g.setFont(new Font("SansSerif", Font.PLAIN, titleSize));
                g.drawString(truncate(r.title, g, outW - 32), 16, y);
                y += titleSize + 2;

                g.setColor(new Color(0, 102, 33));
                g.setFont(new Font("SansSerif", Font.PLAIN, bodySize));
                g.drawString(truncate(r.url, g, outW - 32), 16, y);
                y += bodySize + 2;

                if (r.snippet != null && !r.snippet.isBlank()) {
                    g.setColor(new Color(75, 75, 75));
                    y = drawWrappedReturn(g, r.snippet, 16, y, outW - 32, bodySize) + 4;
                }
                y += Math.max(8, outH / 40);
            }
        }

        g.dispose();
        currentUrl = searchUrl(query);
        return scaleToRgb(img, outW, outH);
    }

    private byte[] renderExternalPage(String url, int outW, int outH) throws IOException, InterruptedException {
        String html = fetchHtml(url);
        String title = firstMatch(TITLE_TAG, html, url);
        String desc = firstMatch(META_DESC, html, "");
        title = cleanText(title);
        desc = cleanText(desc);
        if (desc.isBlank()) {
            desc = cleanText(STRIP_TAGS.matcher(html).replaceAll(" "));
            if (desc.length() > 400) {
                desc = desc.substring(0, 400) + "…";
            }
        }
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableNice(g);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, outW, outH);

        g.setColor(new Color(66, 133, 244));
        g.fillRect(0, 0, outW, Math.max(28, outH / 10));
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, outH / 22)));
        g.drawString(truncate(url, g, outW - 24), 12, Math.max(18, outH / 14));

        int y = Math.max(44, outH / 7);
        g.setColor(new Color(26, 13, 171));
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, outH / 16)));
        y = drawWrappedReturn(g, title, 16, y, outW - 32, Math.max(14, outH / 16));
        y += 12;
        g.setColor(new Color(60, 64, 67));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, outH / 24)));
        drawWrappedReturn(g, desc, 16, y, outW - 32, Math.max(10, outH / 24));
        g.dispose();
        return scaleToRgb(img, outW, outH);
    }

    private byte[] renderMessagePage(int outW, int outH, String title, String message) {
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableNice(g);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, outW, outH);
        drawGoogleLogo(g, outW / 2, outH / 3, Math.max(22, outH / 9));
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, outH / 22)));
        drawWrapped(g, message, 16, outH / 2, outW - 32);
        g.dispose();
        return scaleToRgb(img, outW, outH);
    }

    private List<SearchResult> fetchGoogleResults(String query) throws IOException, InterruptedException {
        String url = searchUrl(query);
        String html;
        try {
            html = fetchHtml(url);
        } catch (Exception e) {
            plugin.getLogger().warning("Google fetch failed: " + e.getMessage());
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        Matcher m = RESULT_BLOCK.matcher(html);
        while (m.find() && results.size() < 8) {
            String href = m.group(2) != null ? m.group(2) : m.group(1);
            String titleHtml = m.group(3);
            if (href == null) {
                continue;
            }
            href = href.replace("&amp;", "&");
            if (href.startsWith("/url?q=")) {
                int amp = href.indexOf('&');
                href = amp > 0 ? href.substring(7, amp) : href.substring(7);
            }
            try {
                href = java.net.URLDecoder.decode(href, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            if (!href.startsWith("http")) {
                continue;
            }
            if (href.contains("google.com/") && !href.contains("/url")) {
                continue;
            }
            String title = cleanText(titleHtml);
            if (title.length() < 3 || title.equalsIgnoreCase("cached") || title.equalsIgnoreCase("similar")) {
                continue;
            }
            boolean dup = false;
            for (SearchResult existing : results) {
                if (existing.url.equals(href) || existing.title.equals(title)) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                continue;
            }
            results.add(new SearchResult(title, href, ""));
        }

        // Fallback: DuckDuckGo HTML if Google returned nothing useful
        if (results.isEmpty()) {
            results.addAll(fetchDdgResults(query));
        }
        return results;
    }

    private List<SearchResult> fetchDdgResults(String query) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String url = "https://html.duckduckgo.com/html/?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            String html = fetchHtml(url);
            Pattern p = Pattern.compile(
                    "class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Pattern snip = Pattern.compile(
                    "class=\"result__snippet\"[^>]*>(.*?)</(?:a|td|div)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(html);
            Matcher s = snip.matcher(html);
            while (m.find() && results.size() < 8) {
                String href = m.group(1).replace("&amp;", "&");
                // DDG wraps redirects
                int uddg = href.indexOf("uddg=");
                if (uddg >= 0) {
                    String enc = href.substring(uddg + 5);
                    int amp = enc.indexOf('&');
                    if (amp > 0) enc = enc.substring(0, amp);
                    href = java.net.URLDecoder.decode(enc, StandardCharsets.UTF_8);
                }
                String title = cleanText(m.group(2));
                String snippet = "";
                if (s.find()) {
                    snippet = cleanText(s.group(1));
                }
                if (title.length() > 2 && href.startsWith("http")) {
                    results.add(new SearchResult(title, href, snippet));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Search fallback failed: " + e.getMessage());
        }
        return results;
    }

    private String fetchHtml(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("google.timeout-seconds", 45)))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }

    private static String extractQuery(String url) {
        int q = url.indexOf("q=");
        if (q < 0) {
            return "";
        }
        String rest = url.substring(q + 2);
        int amp = rest.indexOf('&');
        if (amp >= 0) {
            rest = rest.substring(0, amp);
        }
        try {
            return java.net.URLDecoder.decode(rest, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return rest;
        }
    }

    private static String firstMatch(Pattern p, String html, String fallback) {
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : fallback;
    }

    private static String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        String t = STRIP_TAGS.matcher(raw).replaceAll("");
        t = t.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        return WHITESPACE.matcher(t).replaceAll(" ").trim();
    }

    private static void drawGoogleLogo(Graphics2D g, int cx, int cy, int size) {
        String[] letters = {"G", "o", "o", "g", "l", "e"};
        Color[] colors = {
                new Color(66, 133, 244),
                new Color(234, 67, 53),
                new Color(251, 188, 5),
                new Color(66, 133, 244),
                new Color(52, 168, 83),
                new Color(234, 67, 53)
        };
        g.setFont(new Font("SansSerif", Font.BOLD, size));
        int total = 0;
        for (String letter : letters) {
            total += g.getFontMetrics().stringWidth(letter);
        }
        int x = cx - total / 2;
        for (int i = 0; i < letters.length; i++) {
            g.setColor(colors[i]);
            g.drawString(letters[i], x, cy);
            x += g.getFontMetrics().stringWidth(letters[i]);
        }
    }

    private static void drawButton(Graphics2D g, int x, int y, String label) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int w = g.getFontMetrics().stringWidth(label) + 16;
        int h = 22;
        g.setColor(new Color(242, 242, 242));
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(new Color(116, 116, 116));
        g.drawRoundRect(x, y, w, h, 4, 4);
        g.setColor(new Color(60, 64, 67));
        g.drawString(label, x + 8, y + 15);
    }

    private static void enableNice(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static String truncate(String text, Graphics2D g, int maxW) {
        if (text == null) {
            return "";
        }
        if (g.getFontMetrics().stringWidth(text) <= maxW) {
            return text;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 0 && g.getFontMetrics().stringWidth(sb + ellipsis) > maxW) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb + ellipsis;
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxW) {
        drawWrappedReturn(g, text, x, y, maxW, g.getFont().getSize());
    }

    private static int drawWrappedReturn(Graphics2D g, String text, int x, int y, int maxW, int fontSize) {
        g.setFont(g.getFont().deriveFont((float) fontSize));
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
            yy += fm.getHeight();
        }
        return yy;
    }

    public static byte[] scaleToRgb(BufferedImage src, int outW, int outH) {
        BufferedImage scaled = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
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

    private record SearchResult(String title, String url, String snippet) {}
}
