package me.p_menta.zb_game_perf;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.ui.UITransition", methodName = "UpdateAll")
public class Patch_UITransition {
    @Patch.OnEnter
    public static void enter() {
        PerfRenderer.render();
        ModDataRenderer.render();
        CommandRenderer.render();
        LoadGridSquareRenderer.render();
    }
}
