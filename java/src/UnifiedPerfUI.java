package me.p_menta.zb_game_perf;

import java.util.List;
import org.lwjglx.input.Keyboard;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.input.GameKeyboard;
import zombie.input.Mouse;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

public class UnifiedPerfUI {
    public static boolean isOpen = false;
    public static int activeTab = 0; // 0=Lua, 1=ModData, 2=Commands, 3=LoadGridSquare

    private static int winX = 60;
    private static int winY = 60;
    private static final int WIN_W = 890;
    private static final int WIN_H = 530;

    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;
    private static boolean wasMouseDown = false;

    private static boolean wasCtrl1 = false;
    private static boolean wasCtrl2 = false;
    private static boolean wasCtrl3 = false;
    private static boolean wasCtrl4 = false;
    private static boolean wasCtrl0 = false;

    private static final String[] TABS = {
        "1. Lua Calls",
        "2. GlobalModData",
        "3. Commands",
        "4. LoadGridSquare"
    };

    public static void checkHotkeys() {
        try {
            boolean isCtrl = GameKeyboard.isKeyDown(Keyboard.KEY_LCONTROL) || GameKeyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            boolean is1 = GameKeyboard.isKeyDown(Keyboard.KEY_1);
            boolean is2 = GameKeyboard.isKeyDown(Keyboard.KEY_2);
            boolean is3 = GameKeyboard.isKeyDown(Keyboard.KEY_3);
            boolean is4 = GameKeyboard.isKeyDown(Keyboard.KEY_4);
            boolean is0 = GameKeyboard.isKeyDown(Keyboard.KEY_0);

            if (isCtrl && is1 && !wasCtrl1) toggleTab(0);
            wasCtrl1 = isCtrl && is1;

            if (isCtrl && is2 && !wasCtrl2) toggleTab(1);
            wasCtrl2 = isCtrl && is2;

            if (isCtrl && is3 && !wasCtrl3) toggleTab(2);
            wasCtrl3 = isCtrl && is3;

            if (isCtrl && is4 && !wasCtrl4) toggleTab(3);
            wasCtrl4 = isCtrl && is4;

            if (isCtrl && is0 && !wasCtrl0) ZBGamePerf.resetStats();
            wasCtrl0 = isCtrl && is0;
        } catch (Throwable ignored) {
        }
    }

    private static void toggleTab(int tab) {
        if (!isOpen) {
            isOpen = true;
            activeTab = tab;
        } else if (activeTab == tab) {
            isOpen = false;
        } else {
            activeTab = tab;
        }
        syncProfilingState();
    }

    private static void syncProfilingState() {
        ZBGamePerf.osdEnabled = isOpen;
        ZBGamePerf.modDataOsdEnabled = isOpen;
        ZBGamePerf.commandOsdEnabled = isOpen;
        ZBGamePerf.loadGridSquareOsdEnabled = isOpen;
    }

    public static void render() {
        checkHotkeys();
        if (!isOpen) return;

        var textMgr = TextManager.instance;
        var core = Core.getInstance();
        if (textMgr == null || core == null) return;

        handleMouse();

        int scrW = core.getScreenWidth();
        int scrH = core.getScreenHeight();
        winX = Math.max(10, Math.min(winX, scrW - WIN_W - 10));
        winY = Math.max(10, Math.min(winY, scrH - WIN_H - 10));

        // Window background
        SpriteRenderer.instance.renderRect(winX, winY, WIN_W, WIN_H, 0.07f, 0.08f, 0.1f, 0.94f);
        // Header bar
        SpriteRenderer.instance.renderRect(winX, winY, WIN_W, 30, 0.12f, 0.13f, 0.17f, 1.0f);
        textMgr.DrawString(UIFont.CodeSmall, winX + 12, winY + 8, "ZBGamePerf - Unified Performance Monitor", 0.9, 0.9, 0.95, 1.0);

        // Buttons
        renderTabs(textMgr);

        // Top Section: Graph
        renderGraph(textMgr);

        // Bottom Section: List
        renderList(textMgr);
    }

    private static void handleMouse() {
        int mx = Mouse.getX();
        int my = Mouse.getY();
        boolean mouseDown = Mouse.isButtonDown(0);
        boolean clicked = mouseDown && !wasMouseDown;

        if (clicked) {
            // Check close button
            int closeX = winX + WIN_W - 28;
            if (mx >= closeX && mx <= closeX + 22 && my >= winY + 4 && my <= winY + 26) {
                isOpen = false;
                syncProfilingState();
                wasMouseDown = mouseDown;
                return;
            }

            // Check reset button
            int resetX = winX + WIN_W - 140;
            if (mx >= resetX && mx <= resetX + 105 && my >= winY + 34 && my <= winY + 58) {
                ZBGamePerf.resetStats();
                wasMouseDown = mouseDown;
                return;
            }

            // Check tabs
            int tabX = winX + 12;
            int tabW = 150;
            for (int i = 0; i < TABS.length; i++) {
                if (mx >= tabX && mx <= tabX + tabW && my >= winY + 34 && my <= winY + 58) {
                    activeTab = i;
                    break;
                }
                tabX += tabW + 6;
            }

            // Check dragging on header
            if (mx >= winX && mx <= winX + WIN_W && my >= winY && my <= winY + 30) {
                isDragging = true;
                dragOffsetX = mx - winX;
                dragOffsetY = my - winY;
            }
        }

        if (mouseDown && isDragging) {
            winX = mx - dragOffsetX;
            winY = my - dragOffsetY;
        } else if (!mouseDown) {
            isDragging = false;
        }

        wasMouseDown = mouseDown;
    }

    private static void renderTabs(TextManager textMgr) {
        int tabX = winX + 12;
        int tabY = winY + 34;
        int tabW = 150;
        int tabH = 24;

        for (int i = 0; i < TABS.length; i++) {
            boolean active = (i == activeTab);
            float r = active ? 0.22f : 0.14f;
            float g = active ? 0.38f : 0.16f;
            float b = active ? 0.55f : 0.22f;

            SpriteRenderer.instance.renderRect(tabX, tabY, tabW, tabH, r, g, b, 1.0f);
            double tr = active ? 1.0 : 0.75;
            double tg = active ? 1.0 : 0.75;
            double tb = active ? 1.0 : 0.85;
            textMgr.DrawString(UIFont.CodeSmall, tabX + 10, tabY + 5, TABS[i], tr, tg, tb, 1.0);
            tabX += tabW + 6;
        }

        // Reset button
        int resetX = winX + WIN_W - 140;
        SpriteRenderer.instance.renderRect(resetX, tabY, 105, tabH, 0.35f, 0.16f, 0.16f, 1.0f);
        textMgr.DrawString(UIFont.CodeSmall, resetX + 10, tabY + 5, "Clear (Ctrl+0)", 1.0, 0.8, 0.8, 1.0);

        // Close button [X]
        int closeX = winX + WIN_W - 28;
        SpriteRenderer.instance.renderRect(closeX, winY + 4, 22, 22, 0.5f, 0.15f, 0.15f, 0.9f);
        textMgr.DrawString(UIFont.CodeSmall, closeX + 7, winY + 6, "X", 1.0, 1.0, 1.0, 1.0);
    }

    private static void renderGraph(TextManager textMgr) {
        int gx = winX + 12;
        int gy = winY + 64;
        int gw = WIN_W - 24;
        int gh = 95;

        // Background box
        SpriteRenderer.instance.renderRect(gx, gy, gw, gh, 0.05f, 0.05f, 0.07f, 1.0f);

        // Mid reference line
        SpriteRenderer.instance.renderRect(gx + 5, gy + (gh / 2), gw - 10, 1, 0.18f, 0.2f, 0.25f, 0.8f);

        if (activeTab == 0) {
            renderLuaGraph(textMgr, gx, gy, gw, gh);
        } else if (activeTab == 1) {
            renderModDataGraph(textMgr, gx, gy, gw, gh);
        } else if (activeTab == 2) {
            renderCommandGraph(textMgr, gx, gy, gw, gh);
        } else {
            renderLgsGraph(textMgr, gx, gy, gw, gh);
        }
    }

    private static void renderLuaGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        long[] history = PerformanceMonitor.getHistory();
        long maxNs = 1_000_000L;
        long sumNs = 0;
        for (long v : history) {
            if (v > maxNs) maxNs = v;
            sumNs += v;
        }

        double maxMs = maxNs / 1_000_000.0;
        double currentMs = history[59] / 1_000_000.0;
        double avgMs = (sumNs / 60.0) / 1_000_000.0;

        String label = String.format(java.util.Locale.US,
            "Lua Execution Time/sec (Last 60s) | Current: %.1fms | Peak: %.1fms | Avg: %.1fms",
            currentMs, maxMs, avgMs);
        textMgr.DrawString(UIFont.CodeSmall, gx + 10, gy + 6, label, 0.95, 0.85, 0.35, 1.0);

        drawBars(history, maxNs, gx + 10, gy + 26, gw - 20, gh - 32, 0.95f, 0.75f, 0.2f);
    }

    private static void renderModDataGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        long[] tx = ModDataMonitor.getHistoryTx();
        long[] rx = ModDataMonitor.getHistoryRx();
        long maxB = 1024L;
        for (int i = 0; i < 60; i++) {
            long total = tx[i] + rx[i];
            if (total > maxB) maxB = total;
        }

        String label = String.format(java.util.Locale.US,
            "GlobalModData Network (Last 60s) | [TX] Cyan: %s/s | [RX] Green: %s/s | Peak: %s/s",
            ModDataMonitor.formatBytes(tx[59]),
            ModDataMonitor.formatBytes(rx[59]),
            ModDataMonitor.formatBytes(maxB));
        textMgr.DrawString(UIFont.CodeSmall, gx + 10, gy + 6, label, 0.4, 0.9, 0.9, 1.0);

        drawStackedBars(tx, rx, maxB, gx + 10, gy + 26, gw - 20, gh - 32);
    }

    private static void renderCommandGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        long[] tx = CommandMonitor.getHistoryTx();
        long[] rx = CommandMonitor.getHistoryRx();
        long maxB = 1024L;
        for (int i = 0; i < 60; i++) {
            long total = tx[i] + rx[i];
            if (total > maxB) maxB = total;
        }

        String label = String.format(java.util.Locale.US,
            "Client/Server Commands (Last 60s) | [TX] Cyan: %s/s | [RX] Green: %s/s | Peak: %s/s",
            ModDataMonitor.formatBytes(tx[59]),
            ModDataMonitor.formatBytes(rx[59]),
            ModDataMonitor.formatBytes(maxB));
        textMgr.DrawString(UIFont.CodeSmall, gx + 10, gy + 6, label, 0.4, 0.9, 0.9, 1.0);

        drawStackedBars(tx, rx, maxB, gx + 10, gy + 26, gw - 20, gh - 32);
    }

    private static void renderLgsGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        long[] duration = LoadGridSquareMonitor.getHistoryDuration();
        int[] squares = LoadGridSquareMonitor.getHistorySquares();
        long maxNs = 1_000_000L;
        int maxSq = 1;
        for (int i = 0; i < 60; i++) {
            if (duration[i] > maxNs) maxNs = duration[i];
            if (squares[i] > maxSq) maxSq = squares[i];
        }

        double curMs = duration[59] / 1_000_000.0;
        double peakMs = maxNs / 1_000_000.0;
        String label = String.format(java.util.Locale.US,
            "LoadGridSquare Time/sec (Last 60s) | Current: %.1fms (%d sq/s) | Peak: %.1fms (%d sq/s)",
            curMs, squares[59], peakMs, maxSq);
        textMgr.DrawString(UIFont.CodeSmall, gx + 10, gy + 6, label, 0.5, 0.9, 0.65, 1.0);

        drawBars(duration, maxNs, gx + 10, gy + 26, gw - 20, gh - 32, 0.35f, 0.85f, 0.6f);
    }

    private static void drawBars(long[] values, long maxVal, int bx, int by, int bw, int bh, float r, float g, float b) {
        float barWidth = (float) bw / 60.0f;
        for (int i = 0; i < 60; i++) {
            float valNorm = (float) Math.min(1.0, (double) values[i] / (double) maxVal);
            int barHeight = Math.max(1, (int) (valNorm * (bh - 2)));
            int x = bx + (int) (i * barWidth);
            int y = by + bh - barHeight;
            SpriteRenderer.instance.renderRect(x, y, (int) Math.max(1.0f, barWidth - 1.0f), barHeight, r, g, b, 0.85f);
        }
    }

    private static void drawStackedBars(long[] tx, long[] rx, long maxVal, int bx, int by, int bw, int bh) {
        float barWidth = (float) bw / 60.0f;
        for (int i = 0; i < 60; i++) {
            float txNorm = (float) Math.min(1.0, (double) tx[i] / (double) maxVal);
            float rxNorm = (float) Math.min(1.0, (double) rx[i] / (double) maxVal);

            int txHeight = Math.max(0, (int) (txNorm * (bh - 2)));
            int rxHeight = Math.max(0, (int) (rxNorm * (bh - 2)));

            int x = bx + (int) (i * barWidth);
            int w = (int) Math.max(1.0f, barWidth - 1.0f);

            // Draw RX (Green) at bottom
            if (rxHeight > 0) {
                int y = by + bh - rxHeight;
                SpriteRenderer.instance.renderRect(x, y, w, rxHeight, 0.4f, 1.0f, 0.5f, 0.85f);
            }
            // Draw TX (Cyan) above RX
            if (txHeight > 0) {
                int y = by + bh - rxHeight - txHeight;
                SpriteRenderer.instance.renderRect(x, y, w, txHeight, 0.3f, 0.85f, 1.0f, 0.85f);
            }
        }
    }

    private static void renderList(TextManager textMgr) {
        int lx = winX + 12;
        int ly = winY + 165;
        int lw = WIN_W - 24;
        int lh = WIN_H - 177;

        SpriteRenderer.instance.renderRect(lx, ly, lw, lh, 0.05f, 0.05f, 0.07f, 1.0f);

        int rowY = ly + 8;
        if (activeTab == 0) {
            renderLuaList(textMgr, lx + 10, rowY);
        } else if (activeTab == 1) {
            renderModDataList(textMgr, lx + 10, rowY);
        } else if (activeTab == 2) {
            renderCommandList(textMgr, lx + 10, rowY);
        } else {
            renderLgsList(textMgr, lx + 10, rowY);
        }
    }

    private static void renderLuaList(TextManager textMgr, int lx, int rowY) {
        textMgr.DrawString(UIFont.CodeSmall, lx, rowY,
            "TIME (TOTAL)   CALLS   ORIGIN       FUNCTION / FILE", 0.7, 0.75, 0.85, 1.0);
        rowY += 18;

        List<PerfRenderer.FormattedCall> calls = PerfRenderer.getTopCalls(16, 3000);
        if (calls.isEmpty()) {
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, "(No Lua activity recorded)", 0.6, 0.6, 0.6, 1.0);
            return;
        }

        for (PerfRenderer.FormattedCall c : calls) {
            String line = c.getFullLine(12);
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, line, c.r, c.g, c.b, 1.0);
            rowY += 16;
        }
    }

    private static void renderModDataList(TextManager textMgr, int lx, int rowY) {
        textMgr.DrawString(UIFont.CodeSmall, lx, rowY,
            "DIR   TAG                     RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)", 0.7, 0.75, 0.85, 1.0);
        rowY += 18;

        List<ModDataMonitor.ModDataEntry> entries = ModDataMonitor.getEntries(1);
        if (entries.isEmpty()) {
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, "(No GlobalModData activity recorded)", 0.6, 0.6, 0.6, 1.0);
            return;
        }

        int count = 0;
        for (ModDataMonitor.ModDataEntry e : entries) {
            if (++count > 16) break;
            String prefix = e.isTx ? "[TX]" : "[RX]";
            String line = String.format(java.util.Locale.US,
                "%-4s  %-22s  %3d/min (%4.1f/s)    %9s    %9s  (%d)",
                prefix, e.tag, e.getCountLastMinute(), e.getRatePerSecond(),
                ModDataMonitor.formatBytes(e.lastBytes), ModDataMonitor.formatBytes(e.totalBytes), e.totalCount);

            double r = e.isTx ? 0.35 : 0.4;
            double g = e.isTx ? 0.85 : 1.0;
            double b = e.isTx ? 1.0 : 0.45;
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, line, r, g, b, 1.0);
            rowY += 16;
        }
    }

    private static void renderCommandList(TextManager textMgr, int lx, int rowY) {
        textMgr.DrawString(UIFont.CodeSmall, lx, rowY,
            "DIR   MODULE:COMMAND               RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)", 0.7, 0.75, 0.85, 1.0);
        rowY += 18;

        List<CommandMonitor.CommandEntry> entries = CommandMonitor.getEntries(1);
        if (entries.isEmpty()) {
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, "(No Client/Server Commands recorded)", 0.6, 0.6, 0.6, 1.0);
            return;
        }

        int count = 0;
        for (CommandMonitor.CommandEntry e : entries) {
            if (++count > 16) break;
            String prefix = e.isTx ? "[TX]" : "[RX]";
            String target = e.module + ":" + e.command;
            String line = String.format(java.util.Locale.US,
                "%-4s  %-26s  %3d/min (%4.1f/s)    %9s    %9s  (%d)",
                prefix, target, e.getCountLastMinute(), e.getRatePerSecond(),
                ModDataMonitor.formatBytes(e.lastBytes), ModDataMonitor.formatBytes(e.totalBytes), e.totalCount);

            double r = e.isTx ? 0.35 : 0.4;
            double g = e.isTx ? 0.85 : 1.0;
            double b = e.isTx ? 1.0 : 0.45;
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, line, r, g, b, 1.0);
            rowY += 16;
        }
    }

    private static void renderLgsList(TextManager textMgr, int lx, int rowY) {
        textMgr.DrawString(UIFont.CodeSmall, lx, rowY,
            "ORIGIN      LOCATION:LINE                   CALLS (RATE/S)   TOTAL TIME    AVG TIME    MAX SPIKE", 0.7, 0.75, 0.85, 1.0);
        rowY += 18;

        List<LoadGridSquareMonitor.CallbackEntry> entries = LoadGridSquareMonitor.getEntries(1);
        if (entries.isEmpty()) {
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, "(No LoadGridSquare callbacks registered)", 0.6, 0.6, 0.6, 1.0);
            return;
        }

        int count = 0;
        for (LoadGridSquareMonitor.CallbackEntry e : entries) {
            if (++count > 16) break;
            String prefixStr = e.info.prefix != null ? e.info.prefix.toString() : "[UNK]";
            double totalMs = e.totalDurationNs / 1_000_000.0;
            double avgUs = e.totalCount > 0 ? (e.totalDurationNs / 1_000.0) / e.totalCount : 0.0;
            double maxMs = e.maxDurationNs / 1_000_000.0;

            String loc = e.info.relativePath + ":" + e.info.line;
            String line = String.format(java.util.Locale.US,
                "%-10s  %-30s  %6d (%4.1f/s)   %7.1fms    %6.1fµs    %6.1fms",
                prefixStr, loc, e.totalCount, e.getRatePerSecond(), totalMs, avgUs, maxMs);

            double r, g, b;
            if (e.totalCount == 0) {
                r = 0.5; g = 0.8; b = 0.7;
            } else if (totalMs >= 50.0 || maxMs >= 10.0) {
                r = 1.0; g = 0.4; b = 0.4;
            } else if (totalMs >= 10.0 || e.totalCount >= 500) {
                r = 1.0; g = 0.85; b = 0.3;
            } else {
                r = 0.4; g = 1.0; b = 0.5;
            }
            textMgr.DrawString(UIFont.CodeSmall, lx, rowY, line, r, g, b, 1.0);
            rowY += 16;
        }
    }
}
