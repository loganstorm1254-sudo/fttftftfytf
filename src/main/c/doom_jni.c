/*
 * JNI bridge for PureDOOM — MineDoom plugin
 * GPL-2.0-only (same as PureDOOM / Doom source)
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/time.h>

#define DOOM_IMPLEMENTATION
#define DOOM_IMPLEMENT_PRINT
#define DOOM_IMPLEMENT_MALLOC
#define DOOM_IMPLEMENT_FILE_IO
#define DOOM_IMPLEMENT_GETTIME
#define DOOM_IMPLEMENT_EXIT
/* We provide a custom getenv that points DOOMWADDIR at our IWAD folder */
#include "PureDOOM.h"

static int g_width = 320;
static int g_height = 200;
static int g_initialized = 0;
static char g_iwad_dir[1024];
static char g_home_dir[1024];

static void jni_print(const char* str) {
    fputs(str, stdout);
    fflush(stdout);
}

static void jni_exit(int code) {
    fprintf(stderr, "[MineDoom] doom_exit(%d) ignored\n", code);
}

static char* jni_getenv(const char* var) {
    if (!var) {
        return NULL;
    }
    if (strcmp(var, "DOOMWADDIR") == 0) {
        return g_iwad_dir;
    }
    if (strcmp(var, "HOME") == 0) {
        if (g_home_dir[0]) {
            return g_home_dir;
        }
        char* home = getenv("HOME");
        return home ? home : (char*)".";
    }
    return getenv(var);
}

static void dirname_of(const char* path, char* out, size_t outSize) {
    strncpy(out, path, outSize - 1);
    out[outSize - 1] = '\0';
    char* slash = strrchr(out, '/');
    if (slash) {
        *slash = '\0';
    } else {
        strncpy(out, ".", outSize - 1);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_minedoom_doom_PureDoomNative_nInit(JNIEnv* env, jclass cls, jstring iwadPath, jint width, jint height) {
    (void)cls;
    if (g_initialized) {
        return JNI_TRUE;
    }

    const char* path = (*env)->GetStringUTFChars(env, iwadPath, NULL);
    if (!path) {
        return JNI_FALSE;
    }

    dirname_of(path, g_iwad_dir, sizeof(g_iwad_dir));

    /* Ensure the file is named doom1.wad / doom.wad / freedoom1.wad as IdentifyVersion expects */
    {
        char cmd_check[1200];
        snprintf(cmd_check, sizeof(cmd_check), "%s/doom1.wad", g_iwad_dir);
        FILE* f = fopen(cmd_check, "rb");
        if (!f) {
            /* try to use whatever was passed by copying symlink-style via reading — just reopen path */
            f = fopen(path, "rb");
            if (!f) {
                fprintf(stderr, "[MineDoom] Cannot open IWAD: %s\n", path);
                (*env)->ReleaseStringUTFChars(env, iwadPath, path);
                return JNI_FALSE;
            }
            fclose(f);
            /* If path is not doom1.wad in that dir, IdentifyVersion won't see it unless named correctly.
               Copy/link by writing doom1.wad next to it if missing — handled in Java. */
        } else {
            fclose(f);
        }
    }

    const char* home = getenv("HOME");
    if (home) {
        strncpy(g_home_dir, home, sizeof(g_home_dir) - 1);
    } else {
        strncpy(g_home_dir, g_iwad_dir, sizeof(g_home_dir) - 1);
    }

    (*env)->ReleaseStringUTFChars(env, iwadPath, path);

    g_width = width > 0 ? width : 320;
    g_height = height > 0 ? height : 200;

    doom_set_print(jni_print);
    doom_set_exit(jni_exit);
    doom_set_getenv(jni_getenv);

    /* Modern WASD defaults */
    doom_set_default_int("key_up", DOOM_KEY_W);
    doom_set_default_int("key_down", DOOM_KEY_S);
    doom_set_default_int("key_strafeleft", DOOM_KEY_A);
    doom_set_default_int("key_straferight", DOOM_KEY_D);
    doom_set_default_int("key_use", DOOM_KEY_E);
    doom_set_default_int("key_fire", DOOM_KEY_CTRL);
    doom_set_default_int("mouse_move", 0);
    doom_set_default_int("use_mouse", 1);
    doom_set_default_int("mouse_sensitivity", 5);

    doom_set_resolution(g_width, g_height);

    /* IdentifyVersion uses DOOMWADDIR; warp straight into E1M1 */
    char* argv[] = {
        (char*)"minedoom",
        (char*)"-nosound",
        (char*)"-nomusic",
        (char*)"-nosfx",
        (char*)"-warp",
        (char*)"1",
        (char*)"1",
        (char*)"-skill",
        (char*)"3",
        NULL
    };
    int argc = 9;

    doom_init(argc, argv, DOOM_FLAG_MENU_DARKEN_BG | DOOM_FLAG_HIDE_SOUND_OPTIONS | DOOM_FLAG_HIDE_MUSIC_OPTIONS);
    g_initialized = 1;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nShutdown(JNIEnv* env, jclass cls) {
    (void)env;
    (void)cls;
    g_initialized = 0;
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nUpdate(JNIEnv* env, jclass cls) {
    (void)env;
    (void)cls;
    if (g_initialized) {
        doom_update();
    }
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nForceUpdate(JNIEnv* env, jclass cls) {
    (void)env;
    (void)cls;
    if (g_initialized) {
        doom_force_update();
    }
}

JNIEXPORT jint JNICALL
Java_com_minedoom_doom_PureDoomNative_nGetWidth(JNIEnv* env, jclass cls) {
    (void)env;
    (void)cls;
    return g_width;
}

JNIEXPORT jint JNICALL
Java_com_minedoom_doom_PureDoomNative_nGetHeight(JNIEnv* env, jclass cls) {
    (void)env;
    (void)cls;
    return g_height;
}

JNIEXPORT jbyteArray JNICALL
Java_com_minedoom_doom_PureDoomNative_nGetFramebufferRgb(JNIEnv* env, jclass cls) {
    (void)cls;
    if (!g_initialized) {
        return NULL;
    }
    const unsigned char* fb = doom_get_framebuffer(3);
    if (!fb) {
        return NULL;
    }
    int size = g_width * g_height * 3;
    jbyteArray arr = (*env)->NewByteArray(env, size);
    if (!arr) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, arr, 0, size, (const jbyte*)fb);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nKeyDown(JNIEnv* env, jclass cls, jint key) {
    (void)env;
    (void)cls;
    if (g_initialized) {
        doom_key_down((doom_key_t)key);
    }
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nKeyUp(JNIEnv* env, jclass cls, jint key) {
    (void)env;
    (void)cls;
    if (g_initialized) {
        doom_key_up((doom_key_t)key);
    }
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nButtonDown(JNIEnv* env, jclass cls, jint button) {
    (void)env;
    (void)cls;
    if (g_initialized) {
        doom_button_down((doom_button_t)button);
    }
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nButtonUp(JNIEnv* env, jclass cls, jint button) {
    (void)env;
    (void)cls;
    if (g_initialized) {
        doom_button_up((doom_button_t)button);
    }
}

JNIEXPORT void JNICALL
Java_com_minedoom_doom_PureDoomNative_nMouseMove(JNIEnv* env, jclass cls, jint dx, jint dy) {
    (void)env;
    (void)cls;
    if (g_initialized) {
        doom_mouse_move(dx, dy);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_minedoom_doom_PureDoomNative_nIsInitialized(JNIEnv* env, jclass cls) {
    (void)env;
    (void)cls;
    return g_initialized ? JNI_TRUE : JNI_FALSE;
}
