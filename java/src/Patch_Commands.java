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
                    if ("Pong".equals(mc[1])) {
                        CommandPingMonitor.onPongReceived();
                    } else if ("ResLuaCalls".equals(mc[1])) {
                        java.nio.ByteBuffer dup = bb.bb.duplicate();
                        dup.position(startPos);
                        ByteBufferReader r = new ByteBufferReader(dup);
                        r.getUTF(); // module
                        r.getUTF(); // command
                        if (r.getBoolean()) { // hasTable
                            se.krka.kahlua.vm.KahluaTable table = zombie.Lua.LuaManager.platform.newTable();
                            zombie.network.TableNetworkUtils.load(table, r);
                            Object data = table.rawget("data");
                            if (data instanceof String) {
                                ServerLuaMonitor.onDataReceived((String) data);
                            }
                        }
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

    @Patch(className = "zombie.network.GameServer", methodName = "receiveClientCommand")
    public static class Patch_GameServer_receiveClientCommand {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) ByteBufferReader bb, @Patch.Argument(1) zombie.core.raknet.UdpConnection connection) {
            if (bb == null || bb.bb == null || connection == null) {
                return;
            }
            try {
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
                if ("ZBGamePerf".equals(module)) {
                    if ("Ping".equals(command)) {
                        zombie.network.GameServer.sendServerCommand("ZBGamePerf", "Pong", (se.krka.kahlua.vm.KahluaTable) null, connection);
                    } else if ("ReqLuaCalls".equals(command)) {
                        PerformanceMonitor.serverLuaProfilingActive = true;
                        PerformanceMonitor.lastServerLuaRequestTimeMs = System.currentTimeMillis();
                        int reqMode = 1;
                        int topN = 15;
                        if (r.getBoolean()) { // hasTable
                            se.krka.kahlua.vm.KahluaTable reqTable = zombie.Lua.LuaManager.platform.newTable();
                            zombie.network.TableNetworkUtils.load(reqTable, r);
                            Object mObj = reqTable.rawget("mode");
                            if (mObj instanceof Double) reqMode = ((Double) mObj).intValue();
                            Object nObj = reqTable.rawget("topN");
                            if (nObj instanceof Double) topN = ((Double) nObj).intValue();
                        }
                        String payload = PerformanceMonitor.packTopCalls(reqMode, topN);
                        se.krka.kahlua.vm.KahluaTable respTable = zombie.Lua.LuaManager.platform.newTable();
                        respTable.rawset("data", payload);
                        zombie.network.GameServer.sendServerCommand("ZBGamePerf", "ResLuaCalls", respTable, connection);
                    }
                }
            } catch (Throwable ignored) {
            }
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
