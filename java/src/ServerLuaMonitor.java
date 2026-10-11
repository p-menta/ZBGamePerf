package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

public class ServerLuaMonitor {
    public static final int SOURCE_CLIENT = 0;
    public static final int SOURCE_SERVER = 1;

    public static volatile int activeSource = SOURCE_CLIENT;

    private static final long[] serverHistoryNs = new long[60];
    private static long lastServerSec = 0;
    private static final List<PerfRenderer.FormattedCall> serverCalls = new ArrayList<>();
    private static long lastClientPollMs = 0;
    public static volatile boolean hasReceivedData = false;

    public static boolean isServerSource() {
        return activeSource == SOURCE_SERVER;
    }

    public static void toggleSource() {
        activeSource = (activeSource == SOURCE_CLIENT) ? SOURCE_SERVER : SOURCE_CLIENT;
    }

    public static synchronized void update() {
        // Run server-side auto-shutdown check
        PerformanceMonitor.serverUpdate();

        // Client-side on-demand polling check
        if (activeSource != SOURCE_SERVER) {
            return;
        }
        if (!GameClient.client || GameClient.connection == null) {
            return;
        }
        if (!UnifiedPerfUI.isOpen || (!UnifiedPerfUI.profileAllTabs && UnifiedPerfUI.activeTab != 0)) {
            return;
        }

        // ponytail: 1-second cadence prevents RakNet packet congestion while keeping graph smooth
        long now = System.currentTimeMillis();
        if (now - lastClientPollMs >= 1000L) {
            lastClientPollMs = now;
            try {
                IsoPlayer player = IsoPlayer.getInstance();
                KahluaTable table = LuaManager.platform.newTable();
                table.rawset("mode", (double) ZBGamePerf.osdMode);
                table.rawset("topN", 15.0);
                GameClient.instance.sendClientCommand(player, "ZBGamePerf", "ReqLuaCalls", table);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void onDataReceived(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        try {
            String[] lines = data.split("\n");
            if (lines.length == 0) return;

            // Header line: mode;latestNs
            String header = lines[0];
            int semiIdx = header.indexOf(';');
            long latestNs = 0L;
            if (semiIdx > 0 && semiIdx < header.length() - 1) {
                try {
                    latestNs = Long.parseLong(header.substring(semiIdx + 1).trim());
                } catch (Throwable ignored) {
                }
            }

            long sec = System.currentTimeMillis() / 1000L;
            advanceHistory(sec);
            serverHistoryNs[(int)(sec % 60)] = latestNs;

            List<PerfRenderer.FormattedCall> list = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 7) {
                    String timeStr = parts[0];
                    String countStr = parts[1];
                    String prefixStr = parts[2];
                    String pathStr = parts[3];
                    double r = Double.parseDouble(parts[4]);
                    double g = Double.parseDouble(parts[5]);
                    double b = Double.parseDouble(parts[6]);
                    list.add(new PerfRenderer.FormattedCall(timeStr, countStr, prefixStr, pathStr, r, g, b));
                }
            }
            serverCalls.clear();
            serverCalls.addAll(list);
            hasReceivedData = true;
        } catch (Throwable ignored) {
        }
    }

    private static void advanceHistory(long sec) {
        if (sec != lastServerSec) {
            long diff = Math.min(sec - lastServerSec, 60L);
            for (long s = 1; s <= diff; s++) {
                serverHistoryNs[(int)((lastServerSec + s) % 60)] = 0;
            }
            lastServerSec = sec;
        }
    }

    public static synchronized long[] getHistory() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceHistory(sec);
        long[] result = new long[60];
        for (int i = 0; i < 60; i++) {
            result[i] = serverHistoryNs[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static synchronized List<PerfRenderer.FormattedCall> getTopCalls() {
        return new ArrayList<>(serverCalls);
    }

    public static synchronized void reset() {
        java.util.Arrays.fill(serverHistoryNs, 0);
        lastServerSec = 0;
        serverCalls.clear();
        lastClientPollMs = 0;
        hasReceivedData = false;
    }
}
