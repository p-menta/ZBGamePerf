package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import zombie.GameWindow;
import zombie.core.znet.ZNetStatistics;
import zombie.iso.IsoWorld;
import zombie.network.GameClient;
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;
import zombie.network.statistics.counters.PoolCounter;
import zombie.network.statistics.data.GameStatistic;
import zombie.network.statistics.data.NetworkStatistic;
import zombie.network.statistics.data.PerformanceStatistic;

public class ServerNetMonitor {
    private static long lastSampleSecond = 0;

    // 60-second sliding histories
    private static final int[] historyPing = new int[60];
    private static final double[] historyLoss = new double[60];
    private static final long[] historyWireTx = new long[60];
    private static final long[] historyWireRx = new long[60];

    private static final int[] historyClientFps = new int[60];
    private static final int[] historyServerFps = new int[60];
    private static final long[] historyClientMem = new long[60];
    private static final long[] historyServerMem = new long[60];

    private static final long[] historyPoolsUsed = new long[60];

    // 1-second cached snapshots (refreshed in update() once every second)
    private static volatile RakNetSnapshot cachedRakNet = new RakNetSnapshot();
    private static volatile ServerWorldSnapshot cachedServerWorld = new ServerWorldSnapshot();
    private static volatile PoolsSnapshot cachedPools = new PoolsSnapshot();

    // Modular measurement control flags
    public static volatile boolean netEnabled = false;
    public static volatile boolean serverWorldEnabled = false;
    public static volatile boolean poolsEnabled = false;
    private static long lastServerWorldReqMs = 0;

    public static class RakNetSnapshot {
        public boolean isConnected;
        public int lastPing;
        public int avgPing;
        public int lowestPing;

        // Low-level RakNet Transport
        public double clientWireRxKb;
        public double serverWireRxKb;
        public double clientWireTxKb;
        public double serverWireTxKb;
        public double clientResentKb;
        public double serverResentKb;
        public double clientLossPct;
        public double serverLossPct;
        public long resendMsgs;
        public boolean isCongested;
        public long bpsLimit;

        // Game Application Protocol
        public double clientGameRxPackets;
        public double serverGameRxPackets;
        public double clientGameTxPackets;
        public double serverGameTxPackets;
        public double clientGameRxKb;
        public double serverGameRxKb;
        public double clientGameTxKb;
        public double serverGameTxKb;

        // VoIP Audio
        public double clientVoipRxKb;
        public double serverVoipRxKb;
        public double clientVoipTxKb;
        public double serverVoipTxKb;
    }

    public static class ServerWorldSnapshot {
        // Performance
        public double clientFps;
        public double serverFps;
        public double clientMemFreeMb;
        public double clientMemUsedMb;
        public double clientMemTotalMb;
        public double clientMemMaxMb;
        public double serverMemFreeMb;
        public double serverMemUsedMb;
        public double serverMemTotalMb;
        public double serverMemMaxMb;
        public double serverTickMinMs;
        public double serverTickMaxMs;
        public double serverTickAvgMs;

        // Characters / Entities
        public double clientPlayers;
        public double serverPlayers;
        public double clientAnimalObjects;
        public double serverAnimalObjects;
        public double clientAnimalInstances;
        public double serverAnimalInstances;
        public double clientAnimalOwned;
        public double serverAnimalOwned;
        public double clientZombiesTotal;
        public double serverZombiesTotal;
        public double clientZombiesLoaded;
        public double serverZombiesLoaded;
        public double clientZombiesSimulated;
        public double serverZombiesSimulated;
        public double clientZombiesCulled;
        public double serverZombiesCulled;

        // World Simulation & Desyncs
        public double clientLoadedCells;
        public double serverLoadedCells;
        public double playerTeleports;
        public double zombieTeleports;
    }

    public static class PoolItem implements Comparable<PoolItem> {
        public String name;
        public int used;
        public int free;
        public int total;
        public long alloc;
        public long reused;

        public PoolItem(String name, int used, int free, int total, long alloc, long reused) {
            this.name = name;
            this.used = used;
            this.free = free;
            this.total = total;
            this.alloc = alloc;
            this.reused = reused;
        }

        @Override
        public int compareTo(PoolItem o) {
            return Integer.compare(o.used, this.used);
        }
    }

    public static class PoolsSnapshot {
        public long totalObjects;
        public long usedObjects;
        public long freeObjects;
        public List<PoolItem> items = new ArrayList<>();
    }

    public static synchronized void update() {
        if (!netEnabled && !serverWorldEnabled && !poolsEnabled) return;

        long sec = System.currentTimeMillis() / 1000L;
        if (sec == lastSampleSecond) return;

        if (lastSampleSecond != 0) {
            long diff = Math.min(sec - lastSampleSecond, 60L);
            for (long s = 1; s <= diff; s++) {
                int idx = (int) ((lastSampleSecond + s) % 60);
                if (netEnabled) {
                    historyPing[idx] = 0;
                    historyLoss[idx] = 0;
                    historyWireTx[idx] = 0;
                    historyWireRx[idx] = 0;
                }
                if (serverWorldEnabled) {
                    historyClientFps[idx] = 0;
                    historyServerFps[idx] = 0;
                    historyClientMem[idx] = 0;
                    historyServerMem[idx] = 0;
                }
                if (poolsEnabled) {
                    historyPoolsUsed[idx] = 0;
                }
            }
        }
        lastSampleSecond = sec;
        int idx = (int) (sec % 60);

        // Network / RakNet sampling
        if (netEnabled) {
            if (GameClient.client && GameClient.connection != null) {
                historyPing[idx] = GameClient.connection.getLastPing();
                ZNetStatistics stats = GameClient.connection.getStatistics();
                if (stats != null) {
                    historyLoss[idx] = stats.packetlossLastSecond;
                    historyWireTx[idx] = stats.lastActualBytesSent;
                    historyWireRx[idx] = stats.lastActualBytesReceived;
                }
            }
            cachedRakNet = buildRakNetSnapshot();
        }

        // FPS & RAM sampling
        if (serverWorldEnabled) {
            historyClientFps[idx] = Math.round(GameWindow.averageFPS);
            Runtime rt = Runtime.getRuntime();
            historyClientMem[idx] = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);

            KahluaTable remotePerf = PerformanceStatistic.getInstance().getRemoteTable();
            if (remotePerf != null) {
                double sFps = getDouble(remotePerf, "fps", -1);
                if (sFps >= 0) historyServerFps[idx] = (int) Math.round(sFps);
                double sMem = getDouble(remotePerf, "memory-used", -1);
                if (sMem >= 0) historyServerMem[idx] = (long) sMem;
            }
            cachedServerWorld = buildServerWorldSnapshot();

            // Periodically refresh remote server statistics if on MP
            if (GameClient.client && (sec % 2 == 0)) {
                requestServerStatistics();
            }
            if (GameClient.client && GameClient.connection != null && UnifiedPerfUI.activeTab == 5 && UnifiedPerfUI.isOpen) {
                long now = System.currentTimeMillis();
                if (now - lastServerWorldReqMs >= 1000L) {
                    lastServerWorldReqMs = now;
                    try {
                        GameClient.instance.sendClientCommand(zombie.characters.IsoPlayer.getInstance(), "ZBGamePerf", "ReqServerWorld", (KahluaTable) null);
                    } catch (Throwable ignored) {}
                }
            }
        }

        // Pools sampling
        if (poolsEnabled) {
            try {
                historyPoolsUsed[idx] = (long) PerformanceStatistic.getInstance().allPoolsInUse.get();
            } catch (Throwable ignored) {
            }
            cachedPools = buildPoolsSnapshot();
        }
    }

    public static synchronized int[] getHistoryPing() {
        return copyCircularInt(historyPing);
    }

    public static synchronized double[] getHistoryLoss() {
        return copyCircularDouble(historyLoss);
    }

    public static synchronized int[] getHistoryClientFps() {
        return copyCircularInt(historyClientFps);
    }

    public static synchronized int[] getHistoryServerFps() {
        return copyCircularInt(historyServerFps);
    }

    public static synchronized long[] getHistoryClientMem() {
        return copyCircularLong(historyClientMem);
    }

    public static synchronized long[] getHistoryServerMem() {
        return copyCircularLong(historyServerMem);
    }

    public static synchronized long[] getHistoryPoolsUsed() {
        return copyCircularLong(historyPoolsUsed);
    }

    private static int[] copyCircularInt(int[] src) {
        int[] out = new int[60];
        long sec = System.currentTimeMillis() / 1000L;
        for (int i = 0; i < 60; i++) {
            out[i] = src[(int) ((sec - 59 + i) % 60 + 60) % 60];
        }
        return out;
    }

    private static double[] copyCircularDouble(double[] src) {
        double[] out = new double[60];
        long sec = System.currentTimeMillis() / 1000L;
        for (int i = 0; i < 60; i++) {
            out[i] = src[(int) ((sec - 59 + i) % 60 + 60) % 60];
        }
        return out;
    }

    private static long[] copyCircularLong(long[] src) {
        long[] out = new long[60];
        long sec = System.currentTimeMillis() / 1000L;
        for (int i = 0; i < 60; i++) {
            out[i] = src[(int) ((sec - 59 + i) % 60 + 60) % 60];
        }
        return out;
    }

    public static RakNetSnapshot getRakNetSnapshot() {
        return cachedRakNet;
    }

    private static RakNetSnapshot buildRakNetSnapshot() {
        RakNetSnapshot snap = new RakNetSnapshot();
        snap.isConnected = GameClient.client && GameClient.connection != null;
        if (snap.isConnected) {
            snap.lastPing = GameClient.connection.getLastPing();
            snap.avgPing = GameClient.connection.getAveragePing();
            snap.lowestPing = GameClient.connection.getLowestPing();

            ZNetStatistics net = GameClient.connection.getStatistics();
            if (net != null) {
                snap.resendMsgs = net.messagesInResendBuffer;
                snap.isCongested = net.isLimitedByCongestionControl;
                snap.bpsLimit = net.bpsLimitByCongestionControl;
            }
        }

        try {
            KahluaTable locNet = NetworkStatistic.getInstance().getLocalTable();
            KahluaTable remNet = NetworkStatistic.getInstance().getRemoteTable();

            snap.clientGameRxPackets = getDouble(locNet, "received-packets", 0);
            snap.serverGameRxPackets = getDouble(remNet, "received-packets", -1);
            snap.clientGameTxPackets = getDouble(locNet, "sent-packets", 0);
            snap.serverGameTxPackets = getDouble(remNet, "sent-packets", -1);

            snap.clientGameRxKb = getDouble(locNet, "received-bytes", 0);
            snap.serverGameRxKb = getDouble(remNet, "received-bytes", -1);
            snap.clientGameTxKb = getDouble(locNet, "sent-bytes", 0);
            snap.serverGameTxKb = getDouble(remNet, "sent-bytes", -1);

            snap.clientWireRxKb = getDouble(locNet, "last-actual-bytes-received", 0);
            snap.serverWireRxKb = getDouble(remNet, "last-actual-bytes-received", -1);
            snap.clientWireTxKb = getDouble(locNet, "last-actual-bytes-sent", 0);
            snap.serverWireTxKb = getDouble(remNet, "last-actual-bytes-sent", -1);

            snap.clientResentKb = getDouble(locNet, "last-user-message-bytes-resent", 0);
            snap.serverResentKb = getDouble(remNet, "last-user-message-bytes-resent", -1);

            snap.clientLossPct = getDouble(locNet, "packet-loss-last-second", 0);
            snap.serverLossPct = getDouble(remNet, "packet-loss-last-second", -1);

            snap.clientVoipRxKb = getDouble(locNet, "voip-received", 0);
            snap.serverVoipRxKb = getDouble(remNet, "voip-received", -1);
            // Fallbacks for client metrics if KahluaTable wasn't populated yet
            if (snap.isConnected) {
                ZNetStatistics net = GameClient.connection.getStatistics();
                if (net != null) {
                    if (snap.clientWireRxKb <= 0) snap.clientWireRxKb = net.lastActualBytesReceived;
                    if (snap.clientWireTxKb <= 0) snap.clientWireTxKb = net.lastActualBytesSent;
                    if (snap.clientResentKb <= 0) snap.clientResentKb = net.lastUserMessageBytesResent;
                    if (snap.clientLossPct <= 0) snap.clientLossPct = net.packetlossLastSecond;
                }
            }
        } catch (Throwable ignored) {
        }

        return snap;
    }

    public static ServerWorldSnapshot getServerWorldSnapshot() {
        return cachedServerWorld;
    }

    private static ServerWorldSnapshot buildServerWorldSnapshot() {
        ServerWorldSnapshot snap = new ServerWorldSnapshot();
        Runtime rt = Runtime.getRuntime();

        try {
            KahluaTable locPerf = PerformanceStatistic.getInstance().getLocalTable();
            KahluaTable remPerf = PerformanceStatistic.getInstance().getRemoteTable();

            snap.clientMemFreeMb = getDouble(locPerf, "memory-free", rt.freeMemory() / 1_000_000.0);
            snap.clientMemUsedMb = getDouble(locPerf, "memory-used", (rt.totalMemory() - rt.freeMemory()) / 1_000_000.0);
            snap.clientMemTotalMb = getDouble(locPerf, "memory-total", rt.totalMemory() / 1_000_000.0);
            snap.clientMemMaxMb = getDouble(locPerf, "memory-max", rt.maxMemory() / 1_000_000.0);
            snap.clientFps = getDouble(locPerf, "fps", Math.round(GameWindow.averageFPS));

            snap.serverMemFreeMb = getDouble(remPerf, "memory-free", -1);
            snap.serverMemUsedMb = getDouble(remPerf, "memory-used", -1);
            snap.serverMemTotalMb = getDouble(remPerf, "memory-total", -1);
            snap.serverMemMaxMb = getDouble(remPerf, "memory-max", -1);
            snap.serverFps = getDouble(remPerf, "fps", -1);
            snap.serverTickMinMs = getDouble(remPerf, "min-update-period", -1);
            snap.serverTickMaxMs = getDouble(remPerf, "max-update-period", -1);
            snap.serverTickAvgMs = getDouble(remPerf, "avg-update-period", -1);
        } catch (Throwable ignored) {
        }

        try {
            KahluaTable locGame = GameStatistic.getInstance().getLocalTable();
            KahluaTable remGame = GameStatistic.getInstance().getRemoteTable();

            snap.clientPlayers = getDouble(locGame, "players", GameClient.client ? GameClient.IDToPlayerMap.size() : 1);
            snap.serverPlayers = getDouble(remGame, "players", -1);

            snap.clientAnimalObjects = getDouble(locGame, "animals-objects", -1);
            snap.serverAnimalObjects = getDouble(remGame, "animals-objects", -1);
            snap.clientAnimalInstances = getDouble(locGame, "animals-instances", -1);
            snap.serverAnimalInstances = getDouble(remGame, "animals-instances", -1);
            snap.clientAnimalOwned = getDouble(locGame, "animals-owned", -1);
            snap.serverAnimalOwned = getDouble(remGame, "animals-owned", -1);

            snap.clientZombiesTotal = getDouble(locGame, "zombies-total", -1);
            snap.serverZombiesTotal = getDouble(remGame, "zombies-total", -1);
            snap.clientZombiesLoaded = getDouble(locGame, "zombies-loaded", -1);
            snap.serverZombiesLoaded = getDouble(remGame, "zombies-loaded", -1);
            snap.clientZombiesSimulated = getDouble(locGame, "zombies-simulated", -1);
            snap.serverZombiesSimulated = getDouble(remGame, "zombies-simulated", -1);
            snap.clientZombiesCulled = getDouble(locGame, "zombies-culled", -1);
            snap.serverZombiesCulled = getDouble(remGame, "zombies-culled", -1);

            snap.clientLoadedCells = getDouble(locGame, "loaded-cells", -1);
            snap.serverLoadedCells = getDouble(remGame, "loaded-cells", -1);
            snap.playerTeleports = getDouble(remGame, "players-teleports", -1);
            snap.zombieTeleports = getDouble(remGame, "zombies-teleports", -1);

            // Fallbacks for client local world entities
            if (snap.clientZombiesLoaded < 0 && IsoWorld.instance != null && IsoWorld.instance.getCell() != null) {
                try {
                    snap.clientZombiesLoaded = IsoWorld.instance.getCell().getZombieList().size();
                } catch (Throwable ignored) {}
            }
            if (snap.clientZombiesTotal < 0) snap.clientZombiesTotal = snap.clientZombiesLoaded;
            if (snap.clientZombiesSimulated < 0) snap.clientZombiesSimulated = snap.clientZombiesLoaded;

            if (snap.clientAnimalObjects < 0 && IsoWorld.instance != null && IsoWorld.instance.currentCell != null) {
                try {
                    snap.clientAnimalObjects = IsoWorld.instance.currentCell.getObjectList().stream()
                        .filter(obj -> obj instanceof zombie.characters.animals.IsoAnimal).count();
                } catch (Throwable ignored) {}
            }
            if (snap.clientAnimalInstances < 0) {
                try {
                    snap.clientAnimalInstances = zombie.popman.animal.AnimalInstanceManager.getInstance().getAnimals().size();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }

        return snap;
    }

    public static PoolsSnapshot getPoolsSnapshot() {
        return cachedPools;
    }

    private static PoolsSnapshot buildPoolsSnapshot() {
        PoolsSnapshot snap = new PoolsSnapshot();
        try {
            PerformanceStatistic perf = PerformanceStatistic.getInstance();
            snap.totalObjects = (long) perf.allPoolsTotal.get();
            snap.usedObjects = (long) perf.allPoolsInUse.get();
            snap.freeObjects = (long) perf.allPoolsFree.get();

            perf.visitAllPools(pc -> {
                if (pc != null) {
                    String pName = pc.total.getName();
                    if (pName != null && pName.endsWith("_total")) {
                        pName = pName.substring(0, pName.length() - 6);
                    }
                    snap.items.add(new PoolItem(
                        pName,
                        pc.getUsedCount(),
                        pc.getFreeCount(),
                        pc.getTotalPoolCount(),
                        (long) pc.alloc.get(),
                        (long) pc.reused.get()
                    ));
                }
            });
            Collections.sort(snap.items);
        } catch (Throwable ignored) {
        }
        return snap;
    }

    public static void requestServerStatistics() {
        if (GameClient.client) {
            try {
                INetworkPacket.send(PacketTypes.PacketType.Statistics);
            } catch (Throwable ignored) {
            }
        }
    }

    public static double getDouble(KahluaTable table, String key, double defVal) {
        if (table == null) return defVal;
        Object obj = table.rawget(key);
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return defVal;
    }

    public static synchronized void reset() {
        lastSampleSecond = 0;
        java.util.Arrays.fill(historyPing, 0);
        java.util.Arrays.fill(historyLoss, 0);
        java.util.Arrays.fill(historyWireTx, 0);
        java.util.Arrays.fill(historyWireRx, 0);
        java.util.Arrays.fill(historyClientFps, 0);
        java.util.Arrays.fill(historyServerFps, 0);
        java.util.Arrays.fill(historyClientMem, 0);
        java.util.Arrays.fill(historyServerMem, 0);
        java.util.Arrays.fill(historyPoolsUsed, 0);
        cachedRakNet = new RakNetSnapshot();
        cachedServerWorld = new ServerWorldSnapshot();
        cachedPools = new PoolsSnapshot();
        lastServerWorldReqMs = 0;
    }

    public static synchronized String packServerWorld() {
        PerformanceStatistic perf = PerformanceStatistic.getInstance();
        GameStatistic game = GameStatistic.getInstance();

        double fps = (perf != null && perf.fps != null) ? perf.fps.get() : Math.round(GameWindow.averageFPS);
        Runtime rt = Runtime.getRuntime();
        double memTotal = rt.totalMemory() / (1024.0 * 1024.0);
        double memFree = rt.freeMemory() / (1024.0 * 1024.0);
        double memUsed = memTotal - memFree;
        double memMax = rt.maxMemory() / (1024.0 * 1024.0);
        if (perf != null) {
            try {
                if (perf.memoryUsed != null) memUsed = perf.memoryUsed.get();
                if (perf.memoryFree != null) memFree = perf.memoryFree.get();
                if (perf.memoryTotal != null) memTotal = perf.memoryTotal.get();
                if (perf.memoryMax != null) memMax = perf.memoryMax.get();
            } catch (Throwable ignored) {}
        }

        double tickMin = (perf != null && perf.minUpdatePeriod != null) ? perf.minUpdatePeriod.get() : -1.0;
        double tickMax = (perf != null && perf.maxUpdatePeriod != null) ? perf.maxUpdatePeriod.get() : -1.0;
        double tickAvg = (perf != null && perf.avgUpdatePeriod != null) ? perf.avgUpdatePeriod.get() : -1.0;

        double players = (game != null && game.players != null) ? game.players.get() : 0.0;
        double animals = (game != null && game.animalObjects != null) ? game.animalObjects.get() : 0.0;
        double zombies = (game != null && game.zombiesLoaded != null) ? game.zombiesLoaded.get() : 0.0;
        double loadedCells = (game != null && game.loadedCells != null) ? game.loadedCells.get() : 0.0;
        double teleports = (game != null && game.zombiesTeleports != null) ? game.zombiesTeleports.get() : 0.0;

        return String.format(java.util.Locale.US,
            "%.1f;%.1f;%.1f;%.1f;%.1f;%.1f;%.1f;%.1f;%.0f;%.0f;%.0f;%.0f;%.0f",
            fps, memUsed, memFree, memTotal, memMax,
            tickMin, tickMax, tickAvg,
            players, animals, zombies, loadedCells, teleports);
    }

    public static synchronized void onServerWorldReceived(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            String[] p = data.trim().split(";");
            if (p.length >= 13) {
                double sFps = Double.parseDouble(p[0]);
                double sMemUsed = Double.parseDouble(p[1]);
                double sMemFree = Double.parseDouble(p[2]);
                double sMemTotal = Double.parseDouble(p[3]);
                double sMemMax = Double.parseDouble(p[4]);
                double sTickMin = Double.parseDouble(p[5]);
                double sTickMax = Double.parseDouble(p[6]);
                double sTickAvg = Double.parseDouble(p[7]);
                double sPlayers = Double.parseDouble(p[8]);
                double sAnimals = Double.parseDouble(p[9]);
                double sZombies = Double.parseDouble(p[10]);
                double sCells = Double.parseDouble(p[11]);
                double sTeleports = Double.parseDouble(p[12]);

                cachedServerWorld.serverFps = sFps;
                cachedServerWorld.serverMemUsedMb = sMemUsed;
                cachedServerWorld.serverMemFreeMb = sMemFree;
                cachedServerWorld.serverMemTotalMb = sMemTotal;
                cachedServerWorld.serverMemMaxMb = sMemMax;
                cachedServerWorld.serverTickMinMs = sTickMin;
                cachedServerWorld.serverTickMaxMs = sTickMax;
                cachedServerWorld.serverTickAvgMs = sTickAvg;
                cachedServerWorld.serverPlayers = sPlayers;
                cachedServerWorld.serverAnimalObjects = sAnimals;
                cachedServerWorld.serverAnimalInstances = sAnimals;
                cachedServerWorld.serverZombiesTotal = sZombies;
                cachedServerWorld.serverZombiesLoaded = sZombies;
                cachedServerWorld.serverZombiesSimulated = sZombies;
                cachedServerWorld.serverLoadedCells = sCells;
                cachedServerWorld.zombieTeleports = sTeleports;

                long sec = System.currentTimeMillis() / 1000L;
                int idx = (int)(sec % 60);
                if (sFps >= 0) historyServerFps[idx] = (int) Math.round(sFps);
                if (sMemUsed >= 0) historyServerMem[idx] = (long) sMemUsed;
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized String packPools(int mode, int topN) {
        PoolsSnapshot snap = buildPoolsSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append(snap.usedObjects).append(';')
          .append(snap.freeObjects).append(';')
          .append(snap.totalObjects).append('\n');
        List<PoolItem> list = new ArrayList<>(snap.items);
        if (mode == 2) {
            list.sort((a, b) -> Long.compare(b.reused, a.reused));
        } else if (mode == 3) {
            list.sort((a, b) -> Long.compare(b.alloc, a.alloc));
        } else {
            list.sort((a, b) -> Integer.compare(b.used, a.used));
        }
        int count = 0;
        for (PoolItem item : list) {
            if (++count > topN) break;
            sb.append(item.name).append('\t')
              .append(item.used).append('\t')
              .append(item.free).append('\t')
              .append(item.total).append('\t')
              .append(item.alloc).append('\t')
              .append(item.reused).append('\n');
        }
        return sb.toString();
    }
}
