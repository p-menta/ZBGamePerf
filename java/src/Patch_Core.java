package me.p_menta.zb_game_perf;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.Core", methodName = "ResetLua")
public class Patch_Core {
    @Patch.OnEnter
    public static void enter() {
        PerformanceMonitor.reset();
    }
}
