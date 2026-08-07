package com.minedoom.doom;

import com.minedoom.MineDoomPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Owns the PureDOOM instance. Simulation runs on a dedicated thread;
 * framebuffer snapshots are published for the main-thread map renderer.
 */
public final class DoomEngine {

    private final MineDoomPlugin plugin;
    private final Path iwad;
    private final Object lock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private Thread doomThread;
    private BukkitTask pumpTask;
    private int width;
    private int height;
    private Consumer<byte[]> frameListener;

    public DoomEngine(MineDoomPlugin plugin, Path iwad) {
        this.plugin = plugin;
        this.iwad = iwad;
        this.width = plugin.getConfig().getInt("doom-width", 320);
        this.height = plugin.getConfig().getInt("doom-height", 200);
    }

    public synchronized void ensureStarted() {
        if (running.get()) {
            return;
        }
        if (!PureDoomNative.nIsInitialized()) {
            boolean ok = PureDoomNative.nInit(iwad.toAbsolutePath().toString(), width, height);
            if (!ok) {
                throw new IllegalStateException("PureDOOM init failed");
            }
            width = PureDoomNative.nGetWidth();
            height = PureDoomNative.nGetHeight();
            plugin.getLogger().info("PureDOOM initialized " + width + "x" + height + " iwad=" + iwad.getFileName());
        }
        running.set(true);
        doomThread = new Thread(this::loop, "MineDoom-PureDOOM");
        doomThread.setDaemon(true);
        doomThread.start();
    }

    private void loop() {
        while (running.get()) {
            try {
                synchronized (lock) {
                    PureDoomNative.nUpdate();
                    byte[] rgb = PureDoomNative.nGetFramebufferRgb();
                    if (rgb != null) {
                        latestFrame.set(rgb);
                        Consumer<byte[]> listener = frameListener;
                        if (listener != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> listener.accept(rgb));
                        }
                    }
                }
                Thread.sleep(28); // ~35 FPS doom_update pacing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                plugin.getLogger().warning("Doom tick error: " + t.getMessage());
            }
        }
    }

    public void setFrameListener(Consumer<byte[]> listener) {
        this.frameListener = listener;
    }

    public byte[] getLatestFrame() {
        return latestFrame.get();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void keyDown(int key) {
        ensureStarted();
        synchronized (lock) {
            PureDoomNative.nKeyDown(key);
        }
    }

    public void keyUp(int key) {
        if (!running.get() && !PureDoomNative.nIsInitialized()) {
            return;
        }
        synchronized (lock) {
            PureDoomNative.nKeyUp(key);
        }
    }

    public void buttonDown(int button) {
        ensureStarted();
        synchronized (lock) {
            PureDoomNative.nButtonDown(button);
        }
    }

    public void buttonUp(int button) {
        if (!PureDoomNative.nIsInitialized()) {
            return;
        }
        synchronized (lock) {
            PureDoomNative.nButtonUp(button);
        }
    }

    public void mouseMove(int dx, int dy) {
        ensureStarted();
        synchronized (lock) {
            PureDoomNative.nMouseMove(dx, dy);
        }
    }

    public boolean isRunning() {
        return running.get() || PureDoomNative.nIsInitialized();
    }

    public synchronized void shutdown() {
        running.set(false);
        if (doomThread != null) {
            doomThread.interrupt();
            try {
                doomThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            doomThread = null;
        }
        if (pumpTask != null) {
            pumpTask.cancel();
            pumpTask = null;
        }
        synchronized (lock) {
            if (PureDoomNative.nIsInitialized()) {
                PureDoomNative.nShutdown();
            }
        }
    }
}
