package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

public class ServerLgsMonitor {
    public static final int SOURCE_CLIENT = 0;
    public static final int SOURCE_SERVER = 1;

    public static volatile int activeSource = SOURCE_CLIENT;

    public static class FormattedLgsEntry {
        public final String prefix;
        public final String loc;
        public final long totalCount;
        public final double ratePerSec;
        public final double totalMs;
        public final double avgUs;
        public final double maxMs;

        public FormattedLgsEntry(String prefix, String loc, long totalCount, double ratePerSec,
                                 double totalMs, double avgUs, double maxMs) {
            this.prefix = prefix;
            this.loc = loc;
            this.totalCount = totalCount;
            this.ratePerSec = ratePerSec;
            this.totalMs = totalMs;
            this.avgUs = avgUs;
            this.maxMs = maxMs;
        }
    }

    private static final long[] serverHistoryDuration = new long[60];
    private static final int[] serverHistorySquares = new int[60];
    private static long lastServerSec = 0;
    private static final List<FormattedLgsEntry> serverEntries = new ArrayList<>();
    private static long lastClientPollMs = 0;
    public static volatile boolean hasReceivedData = false;

    public static boolean isServerSource() {
        return activeSource == SOURCE_SERVER;
    }

    public static void toggleSource() {
        activeSource = (activeSource == SOURCE_CLIENT) ? SOURCE_SERVER : SOURCE_CLIENT;
    }

    public static synchronized void update() {
        if (activeSource != SOURCE_SERVER) return;
        if (!GameClient.client || GameClient.connection == null) return;
        if (UnifiedPerfUI.activeTab != 3 || !UnifiedPerfUI.isOpen) return;

        // ponytail: 1-second cadence prevents RakNet packet congestion
        long now = System.currentTimeMillis();
        if (now - lastClientPollMs >= 1000L) {
            lastClientPollMs = now;
            try {
                IsoPlayer player = IsoPlayer.getInstance();
                KahluaTable table = LuaManager.platform.newTable();
                table.rawset("mode", (double) ZBGamePerf.loadGridSquareOsdMode);
                table.rawset("topN", 15.0);
                GameClient.instance.sendClientCommand(player, "ZBGamePerf", "ReqLgs", table);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void onDataReceived(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            String[] lines = data.split("\n");
            if (lines.length == 0) return;

            // Header line: durationNs;squares
            String header = lines[0];
            int semiIdx = header.indexOf(';');
            long durNs = 0L;
            int sqs = 0;
            if (semiIdx > 0 && semiIdx < header.length() - 1) {
                try {
                    durNs = Long.parseLong(header.substring(0, semiIdx).trim());
                    sqs = Integer.parseInt(header.substring(semiIdx + 1).trim());
                } catch (Throwable ignored) {
                }
            }

            long sec = System.currentTimeMillis() / 1000L;
            advanceHistory(sec);
            serverHistoryDuration[(int)(sec % 60)] = durNs;
            serverHistorySquares[(int)(sec % 60)] = sqs;

            List<FormattedLgsEntry> list = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 7) {
                    String prefix = parts[0];
                    String loc = parts[1];
                    long totalCount = Long.parseLong(parts[2]);
                    double rate = Double.parseDouble(parts[3]);
                    double totalMs = Double.parseDouble(parts[4]);
                    double avgUs = Double.parseDouble(parts[5]);
                    double maxMs = Double.parseDouble(parts[6]);
                    list.add(new FormattedLgsEntry(prefix, loc, totalCount, rate, totalMs, avgUs, maxMs));
                }
            }
            serverEntries.clear();
            serverEntries.addAll(list);
            hasReceivedData = true;
        } catch (Throwable ignored) {
        }
    }

    private static void advanceHistory(long sec) {
        if (sec != lastServerSec) {
            long diff = Math.min(sec - lastServerSec, 60L);
            for (long s = 1; s <= diff; s++) {
                int idx = (int)((lastServerSec + s) % 60);
                serverHistoryDuration[idx] = 0;
                serverHistorySquares[idx] = 0;
            }
            lastServerSec = sec;
        }
    }

    public static synchronized long[] getHistoryDuration() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceHistory(sec);
        long[] result = new long[60];
        for (int i = 0; i < 60; i++) {
            result[i] = serverHistoryDuration[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static synchronized int[] getHistorySquares() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceHistory(sec);
        int[] result = new int[60];
        for (int i = 0; i < 60; i++) {
            result[i] = serverHistorySquares[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static synchronized List<FormattedLgsEntry> getEntries() {
        return new ArrayList<>(serverEntries);
    }

    public static synchronized void reset() {
        activeSource = SOURCE_CLIENT;
        java.util.Arrays.fill(serverHistoryDuration, 0);
        java.util.Arrays.fill(serverHistorySquares, 0);
        lastServerSec = 0;
        serverEntries.clear();
        hasReceivedData = false;
        lastClientPollMs = 0;
    }
}
