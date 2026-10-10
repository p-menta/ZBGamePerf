package me.p_menta.zb_game_perf;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.network.ByteBufferReader;
import zombie.network.GameClient;

public class Patch_Commands {

    @Patch(className = "zombie.network.GameClient", methodName = "sendClientCommand")
    public static class Patch_GameClient_sendClientCommand {
        @Patch.OnExit
        public static void exit(@Patch.Argument(1) String module, @Patch.Argument(2) String command) {
            if (!ZBGamePerf.commandOsdEnabled) {
                return;
            }
            try {
                int bytes = 0;
                if (GameClient.connection != null) {
                    bytes = CommandMonitor.getConnectionByteCount(GameClient.connection);
                }
                CommandMonitor.recordTx(module, command, bytes);
            } catch (Throwable ignored) {
            }
        }
    }

    @Patch(className = "zombie.network.GameClient", methodName = "receiveClientCommand")
    public static class Patch_GameClient_receiveClientCommand {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) ByteBufferReader bb, @Patch.Local("startPos") int startPos) {
            startPos = (bb != null && bb.bb != null) ? bb.bb.position() : 0;
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) ByteBufferReader bb, @Patch.Local("startPos") int startPos) {
            if (bb == null || bb.bb == null) {
                return;
            }
            try {
                String[] mc = CommandMonitor.peekModuleCommand(bb.bb, startPos);
                if ("ZBGamePerf".equals(mc[0])) {
                    String cmd = mc[1];
                    if ("Pong".equals(cmd)) {
                        CommandPingMonitor.onPongReceived();
                    } else if ("ResLuaCalls".equals(cmd)) {
                        String data = unpackDataString(bb.bb, startPos);
                        if (data != null) ServerLuaMonitor.onDataReceived(data);
                    } else if ("ResModData".equals(cmd)) {
                        String data = unpackDataString(bb.bb, startPos);
                        if (data != null) ServerModDataMonitor.onDataReceived(data);
                    } else if ("ResCommands".equals(cmd)) {
                        String data = unpackDataString(bb.bb, startPos);
                        if (data != null) ServerCommandMonitor.onDataReceived(data);
                    } else if ("ResLgs".equals(cmd)) {
                        String data = unpackDataString(bb.bb, startPos);
                        if (data != null) ServerLgsMonitor.onDataReceived(data);
                    } else if ("ResServerWorld".equals(cmd)) {
                        String data = unpackDataString(bb.bb, startPos);
                        if (data != null) ServerNetMonitor.onServerWorldReceived(data);
                    } else if ("ResPools".equals(cmd)) {
                        String data = unpackDataString(bb.bb, startPos);
                        if (data != null) ServerPoolsMonitor.onDataReceived(data);
                    }
                }
                if (ZBGamePerf.commandOsdEnabled) {
                    int bytes = Math.max(0, bb.bb.position() - startPos);
                    CommandMonitor.recordRx(mc[0], mc[1], bytes);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static long lastServerModDataRequestTimeMs = 0;
    public static long lastServerCommandsRequestTimeMs = 0;
    public static long lastServerLgsRequestTimeMs = 0;
    public static long lastServerWorldRequestTimeMs = 0;
    public static long lastServerPoolsRequestTimeMs = 0;

    public static void serverUpdate() {
        long now = System.currentTimeMillis();
        PerformanceMonitor.serverUpdate();
        if (ModDataMonitor.serverProfilingActive && (now - lastServerModDataRequestTimeMs > 5000L)) {
            ModDataMonitor.serverProfilingActive = false;
        }
        if (CommandMonitor.serverProfilingActive && (now - lastServerCommandsRequestTimeMs > 5000L)) {
            CommandMonitor.serverProfilingActive = false;
        }
        if (LoadGridSquareMonitor.serverProfilingActive && (now - lastServerLgsRequestTimeMs > 5000L)) {
            LoadGridSquareMonitor.serverProfilingActive = false;
        }
    }

    @Patch(className = "zombie.network.GameServer", methodName = "sendServerCommand")
    public static class Patch_GameServer_sendServerCommand {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) String module, @Patch.Argument(1) String command,
                                @Patch.Argument(3) zombie.core.raknet.UdpConnection connection) {
            if (!CommandMonitor.serverProfilingActive) return;
            try {
                int bytes = 0;
                if (connection != null) {
                    bytes = CommandMonitor.getConnectionByteCount(connection);
                }
                CommandMonitor.recordTx(module, command, bytes);
            } catch (Throwable ignored) {
            }
        }
    }

    @Patch(className = "zombie.network.GameServer", methodName = "receiveClientCommand")
    public static class Patch_GameServer_receiveClientCommand {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) ByteBufferReader bb, @Patch.Argument(1) zombie.core.raknet.UdpConnection connection) {
            if (bb == null || bb.bb == null || connection == null) {
                return;
            }
            try {
                serverUpdate();
                if (bb.bb.remaining() < 5) {
                    return;
                }
                int pos = bb.bb.position();
                java.nio.ByteBuffer dup = bb.bb.duplicate();
                dup.position(pos);
                ByteBufferReader r = new ByteBufferReader(dup);
                r.getByte(); // skip playerIndex
                String module = r.getUTF();
                String command = r.getUTF();
                if (CommandMonitor.serverProfilingActive) {
                    int bytes = Math.max(0, bb.bb.position() - pos);
                    CommandMonitor.recordRx(module, command, bytes);
                }
                if ("ZBGamePerf".equals(module)) {
                    long now = System.currentTimeMillis();
                    if ("Ping".equals(command)) {
                        zombie.network.GameServer.sendServerCommand("ZBGamePerf", "Pong", (se.krka.kahlua.vm.KahluaTable) null, connection);
                    } else if ("ReqLuaCalls".equals(command)) {
                        PerformanceMonitor.serverLuaProfilingActive = true;
                        PerformanceMonitor.lastServerLuaRequestTimeMs = now;
                        int[] req = parseModeTopN(r, 1, 15);
                        String payload = PerformanceMonitor.packTopCalls(req[0], req[1]);
                        sendServerResponse("ResLuaCalls", payload, connection);
                    } else if ("ReqModData".equals(command)) {
                        ModDataMonitor.serverProfilingActive = true;
                        lastServerModDataRequestTimeMs = now;
                        int[] req = parseModeTopN(r, 1, 15);
                        String payload = ModDataMonitor.packEntries(req[0], req[1]);
                        sendServerResponse("ResModData", payload, connection);
                    } else if ("ReqCommands".equals(command)) {
                        CommandMonitor.serverProfilingActive = true;
                        lastServerCommandsRequestTimeMs = now;
                        int[] req = parseModeTopN(r, 1, 15);
                        String payload = CommandMonitor.packEntries(req[0], req[1]);
                        sendServerResponse("ResCommands", payload, connection);
                    } else if ("ReqLgs".equals(command)) {
                        LoadGridSquareMonitor.serverProfilingActive = true;
                        lastServerLgsRequestTimeMs = now;
                        int[] req = parseModeTopN(r, 1, 15);
                        String payload = LoadGridSquareMonitor.packEntries(req[0], req[1]);
                        sendServerResponse("ResLgs", payload, connection);
                    } else if ("ReqServerWorld".equals(command)) {
                        lastServerWorldRequestTimeMs = now;
                        String payload = ServerNetMonitor.packServerWorld();
                        sendServerResponse("ResServerWorld", payload, connection);
                    } else if ("ReqPools".equals(command)) {
                        lastServerPoolsRequestTimeMs = now;
                        int[] req = parseModeTopN(r, 1, 20);
                        String payload = ServerNetMonitor.packPools(req[0], req[1]);
                        sendServerResponse("ResPools", payload, connection);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static String unpackDataString(java.nio.ByteBuffer bb, int startPos) {
        try {
            java.nio.ByteBuffer dup = bb.duplicate();
            dup.position(startPos);
            ByteBufferReader r = new ByteBufferReader(dup);
            r.getUTF(); // module
            r.getUTF(); // command
            if (r.getBoolean()) { // hasTable
                se.krka.kahlua.vm.KahluaTable table = zombie.Lua.LuaManager.platform.newTable();
                zombie.network.TableNetworkUtils.load(table, r);
                Object data = table.rawget("data");
                if (data instanceof String) {
                    return (String) data;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int[] parseModeTopN(ByteBufferReader r, int defMode, int defTopN) {
        int reqMode = defMode;
        int topN = defTopN;
        try {
            if (r.getBoolean()) {
                se.krka.kahlua.vm.KahluaTable reqTable = zombie.Lua.LuaManager.platform.newTable();
                zombie.network.TableNetworkUtils.load(reqTable, r);
                Object mObj = reqTable.rawget("mode");
                if (mObj instanceof Double) reqMode = ((Double) mObj).intValue();
                Object nObj = reqTable.rawget("topN");
                if (nObj instanceof Double) topN = ((Double) nObj).intValue();
            }
        } catch (Throwable ignored) {
        }
        return new int[] { reqMode, topN };
    }

    private static void sendServerResponse(String command, String payload, zombie.core.raknet.UdpConnection connection) {
        try {
            se.krka.kahlua.vm.KahluaTable respTable = zombie.Lua.LuaManager.platform.newTable();
            respTable.rawset("data", payload != null ? payload : "");
            zombie.network.GameServer.sendServerCommand("ZBGamePerf", command, respTable, connection);
        } catch (Throwable ignored) {
        }
    }

    @Patch(className = "zombie.spnetwork.SinglePlayerClient", methodName = "sendClientCommand")
    public static class Patch_SinglePlayerClient_sendClientCommand {
        @Patch.OnExit
        public static void exit(@Patch.Argument(1) String module, @Patch.Argument(2) String command) {
            if (!ZBGamePerf.commandOsdEnabled) {
                return;
            }
            CommandMonitor.recordTx(module, command, 0);
        }
    }

    @Patch(className = "zombie.spnetwork.SinglePlayerClient", methodName = "receiveServerCommand")
    public static class Patch_SinglePlayerClient_receiveServerCommand {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) ByteBufferReader bb, @Patch.Local("startPos") int startPos) {
            startPos = (bb != null && bb.bb != null) ? bb.bb.position() : 0;
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) ByteBufferReader bb, @Patch.Local("startPos") int startPos) {
            if (!ZBGamePerf.commandOsdEnabled || bb == null || bb.bb == null) {
                return;
            }
            try {
                int bytes = Math.max(0, bb.bb.position() - startPos);
                String[] mc = CommandMonitor.peekModuleCommand(bb.bb, startPos);
                CommandMonitor.recordRx(mc[0], mc[1], bytes);
            } catch (Throwable ignored) {
            }
        }
    }
}
