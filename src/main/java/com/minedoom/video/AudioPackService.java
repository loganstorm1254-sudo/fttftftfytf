package com.minedoom.video;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a tiny resource pack containing one OGG and uploads it so clients can play audio.
 */
public final class AudioPackService {

    public record Pack(Path zip, String url, String sha1Hex, String soundKey) {}

    private final JavaPlugin plugin;

    public AudioPackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Pack buildAndHost(Path oggFile, String soundId) throws Exception {
        int packFormat = plugin.getConfig().getInt("video.pack-format", 34);
        Path zip = oggFile.getParent().resolve("pack-" + soundId + ".zip");
        String soundKey = "minedoom." + soundId;

        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            write(zos, "pack.mcmeta", """
                    {
                      "pack": {
                        "pack_format": %d,
                        "description": "MineDoom video audio"
                      }
                    }
                    """.formatted(packFormat).getBytes(StandardCharsets.UTF_8));
            write(zos, "assets/minecraft/sounds.json", """
                    {
                      "%s": {
                        "sounds": [
                          { "name": "minedoom/%s", "stream": true }
                        ]
                      }
                    }
                    """.formatted(soundKey, soundId).getBytes(StandardCharsets.UTF_8));
            write(zos, "assets/minecraft/sounds/minedoom/" + soundId + ".ogg", Files.readAllBytes(oggFile));
        }

        byte[] bytes = Files.readAllBytes(zip);
        String sha1 = sha1Hex(bytes);
        String url = upload(zip, bytes);
        return new Pack(zip, url, sha1, soundKey);
    }

    private String upload(Path zip, byte[] bytes) throws Exception {
        String configured = plugin.getConfig().getString("video.pack-url", "");
        if (configured != null && !configured.isBlank()) {
            // Admin hosts the pack themselves at this base URL + filename
            String base = configured.endsWith("/") ? configured : configured + "/";
            return base + zip.getFileName();
        }

        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        // 1) catbox.moe
        try {
            String url = multipartUpload(http, "https://catbox.moe/user/api.php", zip, bytes,
                    "reqtype", "fileupload", "fileToUpload");
            if (url != null && url.startsWith("http")) {
                plugin.getLogger().info("Audio pack hosted at " + url);
                return url.trim();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("catbox upload failed: " + e.getMessage());
        }

        // 2) 0x0.st
        try {
            String url = multipartUpload(http, "https://0x0.st", zip, bytes, null, null, "file");
            if (url != null && url.startsWith("http")) {
                plugin.getLogger().info("Audio pack hosted at " + url.trim());
                return url.trim();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("0x0.st upload failed: " + e.getMessage());
        }

        // 3) Local HTTP fallback (needs an open port + public host in config)
        int port = plugin.getConfig().getInt("video.pack-http-port", 0);
        String publicHost = plugin.getConfig().getString("video.pack-public-host", "");
        if (port > 0 && publicHost != null && !publicHost.isBlank()) {
            LocalPackServer.ensure(plugin, port).offer(zip.getFileName().toString(), bytes);
            String url = "http://" + publicHost + ":" + port + "/" + zip.getFileName();
            plugin.getLogger().info("Audio pack served locally at " + url);
            return url;
        }

        throw new IOException(
                "Could not host audio resource pack (upload blocked). "
                        + "Set video.pack-url or video.pack-http-port + video.pack-public-host in config.yml");
    }

    private static String multipartUpload(
            HttpClient http,
            String endpoint,
            Path file,
            byte[] bytes,
            String extraName,
            String extraValue,
            String fileField
    ) throws Exception {
        String boundary = "----MineDoom" + System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (extraName != null) {
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("Content-Disposition: form-data; name=\"" + extraName + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write((extraValue + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                + file.getFileName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/zip\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "MineDoom/1.0")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }

    private static void write(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    private static String sha1Hex(byte[] data) throws Exception {
        byte[] dig = MessageDigest.getInstance("SHA-1").digest(data);
        return HexFormat.of().formatHex(dig);
    }
}
