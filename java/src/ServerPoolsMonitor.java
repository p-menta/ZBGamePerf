package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

public class ServerPoolsMonitor {
    public static final int SOURCE_CLIENT = 0;
    public static final int SOURCE_SERVER = 1;

    public static volatile int activeSource = SOURCE_CLIENT;

    private static final long[] serverHistoryPoolsUsed = new long[60];
    private static long lastServerSec = 0;
    private static volatile ServerNetMonitor.PoolsSnapshot serverSnapshot = new ServerNetMonitor.PoolsSnapshot();
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
        if (UnifiedPerfUI.activeTab != 6 || !UnifiedPerfUI.isOpen) return;

        // ponytail: 1-second cadence prevents RakNet packet congestion
        long now = System.currentTimeMillis();
        if (now - lastClientPollMs >= 1000L) {
            lastClientPollMs = now;
            try {
                IsoPlayer player = IsoPlayer.getInstance();
                KahluaTable table = LuaManager.platform.newTable();
                table.rawset("mode", 1.0);
                table.rawset("topN", 20.0);
                GameClient.instance.sendClientCommand(player, "ZBGamePerf", "ReqPools", table);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void onDataReceived(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            String[] lines = data.split("\n");
            if (lines.length == 0) return;

            // Header line: used;free;total
            String header = lines[0];
            String[] hp = header.split(";");
            long used = 0L;
            long free = 0L;
            long total = 0L;
            if (hp.length >= 3) {
                used = Long.parseLong(hp[0].trim());
                free = Long.parseLong(hp[1].trim());
                total = Long.parseLong(hp[2].trim());
            }

            long sec = System.currentTimeMillis() / 1000L;
            advanceHistory(sec);
            serverHistoryPoolsUsed[(int)(sec % 60)] = used;

            ServerNetMonitor.PoolsSnapshot snap = new ServerNetMonitor.PoolsSnapshot();
            snap.usedObjects = used;
            snap.freeObjects = free;
            snap.totalObjects = total;

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 6) {
                    String name = parts[0];
                    int u = Integer.parseInt(parts[1]);
                    int f = Integer.parseInt(parts[2]);
                    int t = Integer.parseInt(parts[3]);
                    long a = Long.parseLong(parts[4]);
                    long r = Long.parseLong(parts[5]);
                    snap.items.add(new ServerNetMonitor.PoolItem(name, u, f, t, a, r));
                }
            }
            serverSnapshot = snap;
            hasReceivedData = true;
        } catch (Throwable ignored) {
        }
    }

    private static void advanceHistory(long sec) {
        if (sec != lastServerSec) {
            long diff = Math.min(sec - lastServerSec, 60L);
            for (long s = 1; s <= diff; s++) {
                int idx = (int)((lastServerSec + s) % 60);
                serverHistoryPoolsUsed[idx] = 0;
            }
            lastServerSec = sec;
        }
    }

    public static synchronized long[] getHistoryPoolsUsed() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceHistory(sec);
        long[] result = new long[60];
        for (int i = 0; i < 60; i++) {
            result[i] = serverHistoryPoolsUsed[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static ServerNetMonitor.PoolsSnapshot getPoolsSnapshot() {
        return serverSnapshot;
    }

    public static synchronized void reset() {
        activeSource = SOURCE_CLIENT;
        java.util.Arrays.fill(serverHistoryPoolsUsed, 0);
        lastServerSec = 0;
        serverSnapshot = new ServerNetMonitor.PoolsSnapshot();
        hasReceivedData = false;
        lastClientPollMs = 0;
    }
}
