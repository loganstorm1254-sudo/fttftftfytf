package com.minedoom.screen;

public enum ScreenKind {
    DOOM,
    GOOGLE;

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
