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
        assert "Not tested".equals(CommandPingMonitor.status) : "status should be 'Not tested'";

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

        // Test ServerModDataMonitor
        ServerModDataMonitor.reset();
        assert !ServerModDataMonitor.isServerSource() : "ServerModDataMonitor source should default to CLIENT";
        ServerModDataMonitor.toggleSource();
        assert ServerModDataMonitor.isServerSource() : "ServerModDataMonitor source should toggle to SERVER";
        ServerModDataMonitor.toggleSource();
        assert !ServerModDataMonitor.isServerSource() : "ServerModDataMonitor source should toggle back to CLIENT";

        String sampleModDataPayload = "512;1024\n" +
            "TestTag\t1\t10\t2.5\t1024\t20480\t50\n";
        ServerModDataMonitor.onDataReceived(sampleModDataPayload);
        assert ServerModDataMonitor.hasReceivedData : "ServerModDataMonitor hasReceivedData should be true";
        java.util.List<ModDataMonitor.ModDataEntry> mdEntries = ServerModDataMonitor.getEntries();
        assert mdEntries.size() == 1 : "ServerModDataMonitor should have 1 entry";
        assert mdEntries.get(0).tag.equals("TestTag") : "tag should match";
        assert mdEntries.get(0).isTx : "isTx should match";
        assert ServerModDataMonitor.getHistoryTx()[59] == 512L : "historyTx should match";
        assert ServerModDataMonitor.getHistoryRx()[59] == 1024L : "historyRx should match";
        ServerModDataMonitor.reset();
        assert !ServerModDataMonitor.hasReceivedData : "ServerModDataMonitor hasReceivedData reset";

        // Test ServerCommandMonitor
        ServerCommandMonitor.reset();
        assert !ServerCommandMonitor.isServerSource() : "ServerCommandMonitor source should default to CLIENT";
        ServerCommandMonitor.toggleSource();
        assert ServerCommandMonitor.isServerSource() : "ServerCommandMonitor source should toggle to SERVER";
        ServerCommandMonitor.toggleSource();
        assert !ServerCommandMonitor.isServerSource() : "ServerCommandMonitor source should toggle back to CLIENT";

        String sampleCommandPayload = "256;512\n" +
            "TestMod\tCmdA\t0\t5\t1.0\t64\t512\t8\n";
        ServerCommandMonitor.onDataReceived(sampleCommandPayload);
        assert ServerCommandMonitor.hasReceivedData : "ServerCommandMonitor hasReceivedData should be true";
        java.util.List<CommandMonitor.CommandEntry> cmdEntries = ServerCommandMonitor.getEntries();
        assert cmdEntries.size() == 1 : "ServerCommandMonitor should have 1 entry";
        assert cmdEntries.get(0).module.equals("TestMod") : "module should match";
        assert cmdEntries.get(0).command.equals("CmdA") : "command should match";
        assert !cmdEntries.get(0).isTx : "isTx should be false for RX";
        assert ServerCommandMonitor.getHistoryTx()[59] == 256L : "historyTx should match";
        assert ServerCommandMonitor.getHistoryRx()[59] == 512L : "historyRx should match";
        ServerCommandMonitor.reset();
        assert !ServerCommandMonitor.hasReceivedData : "ServerCommandMonitor hasReceivedData reset";

        // Test ServerLgsMonitor
        ServerLgsMonitor.reset();
        assert !ServerLgsMonitor.isServerSource() : "ServerLgsMonitor source should default to CLIENT";
        ServerLgsMonitor.toggleSource();
        assert ServerLgsMonitor.isServerSource() : "ServerLgsMonitor source should toggle to SERVER";
        ServerLgsMonitor.toggleSource();
        assert !ServerLgsMonitor.isServerSource() : "ServerLgsMonitor source should toggle back to CLIENT";

        String sampleLgsPayload = "1200000;45\n" +
            "[MOD]\tMyScript.lua:10\t15\t3.0\t1.2\t80.0\t0.5\n";
        ServerLgsMonitor.onDataReceived(sampleLgsPayload);
        assert ServerLgsMonitor.hasReceivedData : "ServerLgsMonitor hasReceivedData should be true";
        java.util.List<ServerLgsMonitor.FormattedLgsEntry> lgsEntries = ServerLgsMonitor.getEntries();
        assert lgsEntries.size() == 1 : "ServerLgsMonitor should have 1 entry";
        assert lgsEntries.get(0).prefix.equals("[MOD]") : "prefix should match";
        assert lgsEntries.get(0).loc.equals("MyScript.lua:10") : "loc should match";
        assert ServerLgsMonitor.getHistoryDuration()[59] == 1200000L : "historyDuration should match";
        assert ServerLgsMonitor.getHistorySquares()[59] == 45 : "historySquares should match";
        ServerLgsMonitor.reset();
        assert !ServerLgsMonitor.hasReceivedData : "ServerLgsMonitor hasReceivedData reset";

        // Test ServerPoolsMonitor
        ServerPoolsMonitor.reset();
        assert !ServerPoolsMonitor.isServerSource() : "ServerPoolsMonitor source should default to CLIENT";
        ServerPoolsMonitor.toggleSource();
        assert ServerPoolsMonitor.isServerSource() : "ServerPoolsMonitor source should toggle to SERVER";
        ServerPoolsMonitor.toggleSource();
        assert !ServerPoolsMonitor.isServerSource() : "ServerPoolsMonitor source should toggle back to CLIENT";

        String samplePoolsPayload = "150;50;200\n" +
            "IsoZombie\t100\t20\t120\t500\t2000\n";
        ServerPoolsMonitor.onDataReceived(samplePoolsPayload);
        assert ServerPoolsMonitor.hasReceivedData : "ServerPoolsMonitor hasReceivedData should be true";
        ServerNetMonitor.PoolsSnapshot srvPoolSnap = ServerPoolsMonitor.getPoolsSnapshot();
        assert srvPoolSnap.usedObjects == 150L : "usedObjects should match";
        assert srvPoolSnap.freeObjects == 50L : "freeObjects should match";
        assert srvPoolSnap.totalObjects == 200L : "totalObjects should match";
        assert srvPoolSnap.items.size() == 1 : "items size should be 1";
        assert srvPoolSnap.items.get(0).name.equals("IsoZombie") : "item name should match";
        assert ServerPoolsMonitor.getHistoryPoolsUsed()[59] == 150L : "historyPoolsUsed should match";
        ServerPoolsMonitor.reset();
        assert !ServerPoolsMonitor.hasReceivedData : "ServerPoolsMonitor hasReceivedData reset";

        // Test Server auto-shutdown in Patch_Commands
        ModDataMonitor.serverProfilingActive = true;
        CommandMonitor.serverProfilingActive = true;
        LoadGridSquareMonitor.serverProfilingActive = true;
        Patch_Commands.lastServerModDataRequestTimeMs = System.currentTimeMillis() - 6000L;
        Patch_Commands.lastServerCommandsRequestTimeMs = System.currentTimeMillis() - 6000L;
        Patch_Commands.lastServerLgsRequestTimeMs = System.currentTimeMillis() - 6000L;
        Patch_Commands.serverUpdate();
        assert !ModDataMonitor.serverProfilingActive : "ModDataMonitor server profiling should timeout";
        assert !CommandMonitor.serverProfilingActive : "CommandMonitor server profiling should timeout";
        assert !LoadGridSquareMonitor.serverProfilingActive : "LoadGridSquareMonitor server profiling should timeout";

        System.out.println("SelfCheck PASSED successfully!");
    }
}
