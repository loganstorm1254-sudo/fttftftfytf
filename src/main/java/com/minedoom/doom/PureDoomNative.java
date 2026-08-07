package com.minedoom.doom;

/**
 * JNI bindings to PureDOOM. Key codes match doom_key_t / doom_button_t.
 */
public final class PureDoomNative {

    private PureDoomNative() {}

    public static final int KEY_ESCAPE = 27;
    public static final int KEY_ENTER = 13;
    public static final int KEY_SPACE = 32;
    public static final int KEY_TAB = 9;
    public static final int KEY_CTRL = 0x80 + 0x1d;
    public static final int KEY_SHIFT = 0x80 + 0x36;
    public static final int KEY_ALT = 0x80 + 0x38;
    public static final int KEY_LEFT = 0xac;
    public static final int KEY_UP = 0xad;
    public static final int KEY_RIGHT = 0xae;
    public static final int KEY_DOWN = 0xaf;
    public static final int KEY_W = 'w';
    public static final int KEY_A = 'a';
    public static final int KEY_S = 's';
    public static final int KEY_D = 'd';
    public static final int KEY_E = 'e';
    public static final int KEY_Q = 'q';
    public static final int KEY_R = 'r';
    public static final int KEY_F = 'f';
    public static final int KEY_1 = '1';
    public static final int KEY_2 = '2';
    public static final int KEY_3 = '3';
    public static final int KEY_4 = '4';
    public static final int KEY_5 = '5';
    public static final int KEY_6 = '6';
    public static final int KEY_7 = '7';

    public static final int BUTTON_LEFT = 0;
    public static final int BUTTON_RIGHT = 1;
    public static final int BUTTON_MIDDLE = 2;

    public static native boolean nInit(String iwadPath, int width, int height);

    public static native void nShutdown();

    public static native void nUpdate();

    public static native void nForceUpdate();

    public static native int nGetWidth();

    public static native int nGetHeight();

    public static native byte[] nGetFramebufferRgb();

    public static native void nKeyDown(int key);

    public static native void nKeyUp(int key);

    public static native void nButtonDown(int button);

    public static native void nButtonUp(int button);

    public static native void nMouseMove(int dx, int dy);

    public static native boolean nIsInitialized();
}
