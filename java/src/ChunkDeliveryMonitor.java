package me.p_menta.zb_game_perf;

import java.lang.reflect.Field;
import java.util.Collection;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoWorld;
import zombie.iso.WorldStreamer;
import zombie.network.GameClient;

public class ChunkDeliveryMonitor {
    public static boolean enabled = true;
    public static boolean showOnScreenHud = false;

    // HUD position
    public static int hudX = 20;
    public static int hudY = 40;
    public static final int HUD_W = 440;
    public static final int HUD_H = 26;
    private static boolean isDraggingHud = false;
    private static int dragOffX = 0;
    private static int dragOffY = 0;

    // Current perimeter metrics
    public static int totalChunks = 0;
    public static int loadedChunks = 0;
    public static double syncPct = 100.0;
    public static boolean isWaiting = false;

    // Timing metrics (in milliseconds)
    public static double currentWaitMs = 0.0;
    public static double lastDeliveryTimeMs = 0.0;
    public static double peakDeliveryTimeMs = 0.0;
    public static double avgDeliveryTimeMs = 0.0;
    public static long totalDeliveries = 0;
    private static double sumDeliveryTimeMs = 0.0;

    // Queue & network metrics
    public static int inFlightRequests = 0;
    public static int loadQueueSize = 0;

    private static long waitStartNano = 0;

    // Reflection cache for WorldStreamer.sentRequests
    private static Field sentRequestsField = null;
    private static boolean fieldLookupDone = false;

    // 60-second history of delivery wait times
    private static final double[] historyDeliveryMs = new double[60];
    private static long lastSampleSec = 0;

    public static void update() {
        if (!enabled) return;

        try {
            if (IsoWorld.instance == null || IsoWorld.instance.currentCell == null) return;
            IsoChunkMap[] maps = IsoWorld.instance.currentCell.chunkMap;
            if (maps == null || maps.length == 0 || maps[0] == null) return;

            IsoChunkMap cm = maps[0];
            int width = IsoChunkMap.chunkGridWidth;
            int total = width * width;
            totalChunks = total;

            int loaded = 0;
            for (int y = 0; y < width; y++) {
                for (int x = 0; x < width; x++) {
                    IsoChunk ch = cm.getChunk(x, y);
                    if (ch != null && ch.loaded) {
                        loaded++;
                    }
                }
            }
            loadedChunks = loaded;
            syncPct = (total > 0) ? (loaded * 100.0 / total) : 100.0;

            inFlightRequests = getInFlightRequests();
            try {
                loadQueueSize = IsoChunk.loadGridSquare.size();
            } catch (Throwable ignored) {
                loadQueueSize = 0;
            }

            long nowNano = System.nanoTime();
            if (loaded < total) {
                if (!isWaiting) {
                    isWaiting = true;
                    waitStartNano = nowNano;
                }
                currentWaitMs = (nowNano - waitStartNano) / 1_000_000.0;
                if (currentWaitMs > peakDeliveryTimeMs) {
                    peakDeliveryTimeMs = currentWaitMs;
                }
            } else {
                if (isWaiting) {
                    isWaiting = false;
                    lastDeliveryTimeMs = (nowNano - waitStartNano) / 1_000_000.0;
                    totalDeliveries++;
                    sumDeliveryTimeMs += lastDeliveryTimeMs;
                    avgDeliveryTimeMs = sumDeliveryTimeMs / totalDeliveries;
                    currentWaitMs = 0.0;
                }
            }

            // Sample 60-second history
            long sec = System.currentTimeMillis() / 1000L;
            if (sec != lastSampleSec) {
                if (lastSampleSec != 0) {
                    long diff = Math.min(sec - lastSampleSec, 60L);
                    for (long s = 1; s <= diff; s++) {
                        int idx = (int) ((lastSampleSec + s) % 60);
                        historyDeliveryMs[idx] = 0.0;
                    }
                }
                lastSampleSec = sec;
                historyDeliveryMs[(int) (sec % 60)] = isWaiting ? currentWaitMs : lastDeliveryTimeMs;
            }
        } catch (Throwable ignored) {
        }
    }

    private static int getInFlightRequests() {
        if (!GameClient.client || WorldStreamer.instance == null) return 0;
        if (!fieldLookupDone) {
            fieldLookupDone = true;
            try {
                sentRequestsField = WorldStreamer.class.getDeclaredField("sentRequests");
                sentRequestsField.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        if (sentRequestsField != null) {
            try {
                Object q = sentRequestsField.get(WorldStreamer.instance);
                if (q instanceof Collection<?>) {
                    return ((Collection<?>) q).size();
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    public static void handleHudMouse(int mx, int my, boolean mouseDown, boolean clicked) {
        if (!showOnScreenHud) return;

        // Close button [x]
        int closeX = hudX + HUD_W - 20;
        int closeY = hudY + 3;
        if (clicked && mx >= closeX && mx <= closeX + 16 && my >= closeY && my <= closeY + 16) {
            showOnScreenHud = false;
            return;
        }

        // Dragging HUD
        if (clicked && mx >= hudX && mx <= hudX + HUD_W && my >= hudY && my <= hudY + HUD_H) {
            isDraggingHud = true;
            dragOffX = mx - hudX;
            dragOffY = my - hudY;
        }

        if (mouseDown && isDraggingHud) {
            hudX = mx - dragOffX;
            hudY = my - dragOffY;
        } else if (!mouseDown) {
            isDraggingHud = false;
        }
    }

    public static void reset() {
        isWaiting = false;
        currentWaitMs = 0;
        lastDeliveryTimeMs = 0;
        peakDeliveryTimeMs = 0;
        avgDeliveryTimeMs = 0;
        totalDeliveries = 0;
        sumDeliveryTimeMs = 0;
        java.util.Arrays.fill(historyDeliveryMs, 0.0);
    }
}
