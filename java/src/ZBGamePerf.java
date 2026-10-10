package me.p_menta.zb_game_perf;

import me.zed_0xff.zombie_buddy.Exposer;

@Exposer.LuaClass
public class ZBGamePerf {
    public static int osdX = 0;
    public static int osdY = -1;
    public static float osdAlpha = 0.7f;
    public static float osdBackgroundAlpha = 0.5f;
    public static int osdWindowMS = 1_000;
    public static int osdUpdateIntervalMS = 1_000;
    public static int osdTopN = 10;
    public static double osdMinTimeMS = 0.1; // Minimum time in milliseconds to show in OSD

    public static void setOSDRenderX(int x) {
        osdX = x;
    }

    public static void setOSDRenderY(int y) {
        osdY = y;
    }

    public static void setOSDAlpha(float alpha) {
        osdAlpha = alpha;
    }

    public static void setOSDBackgroundAlpha(float alpha) {
        osdBackgroundAlpha = alpha;
    }

    public static void setOSDWindowMS(int windowMS) {
        osdWindowMS = windowMS;
    }

    public static void setOSDUpdateIntervalMS(int updateIntervalMS) {   
        osdUpdateIntervalMS = updateIntervalMS;
    }

    public static void setOSDTopN(int topN) {
        osdTopN = topN;
    }

    public static void setOSDMinTimeMS(double minTimeMS) {
        osdMinTimeMS = minTimeMS;
    }

    public static int logIntervalSeconds     = 5;
    public static long minTimeMicroseconds   = 10; // 10 microseconds = 10,000 nanoseconds
    public static int osdMode                = 1; // 1 = Recent Window (1s), 2 = Total Time, 3 = Call Count
    public static boolean osdEnabled         = false; // Do not store/render data while closed
    public static boolean osdFrozen          = false;
    public static boolean logWhenOSDOff      = false;
    public static boolean excludeGameEntries = false;

    public static void setLogIntervalSeconds(int seconds) {
        logIntervalSeconds = seconds;
        PerformanceMonitor.logIntervalSeconds = seconds;
    }

    public static void setLogEnabled(boolean enabled) {
        PerformanceMonitor.logEnabled = enabled;
    }

    public static void setMinTimeMicroseconds(long microseconds) {
        minTimeMicroseconds = microseconds;
        Patch_LuaCaller.minTimeNS = microseconds * 1000; // Convert to nanoseconds
    }

    public static void setOSDEnabled(boolean enabled) {
        osdEnabled = enabled;
    }

    public static void setOSDMode(int mode) {
        osdMode = (mode >= 1 && mode <= 3) ? mode : 1;
    }

    public static int getOSDMode() {
        return osdMode;
    }

    public static int cycleOSDMode() {
        osdMode = (osdMode % 3) + 1;
        return osdMode;
    }

    public static int modDataOsdMode = 1; // 1 = TX & RX, 2 = TX only, 3 = RX only
    public static boolean modDataOsdEnabled = false;

    public static int cycleModDataMode() {
        modDataOsdMode = (modDataOsdMode % 3) + 1;
        return modDataOsdMode;
    }

    public static void setModDataOsdMode(int mode) {
        modDataOsdMode = (mode >= 1 && mode <= 3) ? mode : 1;
    }

    public static int getModDataOsdMode() {
        return modDataOsdMode;
    }

    public static boolean getModDataOsdEnabled() {
        return modDataOsdEnabled;
    }

    public static int commandOsdMode = 1; // 1 = TX & RX, 2 = TX only, 3 = RX only
    public static boolean commandOsdEnabled = false;

    public static int cycleCommandMode() {
        commandOsdMode = (commandOsdMode % 3) + 1;
        return commandOsdMode;
    }

    public static void setCommandOsdMode(int mode) {
        commandOsdMode = (mode >= 1 && mode <= 3) ? mode : 1;
    }

    public static int getCommandOsdMode() {
        return commandOsdMode;
    }

    public static boolean getCommandOsdEnabled() {
        return commandOsdEnabled;
    }

    public static int loadGridSquareOsdMode = 1; // 1 = Recent Activity, 2 = Total Time, 3 = Most Calls
    public static boolean loadGridSquareOsdEnabled = false;

    public static int cycleLoadGridSquareMode() {
        loadGridSquareOsdMode = (loadGridSquareOsdMode % 3) + 1;
        return loadGridSquareOsdMode;
    }

    public static void setLoadGridSquareOsdMode(int mode) {
        loadGridSquareOsdMode = (mode >= 1 && mode <= 3) ? mode : 1;
    }

    public static int getLoadGridSquareOsdMode() {
        return loadGridSquareOsdMode;
    }

    public static boolean getLoadGridSquareOsdEnabled() {
        return loadGridSquareOsdEnabled;
    }

    public static void resetStats() {
        PerformanceMonitor.reset();
        ModDataMonitor.reset();
        CommandMonitor.reset();
        LoadGridSquareMonitor.reset();
        ServerNetMonitor.reset();
        ChunkDeliveryMonitor.reset();
        CommandPingMonitor.reset();
        ServerLuaMonitor.reset();
        ServerModDataMonitor.reset();
        ServerCommandMonitor.reset();
        ServerLgsMonitor.reset();
        ServerPoolsMonitor.reset();
    }

    public static void setLogWhenOSDOff(boolean enabled) {
        logWhenOSDOff = enabled;
    }

    public static boolean getOSDEnabled() {
        return osdEnabled;
    }

    public static boolean toggleOSD() {
        UnifiedPerfUI.isOpen = !UnifiedPerfUI.isOpen;
        if (UnifiedPerfUI.isOpen) {
            UnifiedPerfUI.activeTab = 0;
        }
        UnifiedPerfUI.syncProfilingState();
        return UnifiedPerfUI.isOpen;
    }

    public static boolean toggleOSDFreeze() {
        osdFrozen = !osdFrozen;
        return osdFrozen;
    }

    public static boolean getLogWhenOSDOff() {
        return logWhenOSDOff;
    }

    public static void setExcludeGameEntries(boolean exclude) {
        excludeGameEntries = exclude;
        // Clear excluded keys when setting is turned off, so GAME entries can be tracked again
        if (!exclude) {
            PerformanceMonitor.clearExcludedSlowKeys();
        }
    }

    public static boolean getExcludeGameEntries() {
        return excludeGameEntries;
    }

    public static void setTrackInternalPerformance(boolean enabled) {
        PerformanceMonitor.trackInternalPerformance = enabled;
    }

    public static boolean getTrackInternalPerformance() {
        return PerformanceMonitor.trackInternalPerformance;
    }

    public static boolean isProfileAllTabs() {
        return UnifiedPerfUI.profileAllTabs;
    }

    public static boolean toggleProfileAllTabs() {
        UnifiedPerfUI.toggleAllTabsProfiling();
        return UnifiedPerfUI.profileAllTabs;
    }

    public static boolean toggleChunkHud() {
        ChunkDeliveryMonitor.showOnScreenHud = !ChunkDeliveryMonitor.showOnScreenHud;
        return ChunkDeliveryMonitor.showOnScreenHud;
    }

    public static boolean isChunkHudActive() {
        return ChunkDeliveryMonitor.showOnScreenHud;
    }
}
