package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

public class LoadGridSquareRenderer {
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
        if (!ZBGamePerf.loadGridSquareOsdEnabled) {
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
        int mode = ZBGamePerf.loadGridSquareOsdMode;

        if (shouldUpdate || cachedLines.isEmpty()) {
            lastUpdateTime = currentTime;
            cachedLines.clear();

            String header;
            if (mode == 2) {
                header = "LoadGridSquare Callbacks (Total Time):";
            } else if (mode == 3) {
                header = "LoadGridSquare Callbacks (Most Calls):";
            } else {
                header = "LoadGridSquare Callbacks (Recent Activity):";
            }
            cachedLines.add(new CachedLine(header, 1.0, 1.0, 1.0));

            String statsLine = String.format(java.util.Locale.US,
                "  Squares Loaded: %d | Total Event Time: %.1fms",
                LoadGridSquareMonitor.totalSquaresLoaded,
                LoadGridSquareMonitor.totalEventDurationNs / 1_000_000.0);
            cachedLines.add(new CachedLine(statsLine, 0.8, 0.8, 0.9));

            List<LoadGridSquareMonitor.CallbackEntry> entries = LoadGridSquareMonitor.getEntries(mode);
            if (entries.isEmpty()) {
                cachedLines.add(new CachedLine("  (No LoadGridSquare callbacks registered)", 0.7, 0.7, 0.7));
            } else {
                for (LoadGridSquareMonitor.CallbackEntry entry : entries) {
                    String prefixStr = entry.info.prefix != null ? entry.info.prefix.toString() : "[UNK]";
                    double totalMs = entry.totalDurationNs / 1_000_000.0;
                    double avgUs = entry.totalCount > 0 ? (entry.totalDurationNs / 1_000.0) / entry.totalCount : 0.0;
                    double maxMs = entry.maxDurationNs / 1_000_000.0;
                    double ratePerSec = entry.getRatePerSecond();

                    String location = entry.info.getFormattedLocation();
                    String line = String.format(java.util.Locale.US,
                        "  %-10s %-32s : %6d calls (%4.1f/s) | Total: %6.1fms | Avg: %5.1fus | Max: %5.1fms",
                        prefixStr, location, entry.totalCount, ratePerSec, totalMs, avgUs, maxMs);

                    double r, g, b;
                    if (entry.totalCount == 0) {
                        r = 0.5; g = 0.8; b = 0.7; // idle / registered
                    } else if (totalMs >= 50.0 || maxMs >= 10.0) {
                        r = 1.0; g = 0.4; b = 0.4; // heavy / spike
                    } else if (totalMs >= 10.0 || entry.totalCount >= 500) {
                        r = 1.0; g = 0.85; b = 0.3; // moderate
                    } else {
                        r = 0.4; g = 1.0; b = 0.5; // fast
                    }
                    cachedLines.add(new CachedLine(line, r, g, b));
                }
            }

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

            // Positioning: stack above CommandRenderer, ModDataRenderer, or PerfRenderer if active
            int bottomY;
            if (ZBGamePerf.commandOsdEnabled) {
                bottomY = CommandRenderer.getRenderY();
            } else if (ZBGamePerf.modDataOsdEnabled) {
                bottomY = ModDataRenderer.getRenderY();
            } else if (ZBGamePerf.osdEnabled) {
                bottomY = PerfRenderer.getRenderY();
            } else {
                int y0 = ZBGamePerf.osdY;
                bottomY = (y0 < 0) ? scrH + y0 + 1 : y0 + cachedTotalHeight;
            }
            cachedCurrentY = bottomY - cachedTotalHeight - 6;
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
