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
