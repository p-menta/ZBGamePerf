package me.p_menta.zb_game_perf;

public class SelfCheck {
    public static void main(String[] args) {
        // Test ServerNetMonitor history buffer and sorting logic
        ServerNetMonitor.reset();

        int[] ping = ServerNetMonitor.getHistoryPing();
        assert ping != null : "ping history must not be null";
        assert ping.length == 60 : "ping history length must be 60";

        ServerNetMonitor.RakNetSnapshot snap = ServerNetMonitor.getRakNetSnapshot();
        assert snap != null : "snapshot must not be null";

        // Verify history values initialized
        for (int v : ping) {
            assert v == 0 : "initial ping must be 0";
        }

        // Verify PoolItem sorting
        ServerNetMonitor.PoolItem p1 = new ServerNetMonitor.PoolItem("ItemA", 10, 50, 60, 5, 20);
        ServerNetMonitor.PoolItem p2 = new ServerNetMonitor.PoolItem("ItemB", 25, 10, 35, 15, 30);
        java.util.List<ServerNetMonitor.PoolItem> list = new java.util.ArrayList<>();
        list.add(p1);
        list.add(p2);

        // Sort by used descending
        list.sort((a, b) -> Integer.compare(b.used, a.used));
        assert list.get(0).name.equals("ItemB") : "ItemB should be first by used";

        // Sort by alloc descending
        list.sort((a, b) -> Long.compare(b.alloc, a.alloc));
        assert list.get(0).name.equals("ItemB") : "ItemB should be first by alloc";

        // Test modular flags on ServerNetMonitor
        assert !ServerNetMonitor.netEnabled : "netEnabled should default to false";
        assert !ServerNetMonitor.serverWorldEnabled : "serverWorldEnabled should default to false";
        assert !ServerNetMonitor.poolsEnabled : "poolsEnabled should default to false";

        ServerNetMonitor.netEnabled = true;
        assert ServerNetMonitor.netEnabled : "netEnabled should be true when set";
        ServerNetMonitor.netEnabled = false;

        // Test ChunkDeliveryMonitor
        ChunkDeliveryMonitor.reset();
        assert !ChunkDeliveryMonitor.isWaiting : "isWaiting should be false after reset";
        assert ChunkDeliveryMonitor.currentWaitMs == 0.0 : "currentWaitMs should be 0 after reset";
        assert ChunkDeliveryMonitor.peakDeliveryTimeMs == 0.0 : "peakDeliveryTimeMs should be 0 after reset";
        assert ChunkDeliveryMonitor.HUD_W == 440 : "HUD_W should be 440";

        boolean hudBefore = ChunkDeliveryMonitor.showOnScreenHud;
        boolean toggled = ZBGamePerf.toggleChunkHud();
        assert toggled == !hudBefore : "toggleChunkHud should invert state";
        assert ZBGamePerf.isChunkHudActive() == toggled : "isChunkHudActive should match toggled state";
        ZBGamePerf.toggleChunkHud(); // restore

        // Test CommandPingMonitor
        CommandPingMonitor.reset();
        assert !CommandPingMonitor.isWaiting : "isWaiting should be false after reset";
        assert CommandPingMonitor.lastRoundTripMs == -1 : "lastRoundTripMs should be -1 after reset";
        assert CommandPingMonitor.totalPongs == 0 : "totalPongs should be 0 after reset";

        CommandPingMonitor.isWaiting = true;
        CommandPingMonitor.lastSentMs = System.currentTimeMillis() - 50;
        CommandPingMonitor.onPongReceived();
        assert !CommandPingMonitor.isWaiting : "isWaiting should be false after pong";
        assert CommandPingMonitor.totalPongs == 1 : "totalPongs should be 1";
        assert CommandPingMonitor.lastRoundTripMs >= 40 : "lastRoundTripMs should be >= 40ms";
        assert CommandPingMonitor.minRoundTripMs == CommandPingMonitor.lastRoundTripMs : "minRoundTripMs should match";
        assert CommandPingMonitor.maxRoundTripMs == CommandPingMonitor.lastRoundTripMs : "maxRoundTripMs should match";
        assert CommandPingMonitor.avgRoundTripMs == (double) CommandPingMonitor.lastRoundTripMs : "avgRoundTripMs should match";

        // Test timeout
        CommandPingMonitor.isWaiting = true;
        CommandPingMonitor.lastSentMs = System.currentTimeMillis() - 6000;
        CommandPingMonitor.update();
        assert !CommandPingMonitor.isWaiting : "should time out after 5s";
        assert CommandPingMonitor.timedOut : "timedOut flag should be true";

        CommandPingMonitor.reset();
        assert !CommandPingMonitor.timedOut : "timedOut should be reset";
        assert CommandPingMonitor.totalPongs == 0 : "totalPongs should be 0 after reset";

        // Test ServerLuaMonitor
        ServerLuaMonitor.reset();
        assert !ServerLuaMonitor.isServerSource() : "initial source should be CLIENT";
        ServerLuaMonitor.toggleSource();
        assert ServerLuaMonitor.isServerSource() : "source should be SERVER after toggle";
        ServerLuaMonitor.toggleSource();
        assert !ServerLuaMonitor.isServerSource() : "source should be CLIENT after 2nd toggle";

        // Test ServerLuaMonitor payload decoding
        String samplePayload = "1;5000000\n" +
            "  12.5ms\t   42\t[GAME]\tISInventoryPane.lua:120\t1.0\t0.6\t0.6\n" +
            "   3.2ms\t   10\t[MOD]\tArmor.lua:50\t1.0\t1.0\t0.6\n";
        ServerLuaMonitor.onDataReceived(samplePayload);
        assert ServerLuaMonitor.hasReceivedData : "hasReceivedData should be true";
        java.util.List<PerfRenderer.FormattedCall> topCalls = ServerLuaMonitor.getTopCalls();
        assert topCalls.size() == 2 : "should unpack 2 calls, got: " + topCalls.size();
        assert topCalls.get(0).pathStr.equals("ISInventoryPane.lua:120") : "path should match";
        assert topCalls.get(0).countStr.equals("   42") : "countStr should match";
        assert topCalls.get(1).prefixStr.equals("[MOD]") : "prefixStr should match";

        long[] srvHist = ServerLuaMonitor.getHistory();
        assert srvHist != null && srvHist.length == 60 : "server history length should be 60";
        assert srvHist[59] == 5000000L : "latest history entry should match sample";

        // Test PerformanceMonitor server auto-shutdown timeout
        PerformanceMonitor.serverLuaProfilingActive = true;
        PerformanceMonitor.lastServerLuaRequestTimeMs = System.currentTimeMillis() - 6000L;
        PerformanceMonitor.serverUpdate();
        assert !PerformanceMonitor.serverLuaProfilingActive : "server profiling should auto-shutdown after 5s timeout";

        ServerLuaMonitor.reset();
        assert !ServerLuaMonitor.hasReceivedData : "hasReceivedData should be reset";
        assert ServerLuaMonitor.getTopCalls().isEmpty() : "topCalls should be empty after reset";

        System.out.println("SelfCheck PASSED successfully!");
    }
}
