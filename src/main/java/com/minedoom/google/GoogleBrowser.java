package com.minedoom.google;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a Google-styled UI on map screens without Chrome.
 * Search uses public JSON APIs (DuckDuckGo + Wikipedia) because Google HTML
 * blocks shared hosts like MineKeep after ~1 request.
 */
public final class GoogleBrowser {

    private static final Pattern OG_IMAGE = Pattern.compile(
            "<meta[^>]*(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]*content=[\"']([^\"']+)[\"']"
                    + "|<meta[^>]*content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_DESC = Pattern.compile(
            "<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRIP_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DDG_TOPIC = Pattern.compile(
            "\"(?:Text|Result)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"[\\s\\S]*?\"FirstURL\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DDG_ABSTRACT = Pattern.compile(
            "\"Abstract(?:Text)?\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern DDG_ABSTRACT_URL = Pattern.compile(
            "\"AbstractURL\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern DDG_HEADING = Pattern.compile(
            "\"Heading\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final JavaPlugin plugin;
    private final HttpClient http;
    private final Map<String, List<SearchResult>> cache = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest) {
            return size() > 32;
        }
    };
    private final Map<String, BufferedImage> shotCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 16;
        }
    };
    private volatile String currentUrl = "https://www.google.com/";
    private volatile String lastQuery = "";
    private volatile String lastError = "";

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
        url = normalizeUrl(url);
        currentUrl = url;
        String lower = url.toLowerCase();

        if (lower.contains("google.com/") && !lower.contains("/search") && !looksLikeDirectMedia(lower)) {
            return renderHome(outW, outH);
        }
        if ((lower.contains("google.com/search") || (lower.contains("q=") && lower.contains("google.")))
                && !looksLikeDirectMedia(lower)) {
            String query = extractQuery(url);
            lastQuery = query;
            return renderSearch(query, outW, outH);
        }
        if (looksLikeDirectMedia(lower)) {
            return renderDirectMedia(url, outW, outH);
        }
        return renderExternalPage(url, outW, outH);
    }

    public String normalizeUrl(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Empty URL");
        }
        String url = raw.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "");
        // Strip common chat wrappers
        if ((url.startsWith("<") && url.endsWith(">")) || (url.startsWith("[") && url.endsWith("]"))) {
            url = url.substring(1, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        url = fixMalformedPercentEncoding(url);
        // Validate early with a clear error
        parseUri(url);
        return url;
    }

    /** Read a full URL from a held written/writable book (for links longer than chat allows). */
    public String readUrlFromHeldBook(org.bukkit.entity.Player player) {
        org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !(hand.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta book)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String page : book.getPages()) {
            sb.append(page);
        }
        String text = sb.toString().replace('\n', ' ').replace('\r', ' ').trim();
        // Prefer the first http(s) URL on the pages
        Matcher m = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            return m.group().replaceAll("[\\])>,.;]+$", "");
        }
        text = text.replace(" ", "");
        return text.isBlank() ? null : text;
    }

    public static boolean looksLikeDirectMedia(String lowerUrl) {
        if (lowerUrl == null) {
            return false;
        }
        String u = lowerUrl.toLowerCase();
        return u.contains(".mp4")
                || u.contains(".webm")
                || u.contains(".mkv")
                || u.contains(".mov")
                || u.contains(".m3u8")
                || u.contains(".mp3")
                || u.contains("cloudfront.net/")
                || (u.contains("/download/") && u.contains("archive.org"))
                || (u.contains("/file/") && u.contains("archive.org"));
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

    private byte[] renderSearch(String query, int outW, int outH) {
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
            String err = lastError.isBlank() ? "No results for this query." : lastError;
            y = drawWrappedReturn(g, err, 16, y, outW - 32, bodySize);
            g.drawString("Try another query — /google search <words>", 16, y + 8);
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
        // Real page preview via remote screenshot (MineKeep can't run Chrome)
        BufferedImage shot = fetchRemoteScreenshot(url, Math.max(640, Math.min(1280, outW * 2)));
        if (shot != null) {
            return composeBrowserView(url, shot, outW, outH, null);
        }

        String html = "";
        try {
            html = fetchHtml(url);
        } catch (Exception e) {
            plugin.getLogger().warning("Page fetch failed for " + url + ": " + e.getMessage());
        }

        BufferedImage og = fetchOgImage(html);
        if (og != null) {
            String title = cleanText(firstMatch(TITLE_TAG, html, url));
            return composeBrowserView(url, og, outW, outH, title);
        }

        String title = cleanText(firstMatch(TITLE_TAG, html, url));
        String desc = cleanText(firstMatch(META_DESC, html, ""));
        if (desc.isBlank() && !html.isBlank()) {
            desc = cleanText(STRIP_TAGS.matcher(html).replaceAll(" "));
            if (desc.length() > 400) {
                desc = desc.substring(0, 400) + "…";
            }
        }
        if (desc.isBlank()) {
            desc = "Could not capture this page (screenshot service busy). Try /google refresh.";
        }

        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableNice(g);
        g.setColor(new Color(32, 33, 36));
        g.fillRect(0, 0, outW, outH);
        int barH = Math.max(28, outH / 12);
        g.setColor(new Color(48, 49, 52));
        g.fillRect(0, 0, outW, barH);
        g.setColor(new Color(232, 234, 237));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, barH / 2)));
        g.drawString(truncate(url, g, outW - 24), 12, barH * 2 / 3);
        int y = barH + Math.max(24, outH / 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, outH / 16)));
        y = drawWrappedReturn(g, title.isBlank() ? url : title, 16, y, outW - 32, Math.max(14, outH / 16));
        y += 12;
        g.setColor(new Color(189, 193, 198));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, outH / 24)));
        drawWrappedReturn(g, desc, 16, y, outW - 32, Math.max(10, outH / 24));
        g.dispose();
        return scaleToRgb(img, outW, outH);
    }

    /** Direct .mp4 / CloudFront file links — can't play video on maps; show a player card + try screenshot. */
    private byte[] renderDirectMedia(String url, int outW, int outH) throws IOException, InterruptedException {
        BufferedImage shot = fetchRemoteScreenshot(url, Math.max(640, Math.min(1280, outW * 2)));
        if (shot != null) {
            return composeBrowserView(url, shot, outW, outH, "Video file");
        }

        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableNice(g);
        g.setColor(new Color(15, 15, 15));
        g.fillRect(0, 0, outW, outH);

        int barH = Math.max(22, outH / 14);
        g.setColor(new Color(48, 49, 52));
        g.fillRect(0, 0, outW, barH);
        g.setColor(new Color(232, 234, 237));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, barH / 2)));
        g.drawString(truncate(url, g, outW - 24), 12, barH * 2 / 3);

        // Fake player stage
        int stagePad = Math.max(16, outW / 20);
        int stageY = barH + stagePad;
        int stageH = outH - barH - stagePad * 2 - Math.max(36, outH / 10);
        g.setColor(new Color(30, 30, 30));
        g.fillRoundRect(stagePad, stageY, outW - stagePad * 2, stageH, 12, 12);

        // Play button
        int cx = outW / 2;
        int cy = stageY + stageH / 2;
        int r = Math.max(28, Math.min(outW, stageH) / 8);
        g.setColor(new Color(255, 255, 255, 220));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(new Color(20, 20, 20));
        int[] xs = {cx - r / 4, cx - r / 4, cx + r / 2};
        int[] ys = {cy - r / 2, cy + r / 2, cy};
        g.fillPolygon(xs, ys, 3);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, outH / 18)));
        String heading = "Video file";
        int tw = g.getFontMetrics().stringWidth(heading);
        g.drawString(heading, (outW - tw) / 2, stageY + stageH + Math.max(22, outH / 22));

        g.setColor(new Color(180, 180, 180));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, outH / 28)));
        String tip = "Maps can't play video — open the archive.org page instead of the .mp4 link";
        drawWrappedReturn(g, tip, stagePad, stageY + stageH + Math.max(36, outH / 16), outW - stagePad * 2, Math.max(10, outH / 28));
        g.dispose();
        return scaleToRgb(img, outW, outH);
    }

    private byte[] composeBrowserView(String url, BufferedImage page, int outW, int outH, String caption) {
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableNice(g);
        int barH = Math.max(22, outH / 14);
        g.setColor(new Color(48, 49, 52));
        g.fillRect(0, 0, outW, barH);
        g.setColor(new Color(60, 64, 67));
        int pad = Math.max(4, barH / 5);
        g.fillRoundRect(pad * 2, pad, outW - pad * 4, barH - pad * 2, barH, barH);
        g.setColor(new Color(232, 234, 237));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, barH / 2)));
        g.drawString(truncate(url, g, outW - pad * 6), pad * 3, barH - pad - 2);

        int contentH = outH - barH;
        g.setColor(Color.WHITE);
        g.fillRect(0, barH, outW, contentH);
        // cover-fit the screenshot into the content area
        double scale = Math.max((double) outW / page.getWidth(), (double) contentH / page.getHeight());
        int dw = (int) Math.round(page.getWidth() * scale);
        int dh = (int) Math.round(page.getHeight() * scale);
        int dx = (outW - dw) / 2;
        int dy = barH + (contentH - dh) / 2;
        g.drawImage(page, dx, dy, dw, dh, null);

        if (caption != null && !caption.isBlank()) {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, outH - Math.max(28, outH / 12), outW, Math.max(28, outH / 12));
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, outH / 22)));
            g.drawString(truncate(caption, g, outW - 24), 12, outH - Math.max(10, outH / 28));
        }
        g.dispose();
        return scaleToRgb(img, outW, outH);
    }

    private BufferedImage fetchRemoteScreenshot(String pageUrl, int width) {
        synchronized (shotCache) {
            BufferedImage cached = shotCache.get(pageUrl);
            if (cached != null) {
                return cached;
            }
        }

        // Encode the target URL so CloudFront %2F / signed params don't break URI.create
        String encodedTarget = URLEncoder.encode(pageUrl, StandardCharsets.UTF_8).replace("+", "%20");
        List<String> endpoints = List.of(
                "https://image.thum.io/get/width/" + width + "/noanimate/" + encodedTarget,
                "https://image.thum.io/get/width/" + width + "/" + encodedTarget,
                "https://s0.wp.com/mshots/v1/" + encodedTarget + "?w=" + width
        );

        for (String endpoint : endpoints) {
            try {
                byte[] bytes = fetchBytes(endpoint);
                if (bytes.length < 2000) {
                    continue; // placeholder / error html
                }
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img == null || img.getWidth() < 64 || img.getHeight() < 64) {
                    continue;
                }
                // mshots sometimes returns a tiny "generating" placeholder
                if (img.getWidth() < 200 && img.getHeight() < 200) {
                    continue;
                }
                synchronized (shotCache) {
                    shotCache.put(pageUrl, img);
                }
                plugin.getLogger().info("Captured page screenshot via " + safeHost(endpoint)
                        + " (" + img.getWidth() + "x" + img.getHeight() + ")");
                return img;
            } catch (Exception e) {
                plugin.getLogger().warning("Screenshot failed (" + safeHost(endpoint) + "): " + e.getMessage());
            }
        }
        return null;
    }

    private BufferedImage fetchOgImage(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = OG_IMAGE.matcher(html);
        if (!m.find()) {
            return null;
        }
        String src = m.group(1) != null ? m.group(1) : m.group(2);
        if (src == null || src.isBlank()) {
            return null;
        }
        src = src.replace("&amp;", "&").trim();
        if (src.startsWith("//")) {
            src = "https:" + src;
        }
        if (!src.startsWith("http")) {
            return null;
        }
        try {
            byte[] bytes = fetchBytes(src);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null && img.getWidth() >= 64) {
                return img;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("og:image fetch failed: " + e.getMessage());
        }
        return null;
    }

    private static String safeHost(String url) {
        try {
            return parseUri(url).getHost();
        } catch (Exception e) {
            return "remote";
        }
    }

    private byte[] fetchBytes(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(parseUri(url))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("google.timeout-seconds", 45)))
                .header("User-Agent", "MineDoom/1.0 (Minecraft plugin; +https://github.com/loganstorm1254-sudo/fttftftfytf)")
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() >= 400) {
            throw new IOException("HTTP " + res.statusCode());
        }
        return res.body();
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

    private List<SearchResult> fetchGoogleResults(String query) {
        String key = query.trim().toLowerCase();
        synchronized (cache) {
            List<SearchResult> cached = cache.get(key);
            if (cached != null && !cached.isEmpty()) {
                lastError = "";
                return cached;
            }
        }

        List<SearchResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 1) DuckDuckGo Instant Answer API — works on shared hosts (no Google scrape)
        try {
            results.addAll(fetchDdgApi(query));
        } catch (Exception e) {
            errors.add("DDG API: " + e.getMessage());
            plugin.getLogger().warning("DDG API search failed: " + e.getMessage());
        }

        // 2) Wikipedia OpenSearch — very reliable JSON API
        if (results.size() < 6) {
            try {
                results.addAll(fetchWikipedia(query));
            } catch (Exception e) {
                errors.add("Wiki: " + e.getMessage());
                plugin.getLogger().warning("Wikipedia search failed: " + e.getMessage());
            }
        }

        // 3) DuckDuckGo HTML scrape as last resort
        if (results.isEmpty()) {
            try {
                results.addAll(fetchDdgHtml(query));
            } catch (Exception e) {
                errors.add("DDG HTML: " + e.getMessage());
                plugin.getLogger().warning("DDG HTML search failed: " + e.getMessage());
            }
        }

        results = dedupe(results);
        if (results.isEmpty()) {
            lastError = errors.isEmpty()
                    ? "No results (server outbound HTTP may be blocked)."
                    : "Search failed: " + String.join(" · ", errors);
        } else {
            lastError = "";
            synchronized (cache) {
                cache.put(key, List.copyOf(results));
            }
        }
        return results;
    }

    private List<SearchResult> fetchDdgApi(String query) throws IOException, InterruptedException {
        String url = "https://api.duckduckgo.com/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=json&no_html=1&skip_disambig=1";
        String json = fetchText(url, "application/json");
        List<SearchResult> results = new ArrayList<>();

        String heading = unescapeJson(firstMatch(DDG_HEADING, json, ""));
        String abs = unescapeJson(firstMatch(DDG_ABSTRACT, json, ""));
        String absUrl = unescapeJson(firstMatch(DDG_ABSTRACT_URL, json, ""));
        if (!abs.isBlank() && absUrl.startsWith("http")) {
            results.add(new SearchResult(
                    heading.isBlank() ? truncatePlain(abs, 80) : heading,
                    absUrl,
                    abs));
        }

        Matcher m = DDG_TOPIC.matcher(json);
        while (m.find() && results.size() < 8) {
            String text = unescapeJson(m.group(1));
            String link = unescapeJson(m.group(2));
            if (!link.startsWith("http") || text.isBlank()) {
                continue;
            }
            String title = text;
            String snippet = "";
            int dash = text.indexOf(" - ");
            if (dash > 0) {
                title = text.substring(0, dash).trim();
                snippet = text.substring(dash + 3).trim();
            }
            results.add(new SearchResult(title, link, snippet));
        }
        return results;
    }

    private List<SearchResult> fetchWikipedia(String query) throws IOException, InterruptedException {
        String url = "https://en.wikipedia.org/w/api.php?action=opensearch&limit=8&namespace=0&format=json&search="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String json = fetchText(url, "application/json");
        List<SearchResult> results = new ArrayList<>();

        // OpenSearch JSON: [query, [titles], [descs], [urls]]
        List<String> titles = parseJsonStringArray(json, 1);
        List<String> descs = parseJsonStringArray(json, 2);
        List<String> urls = parseJsonStringArray(json, 3);
        int n = Math.min(titles.size(), urls.size());
        for (int i = 0; i < n && results.size() < 8; i++) {
            String title = titles.get(i);
            String link = urls.get(i);
            String snippet = i < descs.size() ? descs.get(i) : "";
            if (title.isBlank() || !link.startsWith("http")) {
                continue;
            }
            results.add(new SearchResult(title, link, snippet));
        }
        return results;
    }

    private List<SearchResult> fetchDdgHtml(String query) throws IOException, InterruptedException {
        List<SearchResult> results = new ArrayList<>();
        String url = "https://html.duckduckgo.com/html/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String html = fetchText(url, "text/html,application/xhtml+xml");
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
        return results;
    }

    private static List<SearchResult> dedupe(List<SearchResult> in) {
        List<SearchResult> out = new ArrayList<>();
        for (SearchResult r : in) {
            boolean dup = false;
            for (SearchResult e : out) {
                if (e.url.equalsIgnoreCase(r.url) || e.title.equalsIgnoreCase(r.title)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                out.add(r);
            }
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    /** Pull the Nth top-level JSON array of strings from an OpenSearch-style payload. */
    private static List<String> parseJsonStringArray(String json, int arrayIndex) {
        List<String> arrays = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '[') {
                if (depth == 1) {
                    start = i;
                }
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 1 && start >= 0) {
                    arrays.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        if (arrayIndex >= arrays.size()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher m = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(arrays.get(arrayIndex));
        while (m.find()) {
            values.add(unescapeJson(m.group(1)));
        }
        return values;
    }

    private static String unescapeJson(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            try {
                                out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                out.append('u');
                            }
                        } else {
                            out.append('u');
                        }
                    }
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String truncatePlain(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    private String fetchText(String url, String accept) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(parseUri(url))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("google.timeout-seconds", 45)))
                .header("User-Agent", "MineDoom/1.0 (Minecraft plugin; +https://github.com/loganstorm1254-sudo/fttftftfytf)")
                .header("Accept", accept)
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }

    private String fetchHtml(String url) throws IOException, InterruptedException {
        return fetchText(url, "text/html,application/xhtml+xml");
    }

    /** Lenient URI parse — fixes truncated %XX from Minecraft's 256-char command limit. */
    static URI parseUri(String raw) {
        String url = fixMalformedPercentEncoding(raw);
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Bad URL (often truncated by chat — put the full link in a book and run /google go): "
                            + shortErr(e.getMessage()),
                    e);
        }
    }

    static String fixMalformedPercentEncoding(String url) {
        StringBuilder sb = new StringBuilder(url.length() + 8);
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '%') {
                if (i + 2 < url.length() && isHex(url.charAt(i + 1)) && isHex(url.charAt(i + 2))) {
                    sb.append('%').append(url.charAt(i + 1)).append(url.charAt(i + 2));
                    i += 2;
                } else {
                    // Lone/truncated % from a cut-off command → encode as %25
                    sb.append("%25");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static String shortErr(String msg) {
        if (msg == null) {
            return "invalid";
        }
        return msg.length() > 80 ? msg.substring(0, 77) + "…" : msg;
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
            return fixMalformedPercentEncoding(rest).replace("%25", "%");
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
