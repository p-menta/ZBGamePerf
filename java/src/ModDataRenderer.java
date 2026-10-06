package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

public class ModDataRenderer {
    private static long lastUpdateTime = 0;
    private static int allTimeMaxWidth = 0;
    
    private static class CachedLine {
        String text;
        double r, g, b;

        CachedLine(String text, double r, double g, double b) {
            this.text = text;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private static final List<CachedLine> cachedLines = new ArrayList<>();
    private static int cachedLineSpacing = 0;
    private static int cachedTotalHeight = 0;
    private static int cachedCurrentY = 0;
    private static int cachedBackgroundWidth = 0;
    private static int cachedBackgroundX = 0;

    public static int getRenderY() {
        return cachedCurrentY;
    }

    public static int getTotalHeight() {
        return cachedTotalHeight;
    }

    public static void render() {
        if (!ZBGamePerf.modDataOsdEnabled) {
            return;
        }

        var textMgr = TextManager.instance;
        if (textMgr == null) {
            return;
        }

        var core = Core.getInstance();
        if (core == null) {
            return;
        }

        long currentTime = System.nanoTime();
        long updateIntervalNS = ZBGamePerf.osdUpdateIntervalMS * 1_000_000L;
        boolean shouldUpdate = ((currentTime - lastUpdateTime) >= updateIntervalNS) && !ZBGamePerf.osdFrozen;

        var font = UIFont.CodeSmall;
        int scrH = core.getScreenHeight();
        int x0 = ZBGamePerf.osdX;
        float alpha = ZBGamePerf.osdAlpha;
        float backgroundAlpha = ZBGamePerf.osdBackgroundAlpha;
        int mode = ZBGamePerf.modDataOsdMode;

        if (shouldUpdate || cachedLines.isEmpty()) {
            lastUpdateTime = currentTime;
            cachedLines.clear();

            String header;
            if (mode == 2) {
                header = "GlobalModData [TX Transmit Only]:";
            } else if (mode == 3) {
                header = "GlobalModData [RX Receive Only]:";
            } else {
                header = "GlobalModData (TX & RX) - Rate & Size:";
            }
            cachedLines.add(new CachedLine(header, 1.0, 1.0, 1.0));

            List<ModDataMonitor.ModDataEntry> entries = ModDataMonitor.getEntries(mode);
            if (entries.isEmpty()) {
                cachedLines.add(new CachedLine("  (No GlobalModData activity recorded yet)", 0.7, 0.7, 0.7));
            } else {
                for (ModDataMonitor.ModDataEntry entry : entries) {
                    String prefix = entry.isTx ? "[TX]" : "[RX]";
                    int perMin = entry.getCountLastMinute();
                    double perSec = entry.getRatePerSecond();
                    String lastSize = ModDataMonitor.formatBytes(entry.lastBytes);
                    String totalSize = ModDataMonitor.formatBytes(entry.totalBytes);

                    String line = String.format(java.util.Locale.US,
                        "  %-4s %-20s : %3d/min (%4.1f/s) | Last: %8s | Total: %8s (%d pkts)",
                        prefix, entry.tag, perMin, perSec, lastSize, totalSize, entry.totalCount);

                    double r = entry.isTx ? 0.35 : 0.4;
                    double g = entry.isTx ? 0.85 : 1.0;
                    double b = entry.isTx ? 1.0 : 0.45;
                    cachedLines.add(new CachedLine(line, r, g, b));
                }
            }

            // Measure widest line
            int maxW = 0;
            for (CachedLine cl : cachedLines) {
                int w = textMgr.MeasureStringX(font, cl.text);
                if (w > maxW) maxW = w;
            }
            if (maxW > allTimeMaxWidth) {
                allTimeMaxWidth = maxW;
            }

            int textHeight = textMgr.MeasureStringY(font, "XXX");
            cachedLineSpacing = textHeight + 2;
            cachedTotalHeight = cachedLines.size() * cachedLineSpacing + 4;
            cachedBackgroundWidth = allTimeMaxWidth + 10;
            cachedBackgroundX = x0 - 5;

            // Positioning: stack above Lua OSD if Lua OSD is active, else bottom-left
            if (ZBGamePerf.osdEnabled) {
                cachedCurrentY = PerfRenderer.getRenderY() - cachedTotalHeight - 6;
            } else {
                int y0 = ZBGamePerf.osdY;
                cachedCurrentY = (y0 < 0) ? scrH + y0 - cachedTotalHeight + 1 : y0;
            }
        }

        // Draw dark background
        if (backgroundAlpha > 0.0f) {
            SpriteRenderer.instance.renderRect(cachedBackgroundX, cachedCurrentY, cachedBackgroundWidth, cachedTotalHeight, 0.0f, 0.0f, 0.0f, backgroundAlpha);
        }

        // Draw lines
        int drawY = cachedCurrentY + 2;
        for (CachedLine cl : cachedLines) {
            textMgr.DrawString(font, x0, drawY, cl.text, cl.r, cl.g, cl.b, alpha);
            drawY += cachedLineSpacing;
        }
    }
}
