package me.p_menta.zb_game_perf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.network.ByteBufferReader;

public class CommandMonitor {
    public static class CommandEntry {
        public final String module;
        public final String command;
        public final boolean isTx; // true = TX (Client Command), false = RX (Server Command)

        public long totalCount = 0;
        public long totalBytes = 0;
        public int lastBytes = 0;
        public long lastTimestampMs = 0;

        // 60 1-second buckets for accurate rate calculation
        private final int[] secondCounts = new int[60];
        private final long[] secondBytes = new long[60];
        private long lastSecond = 0;

        public CommandEntry(String module, String command, boolean isTx) {
            this.module = module;
            this.command = command;
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

    private static final Map<String, CommandEntry> entries = new ConcurrentHashMap<>();
    private static java.lang.reflect.Field bbField = null;

    static {
        try {
            bbField = Class.forName("zombie.core.raknet.UdpConnection").getDeclaredField("bb");
            bbField.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    public static void recordTx(String module, String command, int bytes) {
        final String m = (module == null || module.isEmpty()) ? "Unknown" : module;
        final String c = (command == null || command.isEmpty()) ? "Unknown" : command;
        String key = "TX:" + m + ":" + c;
        entries.computeIfAbsent(key, k -> new CommandEntry(m, c, true)).record(bytes);
    }

    public static void recordRx(String module, String command, int bytes) {
        final String m = (module == null || module.isEmpty()) ? "Unknown" : module;
        final String c = (command == null || command.isEmpty()) ? "Unknown" : command;
        String key = "RX:" + m + ":" + c;
        entries.computeIfAbsent(key, k -> new CommandEntry(m, c, false)).record(bytes);
    }

    public static void reset() {
        entries.clear();
    }

    public static List<CommandEntry> getEntries(int mode) {
        List<CommandEntry> list = new ArrayList<>();
        for (CommandEntry e : entries.values()) {
            if (mode == 2 && !e.isTx) continue; // TX only
            if (mode == 3 && e.isTx) continue;  // RX only
            list.add(e);
        }
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getCountLastMinute(), a.getCountLastMinute());
            if (cmp != 0) return cmp;
            cmp = Long.compare(b.lastTimestampMs, a.lastTimestampMs);
            if (cmp != 0) return cmp;
            return Long.compare(b.totalCount, a.totalCount);
        });
        return list;
    }

    public static int getConnectionByteCount(Object connection) {
        if (bbField != null && connection != null) {
            try {
                ByteBuffer bb = (ByteBuffer) bbField.get(connection);
                if (bb != null) {
                    return bb.limit();
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    public static String[] peekModuleCommand(ByteBuffer bb, int startPos) {
        try {
            ByteBuffer dup = bb.duplicate();
            dup.position(startPos);
            ByteBufferReader reader = new ByteBufferReader(dup);
            String module = reader.getUTF();
            String command = reader.getUTF();
            return new String[] { module, command };
        } catch (Throwable t) {
            return new String[] { "Unknown", "Unknown" };
        }
    }
}
