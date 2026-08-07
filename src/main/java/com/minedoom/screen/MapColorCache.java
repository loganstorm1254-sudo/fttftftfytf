package com.minedoom.screen;

import org.bukkit.map.MapPalette;

import java.util.Arrays;

/**
 * Fast RGB → Minecraft map palette conversion with a 15-bit color cache.
 */
@SuppressWarnings("deprecation")
public final class MapColorCache {

    private static final int SHIFT = 3; // 8-bit → 5-bit per channel
    private static final int SIZE = 1 << (3 * (8 - SHIFT)); // 32768
    private final byte[] cache = new byte[SIZE];

    public MapColorCache() {
        Arrays.fill(cache, (byte) -1);
    }

    public byte match(int r, int g, int b) {
        int rr = r >> SHIFT;
        int gg = g >> SHIFT;
        int bb = b >> SHIFT;
        int idx = (rr << (2 * (8 - SHIFT))) | (gg << (8 - SHIFT)) | bb;
        byte cached = cache[idx];
        if (cached != -1) {
            return cached;
        }
        byte mapped = MapPalette.matchColor(r & 0xFF, g & 0xFF, b & 0xFF);
        cache[idx] = mapped;
        return mapped;
    }

    public byte match(byte r, byte g, byte b) {
        return match(r & 0xFF, g & 0xFF, b & 0xFF);
    }
}
