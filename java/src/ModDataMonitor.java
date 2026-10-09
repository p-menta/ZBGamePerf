package me.p_menta.zb_game_perf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import zombie.GameWindow;

public class ModDataMonitor {
    public static class ModDataEntry {
        public final String tag;
        public final boolean isTx; // true = TX (sent), false = RX (received)
        
        public long totalCount = 0;
        public long totalBytes = 0;
        public int lastBytes = 0;
        public long lastTimestampMs = 0;
        
        // 60 1-second buckets for accurate rate calculation
        private final int[] secondCounts = new int[60];
        private final long[] secondBytes = new long[60];
        private long lastSecond = 0;
        
        public ModDataEntry(String tag, boolean isTx) {
            this.tag = tag;
            this.isTx = isTx;
        }
        
        public synchronized void record(int bytes) {
            long now = System.currentTimeMillis();
            long sec = now / 1000L;
            
            if (sec != lastSecond) {
                long diff = Math.min(sec - lastSecond, 60L);
                for (long s = 1; s <= diff; s++) {
                    int idx = (int)((lastSecond + s) % 60);
                    secondCounts[idx] = 0;
                    secondBytes[idx] = 0;
                }
                lastSecond = sec;
            }
            
            int idx = (int)(sec % 60);
            secondCounts[idx]++;
            secondBytes[idx] += bytes;
            
            totalCount++;
            totalBytes += bytes;
            lastBytes = bytes;
            lastTimestampMs = now;
        }
        
        public synchronized int getCountLastMinute() {
            clearOld(System.currentTimeMillis() / 1000L);
            int sum = 0;
            for (int c : secondCounts) {
                sum += c;
            }
            return sum;
        }

        public synchronized double getRatePerSecond() {
            long sec = System.currentTimeMillis() / 1000L;
            clearOld(sec);
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += secondCounts[(int)((sec - i) % 60 + 60) % 60];
            }
            return sum / 10.0;
        }
        
        public synchronized long getBytesLastMinute() {
            clearOld(System.currentTimeMillis() / 1000L);
            long sum = 0;
            for (long b : secondBytes) {
                sum += b;
            }
            return sum;
        }

        private void clearOld(long sec) {
            if (sec > lastSecond) {
                long diff = Math.min(sec - lastSecond, 60L);
                for (long s = 1; s <= diff; s++) {
                    int idx = (int)((lastSecond + s) % 60);
                    secondCounts[idx] = 0;
                    secondBytes[idx] = 0;
                }
                lastSecond = sec;
            }
        }
    }

    private static final Map<String, ModDataEntry> entries = new ConcurrentHashMap<>();
    private static java.lang.reflect.Field tagField = null;

    static {
        try {
            tagField = Class.forName("zombie.network.packets.service.GlobalModDataPacket").getDeclaredField("tag");
            tagField.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    private static final long[] secondTxBytes = new long[60];
    private static final long[] secondRxBytes = new long[60];
    private static long lastModDataSecond = 0;

    public static synchronized void recordTx(String tag, int bytes) {
        final String effectiveTag = (tag == null || tag.isEmpty()) ? "Unknown" : tag;
        String key = "TX:" + effectiveTag;
        entries.computeIfAbsent(key, k -> new ModDataEntry(effectiveTag, true)).record(bytes);

        long sec = System.currentTimeMillis() / 1000L;
        advanceGlobalSecond(sec);
        secondTxBytes[(int)(sec % 60)] += bytes;
    }

    public static synchronized void recordRx(String tag, int bytes) {
        final String effectiveTag = (tag == null || tag.isEmpty()) ? "Unknown" : tag;
        String key = "RX:" + effectiveTag;
        entries.computeIfAbsent(key, k -> new ModDataEntry(effectiveTag, false)).record(bytes);

        long sec = System.currentTimeMillis() / 1000L;
        advanceGlobalSecond(sec);
        secondRxBytes[(int)(sec % 60)] += bytes;
    }

    private static void advanceGlobalSecond(long sec) {
        if (sec != lastModDataSecond) {
            long diff = Math.min(sec - lastModDataSecond, 60L);
            for (long s = 1; s <= diff; s++) {
                int idx = (int)((lastModDataSecond + s) % 60);
                secondTxBytes[idx] = 0;
                secondRxBytes[idx] = 0;
            }
            lastModDataSecond = sec;
        }
    }

    public static synchronized long[] getHistoryTx() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceGlobalSecond(sec);
        long[] result = new long[60];
        for (int i = 0; i < 60; i++) {
            result[i] = secondTxBytes[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static synchronized long[] getHistoryRx() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceGlobalSecond(sec);
        long[] result = new long[60];
        for (int i = 0; i < 60; i++) {
            result[i] = secondRxBytes[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static synchronized void reset() {
        entries.clear();
        java.util.Arrays.fill(secondTxBytes, 0);
        java.util.Arrays.fill(secondRxBytes, 0);
        lastModDataSecond = 0;
    }

    public static List<ModDataEntry> getEntries(int mode) {
        List<ModDataEntry> list = new ArrayList<>();
        for (ModDataEntry e : entries.values()) {
            if (mode == 2 && !e.isTx) continue; // TX only
            if (mode == 3 && e.isTx) continue;  // RX only
            list.add(e);
        }
        // Sort by activity in last minute, then timestamp, then total count
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getCountLastMinute(), a.getCountLastMinute());
            if (cmp != 0) return cmp;
            cmp = Long.compare(b.lastTimestampMs, a.lastTimestampMs);
            if (cmp != 0) return cmp;
            return Long.compare(b.totalCount, a.totalCount);
        });
        return list;
    }

    public static String getTag(Object packet, ByteBuffer bb, int startPos) {
        if (tagField != null && packet != null) {
            try {
                String val = (String) tagField.get(packet);
                if (val != null && !val.isEmpty()) {
                    return val;
                }
            } catch (Throwable ignored) {
            }
        }
        return peekTag(bb, startPos);
    }

    public static String peekTag(ByteBuffer bb, int startPos) {
        try {
            ByteBuffer dup = bb.duplicate();
            dup.position(startPos);
            return GameWindow.ReadString(dup);
        } catch (Throwable t) {
            return "Unknown";
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
