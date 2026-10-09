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

    public static class RakNetSnapshot {
        public boolean isConnected;
        public int lastPing;
        public int avgPing;
        public int lowestPing;
        public double lossLastSec;
        public double lossTotal;
        public long wireTxBytes;
        public long wireRxBytes;
        public long resendBytes;
        public long resendMsgs;
        public boolean isCongested;
        public long bpsLimit;
        public double gameRxPackets;
        public double gameTxPackets;
        public double gameRxBytes;
        public double gameTxBytes;
        public double voipRxBytes;
        public double voipTxBytes;
    }

    public static class ServerWorldSnapshot {
        public int clientFps;
        public double serverFps;
        public long clientMemUsedMb;
        public long clientMemTotalMb;
        public long clientMemMaxMb;
        public double serverMemUsedMb;
        public double serverMemFreeMb;
        public double serverMemTotalMb;
        public double serverMemMaxMb;
        public double serverTickMinMs;
        public double serverTickMaxMs;
        public double serverTickAvgMs;
        public double playersCount;
        public double serverZombiesTotal;
        public double serverZombiesLoaded;
        public double serverZombiesSimulated;
        public double serverZombiesCulled;
        public int localZombies;
        public double animalObjects;
        public double animalInstances;
        public double loadedCells;
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
        long sec = System.currentTimeMillis() / 1000L;
        if (sec == lastSampleSecond) return;

        if (lastSampleSecond != 0) {
            long diff = Math.min(sec - lastSampleSecond, 60L);
            for (long s = 1; s <= diff; s++) {
                int idx = (int) ((lastSampleSecond + s) % 60);
                historyPing[idx] = 0;
                historyLoss[idx] = 0;
                historyWireTx[idx] = 0;
                historyWireRx[idx] = 0;
                historyClientFps[idx] = 0;
                historyServerFps[idx] = 0;
                historyClientMem[idx] = 0;
                historyServerMem[idx] = 0;
                historyPoolsUsed[idx] = 0;
            }
        }
        lastSampleSecond = sec;
        int idx = (int) (sec % 60);

        // Network / RakNet sampling
        if (GameClient.client && GameClient.connection != null) {
            historyPing[idx] = GameClient.connection.getLastPing();
            ZNetStatistics stats = GameClient.connection.getStatistics();
            if (stats != null) {
                historyLoss[idx] = stats.packetlossLastSecond;
                historyWireTx[idx] = stats.lastActualBytesSent;
                historyWireRx[idx] = stats.lastActualBytesReceived;
            }
        }

        // FPS & RAM sampling
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

        // Pools sampling
        try {
            historyPoolsUsed[idx] = (long) PerformanceStatistic.getInstance().allPoolsInUse.get();
        } catch (Throwable ignored) {
        }

        // Rebuild 1-second cached snapshots
        cachedRakNet = buildRakNetSnapshot();
        cachedServerWorld = buildServerWorldSnapshot();
        cachedPools = buildPoolsSnapshot();
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
                snap.lossLastSec = net.packetlossLastSecond;
                snap.lossTotal = net.packetlossTotal;
                snap.wireTxBytes = net.lastActualBytesSent;
                snap.wireRxBytes = net.lastActualBytesReceived;
                snap.resendBytes = net.lastUserMessageBytesResent;
                snap.resendMsgs = net.messagesInResendBuffer;
                snap.isCongested = net.isLimitedByCongestionControl;
                snap.bpsLimit = net.bpsLimitByCongestionControl;
            }
        }

        try {
            NetworkStatistic netStat = NetworkStatistic.getInstance();
            snap.gameRxPackets = netStat.receivedPackets.get();
            snap.gameTxPackets = netStat.sentPackets.get();
            snap.gameRxBytes = netStat.receivedBytes.get();
            snap.gameTxBytes = netStat.sentBytes.get();
            snap.voipRxBytes = netStat.voipReceived.get();
            snap.voipTxBytes = netStat.voipSent.get();
        } catch (Throwable ignored) {
        }

        return snap;
    }

    public static ServerWorldSnapshot getServerWorldSnapshot() {
        return cachedServerWorld;
    }

    private static ServerWorldSnapshot buildServerWorldSnapshot() {
        ServerWorldSnapshot snap = new ServerWorldSnapshot();
        snap.clientFps = Math.round(GameWindow.averageFPS);

        Runtime rt = Runtime.getRuntime();
        snap.clientMemUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        snap.clientMemTotalMb = rt.totalMemory() / (1024L * 1024L);
        snap.clientMemMaxMb = rt.maxMemory() / (1024L * 1024L);

        KahluaTable remotePerf = PerformanceStatistic.getInstance().getRemoteTable();
        snap.serverFps = getDouble(remotePerf, "fps", -1);
        snap.serverMemUsedMb = getDouble(remotePerf, "memory-used", -1);
        snap.serverMemFreeMb = getDouble(remotePerf, "memory-free", -1);
        snap.serverMemTotalMb = getDouble(remotePerf, "memory-total", -1);
        snap.serverMemMaxMb = getDouble(remotePerf, "memory-max", -1);
        snap.serverTickMinMs = getDouble(remotePerf, "min-update-period", -1);
        snap.serverTickMaxMs = getDouble(remotePerf, "max-update-period", -1);
        snap.serverTickAvgMs = getDouble(remotePerf, "avg-update-period", -1);

        KahluaTable remoteGame = GameStatistic.getInstance().getRemoteTable();
        snap.playersCount = getDouble(remoteGame, "players", -1);
        snap.serverZombiesTotal = getDouble(remoteGame, "zombies-total", -1);
        snap.serverZombiesLoaded = getDouble(remoteGame, "zombies-loaded", -1);
        snap.serverZombiesSimulated = getDouble(remoteGame, "zombies-simulated", -1);
        snap.serverZombiesCulled = getDouble(remoteGame, "zombies-culled", -1);
        snap.animalObjects = getDouble(remoteGame, "animals-objects", -1);
        snap.animalInstances = getDouble(remoteGame, "animals-instances", -1);
        snap.loadedCells = getDouble(remoteGame, "loaded-cells", -1);
        snap.playerTeleports = getDouble(remoteGame, "players-teleports", -1);
        snap.zombieTeleports = getDouble(remoteGame, "zombies-teleports", -1);

        try {
            if (IsoWorld.instance != null && IsoWorld.instance.getCell() != null && IsoWorld.instance.getCell().getZombieList() != null) {
                snap.localZombies = IsoWorld.instance.getCell().getZombieList().size();
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
    }
}
