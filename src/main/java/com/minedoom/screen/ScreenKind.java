package com.minedoom.screen;

public enum ScreenKind {
    DOOM,
    GOOGLE,
    /** Python / custom image display screens (16:9 terminal screens). */
    DISPLAY;

    public static ScreenKind fromString(String raw) {
        if (raw == null) {
            return DOOM;
        }
        try {
            return ScreenKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DOOM;
        }
    }
}
