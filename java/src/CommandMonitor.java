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

    private static final long[] secondTxBytes = new long[60];
    private static final long[] secondRxBytes = new long[60];
    private static long lastCmdSecond = 0;
    public static volatile boolean serverProfilingActive = false;

    public static synchronized void recordTx(String module, String command, int bytes) {
        final String m = (module == null || module.isEmpty()) ? "Unknown" : module;
        final String c = (command == null || command.isEmpty()) ? "Unknown" : command;
        String key = "TX:" + m + ":" + c;
        entries.computeIfAbsent(key, k -> new CommandEntry(m, c, true)).record(bytes);

        long sec = System.currentTimeMillis() / 1000L;
        advanceGlobalSecond(sec);
        secondTxBytes[(int)(sec % 60)] += bytes;
    }

    public static synchronized void recordRx(String module, String command, int bytes) {
        final String m = (module == null || module.isEmpty()) ? "Unknown" : module;
        final String c = (command == null || command.isEmpty()) ? "Unknown" : command;
        String key = "RX:" + m + ":" + c;
        entries.computeIfAbsent(key, k -> new CommandEntry(m, c, false)).record(bytes);

        long sec = System.currentTimeMillis() / 1000L;
        advanceGlobalSecond(sec);
        secondRxBytes[(int)(sec % 60)] += bytes;
    }

    private static void advanceGlobalSecond(long sec) {
        if (sec != lastCmdSecond) {
            long diff = Math.min(sec - lastCmdSecond, 60L);
            for (long s = 1; s <= diff; s++) {
                int idx = (int)((lastCmdSecond + s) % 60);
                secondTxBytes[idx] = 0;
                secondRxBytes[idx] = 0;
            }
            lastCmdSecond = sec;
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
        lastCmdSecond = 0;
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

    public static synchronized String packEntries(int mode, int topN) {
        long sec = System.currentTimeMillis() / 1000L;
        advanceGlobalSecond(sec);
        long txB = secondTxBytes[(int)(sec % 60)];
        long rxB = secondRxBytes[(int)(sec % 60)];
        StringBuilder sb = new StringBuilder();
        sb.append(txB).append(';').append(rxB).append('\n');
        List<CommandEntry> list = getEntries(mode);
        int count = 0;
        for (CommandEntry e : list) {
            if (++count > topN) break;
            sb.append(e.module).append('\t')
              .append(e.command).append('\t')
              .append(e.isTx ? '1' : '0').append('\t')
              .append(e.getCountLastMinute()).append('\t')
              .append(String.format(java.util.Locale.US, "%.1f", e.getRatePerSecond())).append('\t')
              .append(e.lastBytes).append('\t')
              .append(e.totalBytes).append('\t')
              .append(e.totalCount).append('\n');
        }
        return sb.toString();
    }
}
