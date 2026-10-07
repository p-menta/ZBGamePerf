package me.p_menta.zb_game_perf;

import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.input.GameKeyboard;
import zombie.ui.TextManager;
import zombie.ui.UIFont;
import org.lwjglx.input.Keyboard;
import java.util.List;

public class PerfRenderer {
    private static long lastUpdateTime = 0;
    private static List<FormattedCall> cachedTopCalls = new java.util.ArrayList<>();
    private static long cachedWindowDurationMS = 0;
    private static int allTimeMaxWidth = 0;
    private static int maxPrefixWidth = 0; // Track maximum prefix column width
    
    // Cached expensive-to-compute values (only recalculate when data or settings change)
    private static int cachedTopN = 0;
    private static int cachedX0 = Integer.MIN_VALUE;
    private static int cachedY0 = Integer.MIN_VALUE;
    private static int cachedScreenHeight = 0;
    private static String cachedLongestLine = "";
    
    // Cached rendering values (computed only when shouldUpdate is true)
    private static int cachedLineSpacing = 0;
    private static int cachedTotalHeight = 0;
    private static int cachedCurrentY = 0;
    private static int cachedBackgroundWidth = 0;
    private static int cachedBackgroundX = 0;
    
    // Pre-rendered line cache with colors
    private static class CachedLine {
        String text;
        double r, g, b; // RGB color values
        
        CachedLine(String text, double r, double g, double b) {
            this.text = text;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }
    private static List<CachedLine> cachedLines = new java.util.ArrayList<>();
    
    // Helper class to hold formatted call data with color info
    private static class FormattedCall {
        String timeStr;
        String countStr;
        String prefixStr;
        String pathStr;
        double r, g, b; // RGB color values
        
        FormattedCall(String timeStr, String countStr, String prefixStr, String pathStr, double r, double g, double b) {
            this.timeStr = timeStr;
            this.countStr = countStr;
            this.prefixStr = prefixStr;
            this.pathStr = pathStr;
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        String getFullLine(int prefixWidth) {
            // Columns: time (variable, right-aligned), count (5 chars, right-aligned), 
            // prefix (variable width, left-aligned), path (left-aligned, variable width)
            String formattedPrefix = String.format("%-" + prefixWidth + "s", prefixStr);
            return timeStr + "  " + countStr + "  " + formattedPrefix + " " + pathStr;
        }
    }

    public static int getRenderY() {
        return cachedCurrentY;
    }

    public static int getTotalHeight() {
        return cachedTotalHeight;
    }

    private static boolean wasCtrl1Down = false;
    private static boolean wasCtrl2Down = false;
    private static boolean wasCtrl3Down = false;
    private static boolean wasCtrl4Down = false;
    private static boolean wasCtrl0Down = false;

    private static void checkHotkeys() {
        try {
            boolean isCtrl = GameKeyboard.isKeyDown(Keyboard.KEY_LCONTROL) || GameKeyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            boolean is1 = GameKeyboard.isKeyDown(Keyboard.KEY_1);
            boolean is2 = GameKeyboard.isKeyDown(Keyboard.KEY_2);
            boolean is3 = GameKeyboard.isKeyDown(Keyboard.KEY_3);
            boolean is4 = GameKeyboard.isKeyDown(Keyboard.KEY_4);
            boolean is0 = GameKeyboard.isKeyDown(Keyboard.KEY_0);

            boolean ctrl1 = isCtrl && is1;
            if (ctrl1 && !wasCtrl1Down) {
                ZBGamePerf.cycleOSDMode();
            }
            wasCtrl1Down = ctrl1;

            boolean ctrl2 = isCtrl && is2;
            if (ctrl2 && !wasCtrl2Down) {
                ZBGamePerf.cycleModDataMode();
            }
            wasCtrl2Down = ctrl2;

            boolean ctrl3 = isCtrl && is3;
            if (ctrl3 && !wasCtrl3Down) {
                ZBGamePerf.cycleCommandMode();
            }
            wasCtrl3Down = ctrl3;

            boolean ctrl4 = isCtrl && is4;
            if (ctrl4 && !wasCtrl4Down) {
                ZBGamePerf.cycleLoadGridSquareMode();
            }
            wasCtrl4Down = ctrl4;

            boolean ctrl0 = isCtrl && is0;
            if (ctrl0 && !wasCtrl0Down) {
                ZBGamePerf.resetStats();
            }
            wasCtrl0Down = ctrl0;
        } catch (Throwable ignored) {
        }
    }

    public static void render() {
        checkHotkeys();

        // Don't render if OSD is disabled
        if (!ZBGamePerf.osdEnabled) {
            return;
        }

        if (PerformanceMonitor.trackInternalPerformance) {
            long renderStartNs = System.nanoTime();
            renderInternal();
            long renderDurationNs = System.nanoTime() - renderStartNs;
            
            int slowKey = -2;
            PerformanceMonitor.recordInternalPerformance(slowKey, renderStartNs, renderDurationNs);
        } else {
            renderInternal();
        }
    }
    
    private static void renderInternal() {
        var textMgr = TextManager.instance;
        if (textMgr == null) {
            return;
        }

        var core = Core.getInstance();
        if (core == null) {
            return;
        }

        // Check if we need to update based on update interval
        long currentTime = System.nanoTime();
        long updateIntervalNS = ZBGamePerf.osdUpdateIntervalMS * 1_000_000L; // Convert ms to ns
        boolean shouldUpdate = ((currentTime - lastUpdateTime) >= updateIntervalNS) && !ZBGamePerf.osdFrozen;

        // Get parameters from ZBGamePerf
        int topN = ZBGamePerf.osdTopN;
        long windowDurationMS = ZBGamePerf.osdWindowMS;
        float alpha = ZBGamePerf.osdAlpha;
        float backgroundAlpha = ZBGamePerf.osdBackgroundAlpha;
        int x0 = ZBGamePerf.osdX;
        int y0 = ZBGamePerf.osdY;

        var font = UIFont.CodeSmall;
        var scrH = core.getScreenHeight();

        // Update cached data only when update interval has elapsed
        if (shouldUpdate) {
            cachedTopCalls = getTopCalls(topN, windowDurationMS);
            cachedWindowDurationMS = windowDurationMS;
            lastUpdateTime = currentTime;
            
            // Store settings for reference
            cachedTopN = topN;
            cachedX0 = x0;
            cachedY0 = y0;
            cachedScreenHeight = scrH;

            // Pre-format all lines with colors and find longest (expensive operations)
            String header;
            if (ZBGamePerf.osdMode == 3) {
                header = "Top " + topN + " Lua Calls (Most Calls):";
            } else if (ZBGamePerf.osdMode == 2) {
                header = "Top " + topN + " Lua Calls (Total Time):";
            } else {
                header = "Top " + topN + " Lua Calls (last " + (windowDurationMS / 1000) + "s):";
            }
            cachedLines.clear();
            // Add header as first line (white color)
            cachedLines.add(new CachedLine(header, 1.0, 1.0, 1.0));
            cachedLongestLine = header;
            // Add formatted call lines with their colors
            for (FormattedCall call : cachedTopCalls) {
                String line = call.getFullLine(maxPrefixWidth);
                cachedLines.add(new CachedLine(line, call.r, call.g, call.b));
                if (line.length() > cachedLongestLine.length()) {
                    cachedLongestLine = line;
                }
            }
            // Measure longest line width (expensive operation)
            int maxWidth = textMgr.MeasureStringX(font, cachedLongestLine);
            if (maxWidth > allTimeMaxWidth) {
                allTimeMaxWidth = maxWidth;
            }
            
            // Compute and cache all rendering values
            int textHeight = textMgr.MeasureStringY(font, "XXX");
            cachedLineSpacing = textHeight + 2;
            cachedTotalHeight = (topN + 1) * cachedLineSpacing;
            cachedCurrentY = (y0 < 0) ? scrH + y0 - cachedTotalHeight + 1 : y0;
            cachedBackgroundWidth = allTimeMaxWidth + 10;
            cachedBackgroundX = x0 - 5;
        }

        // Draw dark background if opacity > 0
        if (backgroundAlpha > 0.0f) {
            SpriteRenderer.instance.renderRect(cachedBackgroundX, cachedCurrentY, cachedBackgroundWidth, cachedTotalHeight, 0.0f, 0.0f, 0.0f, backgroundAlpha);
        }

        // Draw all pre-rendered lines from cache
        int drawY = cachedCurrentY;
        for (CachedLine cachedLine : cachedLines) {
            textMgr.DrawString(font, x0, drawY, cachedLine.text, cachedLine.r, cachedLine.g, cachedLine.b, alpha);
            drawY += cachedLineSpacing;
        }
    }
        
    // Get top N calls for rendering (returns a list of formatted call data)
    public static java.util.List<FormattedCall> getTopCalls(int topN, long windowDurationMS) {
        java.util.List<FormattedCall> result = new java.util.ArrayList<>();
        double minTimeMS = ZBGamePerf.osdMinTimeMS;
        int[] currentMaxPrefixWidth = {0}; // Use array to allow modification in lambda
        
        boolean isTotalMode = (ZBGamePerf.osdMode == 2);
        boolean isCountMode = (ZBGamePerf.osdMode == 3);
        boolean isCumulative = isTotalMode || isCountMode;

        java.util.List<PerformanceMonitor.StatsEntryWithWindow> entries;
        if (isCountMode) {
            entries = PerformanceMonitor.getTopEntriesCount(topN);
        } else if (isTotalMode) {
            entries = PerformanceMonitor.getTopEntriesTotal(topN);
        } else {
            entries = PerformanceMonitor.getTopEntries(windowDurationMS, topN);
        }

        entries.forEach(entry -> {
            FileInfo info = entry.info;
            double totalMs = isCumulative ? entry.stats.getTotalSum() : entry.windowStats.getTotalSumMs();
            long count = isCumulative ? entry.stats.getCount() : entry.windowStats.count;
            
            // Filter out entries below minimum time threshold (only in windowed/total time modes)
            if (!isCountMode && totalMs < minTimeMS) {
                return;
            }
            
            String timeStr = (totalMs >= 1000.0)
                ? String.format("%7.2fs", totalMs / 1000.0)
                : String.format("%8.1fms", totalMs);

            double r, g, b;
            if (isCountMode) {
                if (count >= 10_000) {
                    r = 1.0; g = 0.6; b = 0.6;
                } else if (count >= 1_000) {
                    r = 1.0; g = 1.0; b = 0.6;
                } else {
                    r = 1.0; g = 1.0; b = 1.0;
                }
            } else {
                if (totalMs > (isTotalMode ? 5000.0 : 500.0)) {
                    r = 1.0; g = 0.6; b = 0.6;
                } else if (totalMs > (isTotalMode ? 1000.0 : 100.0)) {
                    r = 1.0; g = 1.0; b = 0.6;
                } else {
                    r = 1.0; g = 1.0; b = 1.0;
                }
            }
            
            String countStr = String.format("%5d", count);
            
            // Extract first folder from path for LMOD/SMOD/WORKSHOP, otherwise use prefix
            FilePrefix prefix = info.prefix != null ? info.prefix : FilePrefix.UNK;
            String pathToDisplay = info.relativePath;
            String prefixStr;
            
            if (prefix == FilePrefix.INTERNAL) {
                prefixStr = "ZBGamePerf";
            } else if (prefix == FilePrefix.LMOD || prefix == FilePrefix.SMOD || prefix == FilePrefix.WMOD) {
                String normalizedPath = pathToDisplay.replace('\\', '/');
                int firstSlash = normalizedPath.indexOf('/');
                if (firstSlash > 0) {
                    String firstFolder = normalizedPath.substring(0, firstSlash);
                    pathToDisplay = normalizedPath.substring(firstSlash + 1);
                    prefixStr = firstFolder;
                } else {
                    prefixStr = prefix.name();
                }
            } else {
                prefixStr = prefix.name();
            }
            
            int prefixLen = prefixStr.length();
            if (prefixLen > currentMaxPrefixWidth[0]) {
                currentMaxPrefixWidth[0] = prefixLen;
            }
            
            String fileDisplay;
            if (info.line > 0 || (pathToDisplay.contains("/") || pathToDisplay.contains("\\"))) {
                fileDisplay = pathToDisplay + ":" + info.line;
            } else {
                fileDisplay = pathToDisplay;
            }
            String pathStr = fileDisplay.length() > 80 ? fileDisplay.substring(0, 77) + "..." : fileDisplay;
            
            result.add(new FormattedCall(timeStr, countStr, prefixStr, pathStr, r, g, b));
        });
        
        // Update global max prefix width (remember longest size)
        if (currentMaxPrefixWidth[0] > maxPrefixWidth) {
            maxPrefixWidth = currentMaxPrefixWidth[0];
        }
        
        return result;
    }
}
