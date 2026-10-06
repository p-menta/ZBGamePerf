package me.p_menta.zb_game_perf;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.IConnection;

public class Patch_GlobalModData {

    @Patch(className = "zombie.network.packets.service.GlobalModDataPacket", methodName = "write")
    public static class Patch_write {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) ByteBufferWriter b, @Patch.Local("startPos") int startPos) {
            startPos = (b != null && b.bb != null) ? b.bb.position() : 0;
        }

        @Patch.OnExit
        public static void exit(@Patch.This Object self, @Patch.Argument(0) ByteBufferWriter b, @Patch.Local("startPos") int startPos) {
            if (!ZBGamePerf.modDataOsdEnabled || b == null || b.bb == null) {
                return;
            }
            try {
                int bytes = Math.max(0, b.bb.position() - startPos);
                String tag = ModDataMonitor.getTag(self, b.bb, startPos);
                ModDataMonitor.recordTx(tag, bytes);
            } catch (Throwable ignored) {
            }
        }
    }

    @Patch(className = "zombie.network.packets.service.GlobalModDataPacket", methodName = "parse")
    public static class Patch_parse {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) ByteBufferReader b, @Patch.Local("startPos") int startPos) {
            startPos = (b != null && b.bb != null) ? b.bb.position() : 0;
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) ByteBufferReader b, @Patch.Argument(1) IConnection connection, @Patch.Local("startPos") int startPos) {
            if (!ZBGamePerf.modDataOsdEnabled || b == null || b.bb == null) {
                return;
            }
            try {
                int bytes = Math.max(0, b.bb.position() - startPos);
                String tag = ModDataMonitor.peekTag(b.bb, startPos);
                ModDataMonitor.recordRx(tag, bytes);
            } catch (Throwable ignored) {
            }
        }
    }

    @Patch(className = "zombie.world.moddata.GlobalModData", methodName = "transmit")
    public static class Patch_transmit {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) String tag) {
            if (!ZBGamePerf.modDataOsdEnabled) {
                return;
            }
            // In singleplayer, the game engine does not send network packets; record as SP activity
            if (!GameClient.client && !GameServer.server) {
                ModDataMonitor.recordTx(tag, 0);
            }
        }
    }
}
