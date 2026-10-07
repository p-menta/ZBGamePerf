package me.p_menta.zb_game_perf;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.Lua.Event;

public class Patch_LoadGridSquare {

    @Patch(className = "zombie.Lua.Event", methodName = "trigger")
    public static class Patch_Event_trigger {
        @Patch.OnEnter
        public static void enter(@Patch.This Event self, @Patch.Local("isLGS") boolean isLGS, @Patch.Local("startNs") long startNs) {
            if (!ZBGamePerf.loadGridSquareOsdEnabled) return;
            if (self != null && "LoadGridsquare".equalsIgnoreCase(self.name)) {
                isLGS = true;
                startNs = System.nanoTime();
                LoadGridSquareMonitor.onEnterEvent();
            }
        }

        @Patch.OnExit
        public static void exit(@Patch.This Event self, @Patch.Local("isLGS") boolean isLGS, @Patch.Local("startNs") long startNs) {
            if (isLGS) {
                long duration = System.nanoTime() - startNs;
                LoadGridSquareMonitor.onExitEvent(duration);
            }
        }
    }

    @Patch(className = "zombie.Lua.MapObjects", methodName = "loadGridSquare")
    public static class Patch_MapObjects_loadGridSquare {
        @Patch.OnEnter
        public static void enter(@Patch.Local("startNs") long startNs) {
            if (!ZBGamePerf.loadGridSquareOsdEnabled) return;
            startNs = System.nanoTime();
            LoadGridSquareMonitor.onEnterEvent();
        }

        @Patch.OnExit
        public static void exit(@Patch.Local("startNs") long startNs) {
            if (!ZBGamePerf.loadGridSquareOsdEnabled) return;
            long duration = System.nanoTime() - startNs;
            LoadGridSquareMonitor.onExitEvent(duration);
        }
    }
}
