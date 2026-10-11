package me.p_menta.zb_game_perf;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.network.ByteBufferReader;
import zombie.network.GameClient;

public class Patch_Commands {

    @Patch(className = "zombie.network.GameClient", methodName = "sendClientCommand")
    public static class Patch_GameClient_sendClientCommand {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) zombie.characters.IsoPlayer player,
                                @Patch.Argument(1) String module,
                                @Patch.Argument(2) String command,
                                @Patch.Argument(3) se.krka.kahlua.vm.KahluaTable args) {
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
        public static void enter(@Patch.Argument(0) ByteBufferReader bb,
                                @Patch.Argument(1) short packetType) {
            if (bb == null || bb.bb == null) {
                return;
            }
            try {
                int pos = bb.bb.position();
                int totalBytes = bb.bb.remaining();
                java.nio.ByteBuffer dup = bb.bb.duplicate();
                dup.position(pos);
                ByteBufferReader r = new ByteBufferReader(dup);
                String module = r.getUTF();
                String command = r.getUTF();
                if ("ZBGamePerf".equals(module)) {
                    if ("Pong".equals(command)) {
                        CommandPingMonitor.onPongReceived();
                    } else if (r.bb != null && r.bb.remaining() > 0 && r.getBoolean()) {
                        se.krka.kahlua.vm.KahluaTable table = zombie.Lua.LuaManager.platform.newTable();
                        zombie.network.TableNetworkUtils.load(table, r);
                        Object data = table.rawget("data");
                        if (data instanceof String) {
                            String dataStr = (String) data;
                            if ("ResLuaCalls".equals(command)) {
                                ServerLuaMonitor.onDataReceived(dataStr);
                            } else if ("ResModData".equals(command)) {
                                ServerModDataMonitor.onDataReceived(dataStr);
                            } else if ("ResCommands".equals(command)) {
                                ServerCommandMonitor.onDataReceived(dataStr);
                            } else if ("ResLgs".equals(command)) {
                                ServerLgsMonitor.onDataReceived(dataStr);
                            } else if ("ResServerWorld".equals(command)) {
                                ServerNetMonitor.onServerWorldReceived(dataStr);
                            } else if ("ResPools".equals(command)) {
                                ServerPoolsMonitor.onDataReceived(dataStr);
                            }
                        }
                    }
                }
                if (ZBGamePerf.commandOsdEnabled) {
                    CommandMonitor.recordRx(module, command, totalBytes);
                }
            } catch (Throwable t) {
                zombie.debug.DebugLog.log("[ZBGamePerf-Client] receiveClientCommand error: " + t);
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
        public static void exit(@Patch.Argument(0) String module,
                                @Patch.Argument(1) String command,
                                @Patch.Argument(2) se.krka.kahlua.vm.KahluaTable args,
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
        public static void enter(@Patch.Argument(0) ByteBufferReader bb,
                                @Patch.Argument(1) zombie.core.raknet.UdpConnection connection,
                                @Patch.Argument(2) short packetType) {
            if (bb == null || bb.bb == null || connection == null) {
                return;
            }
            try {
                serverUpdate();
                if (bb.bb.remaining() < 5) {
                    return;
                }
                int pos = bb.bb.position();
                int totalBytes = bb.bb.remaining();
                java.nio.ByteBuffer dup = bb.bb.duplicate();
                dup.position(pos);
                ByteBufferReader r = new ByteBufferReader(dup);
                r.getByte(); // skip playerIndex
                String module = r.getUTF();
                String command = r.getUTF();
                if (CommandMonitor.serverProfilingActive) {
                    CommandMonitor.recordRx(module, command, totalBytes);
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
            } catch (Throwable t) {
                zombie.debug.DebugLog.log("[ZBGamePerf-Server] receiveClientCommand error: " + t);
            }
        }
    }

    public static int[] parseModeTopN(ByteBufferReader r, int defMode, int defTopN) {
        int reqMode = defMode;
        int topN = defTopN;
        try {
            if (r.bb != null && r.bb.remaining() > 0 && r.getBoolean()) {
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

    public static void sendServerResponse(String command, String payload, zombie.core.raknet.UdpConnection connection) {
        try {
            se.krka.kahlua.vm.KahluaTable respTable = zombie.Lua.LuaManager.platform.newTable();
            respTable.rawset("data", payload != null ? payload : "");
            zombie.network.GameServer.sendServerCommand("ZBGamePerf", command, respTable, connection);
        } catch (Throwable t) {
            zombie.debug.DebugLog.log("[ZBGamePerf-Server] sendServerResponse error: " + t);
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
        public static void enter(@Patch.Argument(0) ByteBufferReader bb) {
            if (!ZBGamePerf.commandOsdEnabled || bb == null || bb.bb == null) {
                return;
            }
            try {
                int pos = bb.bb.position();
                int totalBytes = bb.bb.remaining();
                String[] mc = CommandMonitor.peekModuleCommand(bb.bb, pos);
                CommandMonitor.recordRx(mc[0], mc[1], totalBytes);
            } catch (Throwable ignored) {
            }
        }
    }
}
