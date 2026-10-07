package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.LuaClosure;
import zombie.Lua.Event;
import zombie.Lua.LuaEventManager;

public class LoadGridSquareMonitor {
    public static class CallbackEntry {
        public final FileInfo info;
        public final Object funcObj;

        public long totalCount = 0;
        public long totalDurationNs = 0;
        public long maxDurationNs = 0;
        public long lastTimestampMs = 0;

        // 60 1-second buckets for rate calculation
        private final int[] secondCounts = new int[60];
        private final long[] secondDurationNs = new long[60];
        private long lastSecond = 0;

        public CallbackEntry(FileInfo info, Object funcObj) {
            this.info = info;
            this.funcObj = funcObj;
        }

        public synchronized void record(long durationNs) {
            long now = System.currentTimeMillis();
            long sec = now / 1000L;

            if (sec != lastSecond) {
                long diff = Math.min(sec - lastSecond, 60L);
                for (long s = 1; s <= diff; s++) {
                    int idx = (int)((lastSecond + s) % 60);
                    secondCounts[idx] = 0;
                    secondDurationNs[idx] = 0;
                }
                lastSecond = sec;
            }

            int idx = (int)(sec % 60);
            secondCounts[idx]++;
            secondDurationNs[idx] += durationNs;

            totalCount++;
            totalDurationNs += durationNs;
            if (durationNs > maxDurationNs) {
                maxDurationNs = durationNs;
            }
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

        public synchronized double getDurationLastMinuteMs() {
            clearOld(System.currentTimeMillis() / 1000L);
            long sum = 0;
            for (long d : secondDurationNs) {
                sum += d;
            }
            return sum / 1_000_000.0;
        }

        private void clearOld(long sec) {
            if (sec > lastSecond) {
                long diff = Math.min(sec - lastSecond, 60L);
                for (long s = 1; s <= diff; s++) {
                    int idx = (int)((lastSecond + s) % 60);
                    secondCounts[idx] = 0;
                    secondDurationNs[idx] = 0;
                }
                lastSecond = sec;
            }
        }
    }

    private static final Map<String, CallbackEntry> entries = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> insideLGS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    public static long totalSquaresLoaded = 0;
    public static long totalEventDurationNs = 0;

    public static boolean isInsideEvent() {
        return insideLGS.get();
    }

    public static void onEnterEvent() {
        if (!ZBGamePerf.loadGridSquareOsdEnabled) return;
        insideLGS.set(Boolean.TRUE);
        totalSquaresLoaded++;
    }

    public static void onExitEvent(long durationNs) {
        insideLGS.set(Boolean.FALSE);
        totalEventDurationNs += durationNs;
    }

    public static void recordCallback(Object fun, long durationNs) {
        if (!ZBGamePerf.loadGridSquareOsdEnabled || fun == null) return;
        FileInfo info = LuaFileInfoExtractor.getLuaFileInfoObj(fun);
        String key = info.prefix + ":" + info.relativePath + ":" + info.line;
        entries.computeIfAbsent(key, k -> new CallbackEntry(info, fun)).record(durationNs);
    }

    public static void syncRegisteredCallbacks() {
        try {
            Event e = LuaEventManager.AddEvent("LoadGridsquare");
            if (e != null && e.callbacks != null) {
                for (int i = 0; i < e.callbacks.size(); i++) {
                    LuaClosure closure = e.callbacks.get(i);
                    if (closure != null) {
                        FileInfo info = LuaFileInfoExtractor.getLuaFileInfoObj(closure);
                        String key = info.prefix + ":" + info.relativePath + ":" + info.line;
                        entries.computeIfAbsent(key, k -> new CallbackEntry(info, closure));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void reset() {
        entries.clear();
        totalSquaresLoaded = 0;
        totalEventDurationNs = 0;
    }

    public static List<CallbackEntry> getEntries(int mode) {
        syncRegisteredCallbacks();
        List<CallbackEntry> list = new ArrayList<>(entries.values());

        if (mode == 2) {
            // Mode 2: Total Time
            list.sort((a, b) -> Long.compare(b.totalDurationNs, a.totalDurationNs));
        } else if (mode == 3) {
            // Mode 3: Most Calls
            list.sort((a, b) -> Long.compare(b.totalCount, a.totalCount));
        } else {
            // Mode 1: Recent Activity / Rate
            list.sort((a, b) -> {
                int cmp = Integer.compare(b.getCountLastMinute(), a.getCountLastMinute());
                if (cmp != 0) return cmp;
                cmp = Long.compare(b.totalDurationNs, a.totalDurationNs);
                if (cmp != 0) return cmp;
                return Long.compare(b.totalCount, a.totalCount);
            });
        }
        return list;
    }
}
