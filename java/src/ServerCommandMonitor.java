package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

public class ServerCommandMonitor {
    public static final int SOURCE_CLIENT = 0;
    public static final int SOURCE_SERVER = 1;

    public static volatile int activeSource = SOURCE_CLIENT;

    private static final long[] serverHistoryTx = new long[60];
    private static final long[] serverHistoryRx = new long[60];
    private static long lastServerSec = 0;
    private static final List<CommandMonitor.CommandEntry> serverEntries = new ArrayList<>();
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
        if (!UnifiedPerfUI.isOpen || (!UnifiedPerfUI.profileAllTabs && UnifiedPerfUI.activeTab != 2)) return;

        // ponytail: 1-second cadence prevents RakNet packet congestion
        long now = System.currentTimeMillis();
        if (now - lastClientPollMs >= 1000L) {
            lastClientPollMs = now;
            try {
                IsoPlayer player = IsoPlayer.getInstance();
                KahluaTable table = LuaManager.platform.newTable();
                table.rawset("mode", (double) ZBGamePerf.commandOsdMode);
                table.rawset("topN", 15.0);
                GameClient.instance.sendClientCommand(player, "ZBGamePerf", "ReqCommands", table);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void onDataReceived(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            String[] lines = data.split("\n");
            if (lines.length == 0) return;

            // Header line: txBytes;rxBytes
            String header = lines[0];
            int semiIdx = header.indexOf(';');
            long txB = 0L;
            long rxB = 0L;
            if (semiIdx > 0 && semiIdx < header.length() - 1) {
                try {
                    txB = Long.parseLong(header.substring(0, semiIdx).trim());
                    rxB = Long.parseLong(header.substring(semiIdx + 1).trim());
                } catch (Throwable ignored) {
                }
            }

            long sec = System.currentTimeMillis() / 1000L;
            advanceHistory(sec);
            serverHistoryTx[(int)(sec % 60)] = txB;
            serverHistoryRx[(int)(sec % 60)] = rxB;

            List<CommandMonitor.CommandEntry> list = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 8) {
                    String module = parts[0];
                    String command = parts[1];
                    boolean isTx = "1".equals(parts[2]);
                    int countLastMin = Integer.parseInt(parts[3]);
                    double rate = Double.parseDouble(parts[4]);
                    int lastBytes = Integer.parseInt(parts[5]);
                    long totalBytes = Long.parseLong(parts[6]);
                    long totalCount = Long.parseLong(parts[7]);

                    CommandMonitor.CommandEntry e = new CommandMonitor.CommandEntry(module, command, isTx);
                    e.totalCount = totalCount;
                    e.totalBytes = totalBytes;
                    e.lastBytes = lastBytes;
                    e.lastTimestampMs = System.currentTimeMillis();
                    for (int k = 0; k < countLastMin; k++) {
                        e.record(0);
                    }
                    e.totalCount = totalCount;
                    e.totalBytes = totalBytes;
                    e.lastBytes = lastBytes;
                    list.add(e);
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
                serverHistoryTx[idx] = 0;
                serverHistoryRx[idx] = 0;
            }
            lastServerSec = sec;
        }
    }

    public static synchronized long[] getHistoryTx() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceHistory(sec);
        long[] result = new long[60];
        for (int i = 0; i < 60; i++) {
            result[i] = serverHistoryTx[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static synchronized long[] getHistoryRx() {
        long sec = System.currentTimeMillis() / 1000L;
        advanceHistory(sec);
        long[] result = new long[60];
        for (int i = 0; i < 60; i++) {
            result[i] = serverHistoryRx[(int)((sec - 59 + i) % 60 + 60) % 60];
        }
        return result;
    }

    public static synchronized List<CommandMonitor.CommandEntry> getEntries() {
        return new ArrayList<>(serverEntries);
    }

    public static synchronized void reset() {
        activeSource = SOURCE_CLIENT;
        java.util.Arrays.fill(serverHistoryTx, 0);
        java.util.Arrays.fill(serverHistoryRx, 0);
        lastServerSec = 0;
        serverEntries.clear();
        hasReceivedData = false;
        lastClientPollMs = 0;
    }
}
