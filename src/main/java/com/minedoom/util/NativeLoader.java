package com.minedoom.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NativeLoader {

    private NativeLoader() {}

    public static void extractAndLoad(JavaPlugin plugin, Path dataFolder) throws IOException {
        Files.createDirectories(dataFolder.resolve("native"));
        Path so = dataFolder.resolve("native/libpuredoom.so");
        try (InputStream in = plugin.getResource("native/libpuredoom.so")) {
            if (in == null) {
                throw new IOException("native/libpuredoom.so missing from plugin jar");
            }
            Files.copy(in, so, StandardCopyOption.REPLACE_EXISTING);
        }
        System.load(so.toAbsolutePath().toString());
        plugin.getLogger().info("Loaded PureDOOM native library from " + so);
    }

    public static Path ensureIwad(JavaPlugin plugin, Path dataFolder) {
        try {
            Files.createDirectories(dataFolder.resolve("wads"));
            Path iwad = dataFolder.resolve("wads/doom1.wad");
            if (!Files.exists(iwad)) {
                try (InputStream in = plugin.getResource("wads/doom1.wad")) {
                    if (in == null) {
                        throw new IOException("wads/doom1.wad missing from plugin jar");
                    }
                    Files.copy(in, iwad);
                }
                plugin.getLogger().info("Extracted shareware doom1.wad");
            }
            // Custom IWADs: PureDOOM IdentifyVersion looks for specific filenames in DOOMWADDIR
            Path customDoom = dataFolder.resolve("wads/doom.wad");
            Path freedoom = dataFolder.resolve("wads/freedoom1.wad");
            if (Files.exists(customDoom)) {
                plugin.getLogger().info("Found doom.wad (registered) in wads/");
            } else if (Files.exists(freedoom)) {
                // Present as doom1.wad name for shareware detection if no doom1 exists — keep both
                plugin.getLogger().info("Found freedoom1.wad — rename/copy to doom1.wad or doom.wad for use");
            }
            // Always return doom1.wad path; native layer uses its parent as DOOMWADDIR
            return iwad;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare IWAD", e);
        }
    }
}
