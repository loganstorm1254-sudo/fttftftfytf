package com.minedoom.screen;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

/**
 * Renders one 128x128 tile of the scaled DOOM framebuffer onto a map.
 * Non-contextual so everyone looking at the item-frame screen can see it.
 */
@SuppressWarnings("deprecation")
public final class DoomMapRenderer extends MapRenderer {

    private final DoomScreen screen;
    private final int tileX;
    private final int tileY;
    private final MapColorCache colorCache;
    private final byte[] pixels = new byte[128 * 128];
    private volatile boolean dirty = true;

    public DoomMapRenderer(DoomScreen screen, int tileX, int tileY, MapColorCache colorCache) {
        super(false); // MUST be non-contextual or item-frame viewers see nothing
        this.screen = screen;
        this.tileX = tileX;
        this.tileY = tileY;
        this.colorCache = colorCache;
        // Fill with dark gray so the panel is visible before the first Doom frame
        byte fill = MapPalette.matchColor(20, 20, 20);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = fill;
        }
    }

    public void updateFromDoom(byte[] doomRgb, int doomW, int doomH) {
        int screenW = screen.getPixelWidth();
        int screenH = screen.getPixelHeight();
        int baseX = tileX * 128;
        int baseY = tileY * 128;

        for (int ly = 0; ly < 128; ly++) {
            int sy = baseY + ly;
            int srcY = (int) ((long) sy * doomH / screenH);
            if (srcY < 0) srcY = 0;
            if (srcY >= doomH) srcY = doomH - 1;
            for (int lx = 0; lx < 128; lx++) {
                int sx = baseX + lx;
                int srcX = (int) ((long) sx * doomW / screenW);
                if (srcX < 0) srcX = 0;
                if (srcX >= doomW) srcX = doomW - 1;
                int srcIndex = (srcY * doomW + srcX) * 3;
                byte r = doomRgb[srcIndex];
                byte g = doomRgb[srcIndex + 1];
                byte b = doomRgb[srcIndex + 2];
                pixels[ly * 128 + lx] = colorCache.match(r, g, b);
            }
        }
        dirty = true;
    }

    @Override
    public boolean isExplorerMap() {
        return false;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (!dirty) {
            return;
        }
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                canvas.setPixel(x, y, pixels[y * 128 + x]);
            }
        }
        dirty = false;
    }
}
