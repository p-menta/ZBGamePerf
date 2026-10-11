package me.p_menta.zb_game_perf;

import java.util.ArrayList;
import java.util.List;
import org.lwjglx.input.Keyboard;
import se.krka.kahlua.vm.KahluaTable;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.input.GameKeyboard;
import zombie.input.Mouse;
import zombie.network.statistics.data.NetworkStatistic;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

public class UnifiedPerfUI {
    public static boolean isOpen = false;
    public static int activeTab = 0; // 0=Lua, 1=ModData, 2=Commands, 3=LGS, 4=Net, 5=Server, 6=Pools

    private static final UIFont FONT_UI = UIFont.Small;
    private static final UIFont FONT_DATA = UIFont.Code;

    private static int winX = 50;
    private static int winY = 50;
    private static final int WIN_W = 1040;
    private static final int WIN_H = 620;
    private static final int TAB_W = 120;
    private static final int TAB_H = 24;

    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;
    private static boolean wasMouseDown = false;

    private static boolean wasCtrl1 = false;
    private static boolean wasCtrl2 = false;
    private static boolean wasCtrl3 = false;
    private static boolean wasCtrl4 = false;
    private static boolean wasCtrl5 = false;
    private static boolean wasCtrl6 = false;
    private static boolean wasCtrl7 = false;
    private static boolean wasCtrl0 = false;
    private static boolean wasCtrlP = false;
    private static boolean wasCtrlH = false;
    private static boolean wasCtrlE = false;

    public static boolean profileAllTabs = false; // When false, only active tab is measured

    private static int netTabMode = 1;     // 1=RakNet & Wire, 2=Game & VoIP
    private static int serverTabMode = 1;  // 1=Performance & RAM, 2=World & Entities
    private static int poolsTabMode = 1;   // 1=Top In-Use, 2=Most Reused, 3=Allocations

    private static final String[] TABS = {
        "1. Lua Calls",
        "2. GlobalModData",
        "3. Commands",
        "4. LoadGridSquare",
        "5. Network",
        "6. Server/World",
        "7. Object Pools"
    };

    public static void checkHotkeys() {
        try {
            boolean isCtrl = GameKeyboard.isKeyDown(Keyboard.KEY_LCONTROL) || GameKeyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            boolean is1 = GameKeyboard.isKeyDown(Keyboard.KEY_1);
            boolean is2 = GameKeyboard.isKeyDown(Keyboard.KEY_2);
            boolean is3 = GameKeyboard.isKeyDown(Keyboard.KEY_3);
            boolean is4 = GameKeyboard.isKeyDown(Keyboard.KEY_4);
            boolean is5 = GameKeyboard.isKeyDown(Keyboard.KEY_5);
            boolean is6 = GameKeyboard.isKeyDown(Keyboard.KEY_6);
            boolean is7 = GameKeyboard.isKeyDown(Keyboard.KEY_7);
            boolean is0 = GameKeyboard.isKeyDown(Keyboard.KEY_0);
            boolean isP = GameKeyboard.isKeyDown(Keyboard.KEY_P);
            boolean isH = GameKeyboard.isKeyDown(Keyboard.KEY_H);
            boolean isE = GameKeyboard.isKeyDown(Keyboard.KEY_E);

            if (isCtrl && is1 && !wasCtrl1) toggleTab(0);
            wasCtrl1 = isCtrl && is1;

            if (isCtrl && is2 && !wasCtrl2) toggleTab(1);
            wasCtrl2 = isCtrl && is2;

            if (isCtrl && is3 && !wasCtrl3) toggleTab(2);
            wasCtrl3 = isCtrl && is3;

            if (isCtrl && is4 && !wasCtrl4) toggleTab(3);
            wasCtrl4 = isCtrl && is4;

            if (isCtrl && is5 && !wasCtrl5) toggleTab(4);
            wasCtrl5 = isCtrl && is5;

            if (isCtrl && is6 && !wasCtrl6) toggleTab(5);
            wasCtrl6 = isCtrl && is6;

            if (isCtrl && is7 && !wasCtrl7) toggleTab(6);
            wasCtrl7 = isCtrl && is7;

            if (isCtrl && is0 && !wasCtrl0) ZBGamePerf.resetStats();
            wasCtrl0 = isCtrl && is0;

            if (isCtrl && isP && !wasCtrlP) toggleAllTabsProfiling();
            wasCtrlP = isCtrl && isP;

            if (isCtrl && isH && !wasCtrlH) {
                ChunkDeliveryMonitor.showOnScreenHud = !ChunkDeliveryMonitor.showOnScreenHud;
            }
            wasCtrlH = isCtrl && isH;

            if (isCtrl && isE && !wasCtrlE) {
                CommandPingMonitor.sendPing();
            }
            wasCtrlE = isCtrl && isE;
        } catch (Throwable ignored) {
        }
    }

    public static void toggleAllTabsProfiling() {
        profileAllTabs = !profileAllTabs;
        if (!isOpen) {
            isOpen = true;
        }
        syncProfilingState();
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

    public static void syncProfilingState() {
        if (!isOpen) {
            ZBGamePerf.osdEnabled = false;
            ZBGamePerf.modDataOsdEnabled = false;
            ZBGamePerf.commandOsdEnabled = false;
            ZBGamePerf.loadGridSquareOsdEnabled = false;
            ServerNetMonitor.netEnabled = false;
            ServerNetMonitor.serverWorldEnabled = false;
            ServerNetMonitor.poolsEnabled = false;
            return;
        }

        if (profileAllTabs) {
            // All tabs profile simultaneously
            ZBGamePerf.osdEnabled = true;
            ZBGamePerf.modDataOsdEnabled = true;
            ZBGamePerf.commandOsdEnabled = true;
            ZBGamePerf.loadGridSquareOsdEnabled = true;
            ServerNetMonitor.netEnabled = true;
            ServerNetMonitor.serverWorldEnabled = true;
            ServerNetMonitor.poolsEnabled = true;
        } else {
            // Modular mode: Only the active tab profiles!
            ZBGamePerf.osdEnabled = (activeTab == 0);
            ZBGamePerf.modDataOsdEnabled = (activeTab == 1);
            ZBGamePerf.commandOsdEnabled = (activeTab == 2);
            ZBGamePerf.loadGridSquareOsdEnabled = (activeTab == 3);
            ServerNetMonitor.netEnabled = (activeTab == 4);
            ServerNetMonitor.serverWorldEnabled = (activeTab == 5);
            ServerNetMonitor.poolsEnabled = (activeTab == 6);
        }

        if (ZBGamePerf.osdMode == 0) ZBGamePerf.osdMode = 1;
        if (ZBGamePerf.modDataOsdMode == 0) ZBGamePerf.modDataOsdMode = 1;
        if (ZBGamePerf.commandOsdMode == 0) ZBGamePerf.commandOsdMode = 1;
        if (ZBGamePerf.loadGridSquareOsdMode == 0) ZBGamePerf.loadGridSquareOsdMode = 1;

        if (profileAllTabs || activeTab == 4 || activeTab == 5) {
            ServerNetMonitor.requestServerStatistics();
        }
    }

    public static int getActiveTabMode() {
        if (activeTab == 0) return ZBGamePerf.osdMode;
        if (activeTab == 1) return ZBGamePerf.modDataOsdMode;
        if (activeTab == 2) return ZBGamePerf.commandOsdMode;
        if (activeTab == 3) return ZBGamePerf.loadGridSquareOsdMode;
        if (activeTab == 4) return netTabMode;
        if (activeTab == 5) return serverTabMode;
        return poolsTabMode;
    }

    public static void cycleActiveTabMode() {
        if (activeTab == 0) {
            ZBGamePerf.osdMode = (ZBGamePerf.osdMode % 3) + 1;
        } else if (activeTab == 1) {
            ZBGamePerf.modDataOsdMode = (ZBGamePerf.modDataOsdMode % 3) + 1;
        } else if (activeTab == 2) {
            ZBGamePerf.commandOsdMode = (ZBGamePerf.commandOsdMode % 3) + 1;
        } else if (activeTab == 3) {
            ZBGamePerf.loadGridSquareOsdMode = (ZBGamePerf.loadGridSquareOsdMode % 3) + 1;
        } else if (activeTab == 4) {
            netTabMode = (netTabMode % 2) + 1;
        } else if (activeTab == 5) {
            serverTabMode = (serverTabMode % 3) + 1;
        } else if (activeTab == 6) {
            poolsTabMode = (poolsTabMode % 3) + 1;
        }
    }

    private static String getModeButtonLabel() {
        int m = getActiveTabMode();
        if (activeTab == 0) {
            if (m == 2) return "Mode: Total Time";
            if (m == 3) return "Mode: Most Calls";
            return "Mode: Last 1s (Recent)";
        } else if (activeTab == 1) {
            if (m == 2) return "Mode: TX Only";
            if (m == 3) return "Mode: RX Only";
            return "Mode: TX & RX (All)";
        } else if (activeTab == 2) {
            if (m == 2) return "Mode: TX Only";
            if (m == 3) return "Mode: RX Only";
            return "Mode: TX & RX (All)";
        } else if (activeTab == 3) {
            if (m == 2) return "Mode: Total Time";
            if (m == 3) return "Mode: Most Calls";
            return "Mode: Recent Activity";
        } else if (activeTab == 4) {
            return (m == 2) ? "Mode: Raw Rates" : "Mode: Network Overview";
        } else if (activeTab == 5) {
            if (m == 2) return "Mode: Performance";
            if (m == 3) return "Mode: Characters & World";
            return "Mode: All (Overview)";
        } else {
            if (m == 2) return "Mode: Most Reused";
            if (m == 3) return "Mode: Allocations";
            return "Mode: Top In-Use";
        }
    }

    private static String getModeDescription() {
        int m = getActiveTabMode();
        if (activeTab == 0) {
            if (m == 2) return "Cumulative Lua execution time across session";
            if (m == 3) return "Most frequently called Lua functions";
            return "Top Lua CPU usage in the last 1s (direct gameplay impact)";
        } else if (activeTab == 1) {
            if (m == 2) return "Showing outgoing GlobalModData packets (TX only)";
            if (m == 3) return "Showing incoming GlobalModData packets (RX only)";
            return "Showing all GlobalModData transmissions & receives";
        } else if (activeTab == 2) {
            if (m == 2) return "Showing Client -> Server commands (TX only)";
            if (m == 3) return "Showing Server -> Client commands (RX only)";
            return "Showing all Client/Server network commands";
        } else if (activeTab == 3) {
            if (m == 2) return "Callbacks sorted by cumulative total execution time";
            if (m == 3) return "Callbacks sorted by total invocation count";
            return "Callbacks with activity in the last minute";
        } else if (activeTab == 4) {
            return (m == 2) ? "RakNet UDP traffic, latency, packet loss, and resend buffer"
                            : "Client vs Server network statistics and RakNet diagnostics";
        } else if (activeTab == 5) {
            if (m == 2) return "JVM memory, FPS, and tick period for Client vs Server";
            if (m == 3) return "Zombies, animals, loaded cells, and desync teleports";
            return "Client vs Server Performance, Entities, and World statistics";
        } else {
            if (m == 2) return "Pools sorted by reuse count (cache hits preventing GC pauses)";
            if (m == 3) return "Pools sorted by fresh object allocations (misses)";
            return "JVM object pools currently allocated & in-use (GC optimization)";
        }
    }

    public static void render() {
        checkHotkeys();
        var textMgr = TextManager.instance;
        var core = Core.getInstance();
        if (textMgr == null || core == null) return;

        int mx = Mouse.getXA();
        int my = Mouse.getYA();
        boolean mouseDown = Mouse.isButtonDown(0);
        boolean clicked = (mouseDown && !wasMouseDown) || Mouse.isLeftPressed();

        ServerNetMonitor.update();
        if (activeTab == 4 || profileAllTabs || ChunkDeliveryMonitor.showOnScreenHud) {
            ChunkDeliveryMonitor.update();
        }
        CommandPingMonitor.update();
        ServerLuaMonitor.update();
        ServerModDataMonitor.update();
        ServerCommandMonitor.update();
        ServerLgsMonitor.update();
        ServerPoolsMonitor.update();

        if (ChunkDeliveryMonitor.showOnScreenHud) {
            ChunkDeliveryMonitor.handleHudMouse(mx, my, mouseDown, clicked);
            renderChunkHud(textMgr);
        }

        if (!isOpen) {
            wasMouseDown = mouseDown;
            return;
        }

        handleMouse(mx, my);

        int scrW = core.getScreenWidth();
        int scrH = core.getScreenHeight();
        winX = Math.max(10, Math.min(winX, scrW - WIN_W - 10));
        winY = Math.max(10, Math.min(winY, scrH - WIN_H - 10));

        // Window background
        SpriteRenderer.instance.renderRect(winX, winY, WIN_W, WIN_H, 0.07f, 0.08f, 0.1f, 0.94f);
        // Header bar
        SpriteRenderer.instance.renderRect(winX, winY, WIN_W, 30, 0.12f, 0.13f, 0.17f, 1.0f);
        textMgr.DrawString(FONT_UI, winX + 12, winY + 6, "ZBGamePerf - Unified Performance Monitor", 0.9, 0.9, 0.95, 1.0);

        // Tabs
        renderTabs(textMgr, mx, my);

        // Top Section: Graph
        renderGraph(textMgr);

        // Bottom Section: List
        renderList(textMgr);

        // Footer Section: Mode switcher & info
        renderFooter(textMgr, mx, my);
    }

    private static void handleMouse(int mx, int my) {
        boolean mouseDown = Mouse.isButtonDown(0);
        boolean clicked = (mouseDown && !wasMouseDown) || Mouse.isLeftPressed();

        if (clicked) {
            // Close button
            int closeX = winX + WIN_W - 28;
            if (mx >= closeX && mx <= closeX + 22 && my >= winY + 4 && my <= winY + 26) {
                isOpen = false;
                syncProfilingState();
                wasMouseDown = mouseDown;
                return;
            }

            // Profiling Mode Toggle button [Ctrl+P]
            int profBtnW = 280;
            int profBtnX = winX + WIN_W - 35 - profBtnW;
            if (mx >= profBtnX && mx <= profBtnX + profBtnW && my >= winY + 4 && my <= winY + 26) {
                toggleAllTabsProfiling();
                wasMouseDown = mouseDown;
                return;
            }

            // Reset button
            int resetW = 115;
            int resetX = winX + WIN_W - resetW - 14;
            if (mx >= resetX && mx <= resetX + resetW && my >= winY + 34 && my <= winY + 34 + TAB_H) {
                ZBGamePerf.resetStats();
                wasMouseDown = mouseDown;
                return;
            }

            // Tabs
            int tabX = winX + 12;
            for (int i = 0; i < TABS.length; i++) {
                if (mx >= tabX && mx <= tabX + TAB_W && my >= winY + 34 && my <= winY + 34 + TAB_H) {
                    activeTab = i;
                    syncProfilingState();
                    wasMouseDown = mouseDown;
                    return;
                }
                tabX += TAB_W + 4;
            }

            // Source Toggle button (in tabs 0, 1, 2, 3, 6)
            if (activeTab == 0 || activeTab == 1 || activeTab == 2 || activeTab == 3 || activeTab == 6) {
                int srcBtnW = 160;
                int srcBtnH = 20;
                int srcBtnX = winX + WIN_W - 12 - srcBtnW - 6;
                int srcBtnY = winY + 64 + 3;
                if (mx >= srcBtnX && mx <= srcBtnX + srcBtnW && my >= srcBtnY && my <= srcBtnY + srcBtnH) {
                    if (activeTab == 0) ServerLuaMonitor.toggleSource();
                    else if (activeTab == 1) ServerModDataMonitor.toggleSource();
                    else if (activeTab == 2) ServerCommandMonitor.toggleSource();
                    else if (activeTab == 3) ServerLgsMonitor.toggleSource();
                    else if (activeTab == 6) ServerPoolsMonitor.toggleSource();
                    wasMouseDown = mouseDown;
                    return;
                }
            }

            // HUD na Tela Toggle button & Ping Engine button (in Network tab)
            if (activeTab == 4) {
                int hudBtnW = 180;
                int hudBtnH = 20;
                int hudBtnX = winX + WIN_W - 12 - hudBtnW - 6;
                int hudBtnY = winY + 64 + 3;
                if (mx >= hudBtnX && mx <= hudBtnX + hudBtnW && my >= hudBtnY && my <= hudBtnY + hudBtnH) {
                    ChunkDeliveryMonitor.showOnScreenHud = !ChunkDeliveryMonitor.showOnScreenHud;
                    wasMouseDown = mouseDown;
                    return;
                }

                int pingBtnW = 190;
                int pingBtnH = 20;
                int pingBtnX = hudBtnX - pingBtnW - 8;
                int pingBtnY = winY + 64 + 3;
                if (mx >= pingBtnX && mx <= pingBtnX + pingBtnW && my >= pingBtnY && my <= pingBtnY + pingBtnH) {
                    CommandPingMonitor.sendPing();
                    wasMouseDown = mouseDown;
                    return;
                }
            }

            // Mode button in footer
            int btnX = winX + 12;
            int btnY = winY + WIN_H - 34;
            int btnW = 240;
            int btnH = 26;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                cycleActiveTabMode();
                wasMouseDown = mouseDown;
                return;
            }

            // Dragging header
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

    private static void renderTabs(TextManager textMgr, int mx, int my) {
        int tabX = winX + 12;
        int tabY = winY + 34;

        for (int i = 0; i < TABS.length; i++) {
            boolean active = (i == activeTab);
            boolean hovered = (mx >= tabX && mx <= tabX + TAB_W && my >= tabY && my <= tabY + TAB_H);

            float r = active ? 0.22f : (hovered ? 0.25f : 0.14f);
            float g = active ? 0.38f : (hovered ? 0.30f : 0.16f);
            float b = active ? 0.55f : (hovered ? 0.42f : 0.22f);

            SpriteRenderer.instance.renderRect(tabX, tabY, TAB_W, TAB_H, r, g, b, 1.0f);
            double tr = active ? 1.0 : (hovered ? 0.95 : 0.75);
            double tg = active ? 1.0 : (hovered ? 0.95 : 0.75);
            double tb = active ? 1.0 : (hovered ? 0.95 : 0.85);
            textMgr.DrawString(FONT_UI, tabX + 8, tabY + 4, TABS[i], tr, tg, tb, 1.0);
            tabX += TAB_W + 4;
        }

        // Reset button
        int resetW = 115;
        int resetX = winX + WIN_W - resetW - 14;
        boolean resetHover = (mx >= resetX && mx <= resetX + resetW && my >= tabY && my <= tabY + TAB_H);
        float rr = resetHover ? 0.50f : 0.35f;
        SpriteRenderer.instance.renderRect(resetX, tabY, resetW, TAB_H, rr, 0.16f, 0.16f, 1.0f);
        textMgr.DrawString(FONT_UI, resetX + 10, tabY + 4, "Clear (Ctrl+0)", 1.0, 0.8, 0.8, 1.0);

        // Profiling Mode Toggle Button [Ctrl+P]
        int profBtnW = 280;
        int profBtnX = winX + WIN_W - 35 - profBtnW;
        int profBtnY = winY + 4;
        int profBtnH = 22;
        boolean profHover = (mx >= profBtnX && mx <= profBtnX + profBtnW && my >= profBtnY && my <= profBtnY + profBtnH);

        float pbr, pbg, pbb;
        String profLabel;
        if (profileAllTabs) {
            pbr = profHover ? 0.20f : 0.14f;
            pbg = profHover ? 0.55f : 0.40f;
            pbb = profHover ? 0.35f : 0.22f;
            profLabel = "Profiling: ALL TABS (Ctrl+P)";
        } else {
            pbr = profHover ? 0.22f : 0.16f;
            pbg = profHover ? 0.36f : 0.26f;
            pbb = profHover ? 0.55f : 0.42f;
            profLabel = "Profiling: ACTIVE TAB (Modular) (Ctrl+P)";
        }
        SpriteRenderer.instance.renderRect(profBtnX, profBtnY, profBtnW, profBtnH, pbr, pbg, pbb, 1.0f);
        textMgr.DrawString(FONT_UI, profBtnX + 10, profBtnY + 3, profLabel, 1.0, 1.0, 1.0, 1.0);

        // Close button [X]
        int closeX = winX + WIN_W - 28;
        int closeY = winY + 4;
        int closeSize = 22;
        boolean closeHover = (mx >= closeX && mx <= closeX + closeSize && my >= closeY && my <= closeY + closeSize);
        float cr = closeHover ? 0.70f : 0.50f;
        SpriteRenderer.instance.renderRect(closeX, closeY, closeSize, closeSize, cr, 0.15f, 0.15f, 0.9f);
        int textW = textMgr.MeasureStringX(FONT_UI, "X");
        int textH = textMgr.MeasureStringY(FONT_UI, "X");
        int textX = closeX + (closeSize - textW) / 2;
        int textY = closeY + (closeSize - textH) / 2;
        textMgr.DrawString(FONT_UI, textX, textY, "X", 1.0, 1.0, 1.0, 1.0);
    }

    private static void renderGraph(TextManager textMgr) {
        int gx = winX + 12;
        int gy = winY + 64;
        int gw = WIN_W - 24;
        int gh = 100;

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
        } else if (activeTab == 3) {
            renderLgsGraph(textMgr, gx, gy, gw, gh);
        } else if (activeTab == 4) {
            renderNetworkGraph(textMgr, gx, gy, gw, gh);
        } else if (activeTab == 5) {
            renderServerWorldGraph(textMgr, gx, gy, gw, gh);
        } else {
            renderPoolsGraph(textMgr, gx, gy, gw, gh);
        }
    }

    private static void renderLuaGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        boolean isServer = ServerLuaMonitor.isServerSource();
        long[] history = isServer ? ServerLuaMonitor.getHistory() : PerformanceMonitor.getHistory();
        long maxNs = 1_000_000L;
        long sumNs = 0;
        for (long v : history) {
            if (v > maxNs) maxNs = v;
            sumNs += v;
        }

        double maxMs = maxNs / 1_000_000.0;
        double currentMs = history[59] / 1_000_000.0;
        double avgMs = (sumNs / 60.0) / 1_000_000.0;

        String sourceTag = isServer ? "[SERVER]" : "[CLIENT]";
        String label = String.format(java.util.Locale.US,
            "%s Lua Time/s (60s) | Current: %.1fms | Peak: %.1fms | Avg: %.1fms",
            sourceTag, currentMs, maxMs, avgMs);
        textMgr.DrawString(FONT_UI, gx + 10, gy + 5, label, 0.95, 0.85, 0.35, 1.0);

        renderSourceButton(textMgr, gx, gy, gw, isServer);

        float barR = isServer ? 0.35f : 0.95f;
        float barG = isServer ? 0.85f : 0.75f;
        float barB = isServer ? 1.0f : 0.2f;
        drawBars(history, maxNs, gx + 10, gy + 26, gw - 20, gh - 32, barR, barG, barB);
    }

    private static void renderSourceButton(TextManager textMgr, int gx, int gy, int gw, boolean isServer) {
        int srcBtnW = 160;
        int srcBtnH = 20;
        int srcBtnX = gx + gw - srcBtnW - 6;
        int srcBtnY = gy + 3;
        int mx = Mouse.getXA();
        int my = Mouse.getYA();
        boolean srcHover = (mx >= srcBtnX && mx <= srcBtnX + srcBtnW && my >= srcBtnY && my <= srcBtnY + srcBtnH);
        float sbr = isServer ? (srcHover ? 0.18f : 0.14f) : (srcHover ? 0.22f : 0.16f);
        float sbg = isServer ? (srcHover ? 0.50f : 0.38f) : (srcHover ? 0.36f : 0.26f);
        float sbb = isServer ? (srcHover ? 0.30f : 0.20f) : (srcHover ? 0.55f : 0.42f);
        SpriteRenderer.instance.renderRect(srcBtnX, srcBtnY, srcBtnW, srcBtnH, sbr, sbg, sbb, 1.0f);
        String srcBtnText = isServer ? "Source: SERVER" : "Source: CLIENT";
        textMgr.DrawString(FONT_UI, srcBtnX + 10, srcBtnY + 3, srcBtnText, 1.0, 1.0, 1.0, 1.0);
    }

    private static void renderModDataGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        boolean isServer = ServerModDataMonitor.isServerSource();
        long[] tx = isServer ? ServerModDataMonitor.getHistoryTx() : ModDataMonitor.getHistoryTx();
        long[] rx = isServer ? ServerModDataMonitor.getHistoryRx() : ModDataMonitor.getHistoryRx();
        long maxB = 1024L;
        for (int i = 0; i < 60; i++) {
            long total = tx[i] + rx[i];
            if (total > maxB) maxB = total;
        }

        long curTotal = tx[59] + rx[59];
        String sourceTag = isServer ? "[SERVER] " : "";
        String label = String.format(java.util.Locale.US,
            "%sGlobalModData Bandwidth/sec (Last 60s) | Current: %s/s (TX: %s, RX: %s) | Peak: %s/s",
            sourceTag, ModDataMonitor.formatBytes(curTotal), ModDataMonitor.formatBytes(tx[59]), ModDataMonitor.formatBytes(rx[59]),
            ModDataMonitor.formatBytes(maxB));
        textMgr.DrawString(FONT_UI, gx + 10, gy + 5, label, 0.35, 0.85, 1.0, 1.0);

        renderSourceButton(textMgr, gx, gy, gw, isServer);
        drawStackedBars(tx, rx, maxB, gx + 10, gy + 26, gw - 20, gh - 32);
    }

    private static void renderCommandGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        boolean isServer = ServerCommandMonitor.isServerSource();
        long[] tx = isServer ? ServerCommandMonitor.getHistoryTx() : CommandMonitor.getHistoryTx();
        long[] rx = isServer ? ServerCommandMonitor.getHistoryRx() : CommandMonitor.getHistoryRx();
        long maxB = 1024L;
        for (int i = 0; i < 60; i++) {
            long total = tx[i] + rx[i];
            if (total > maxB) maxB = total;
        }

        long curTotal = tx[59] + rx[59];
        String sourceTag = isServer ? "[SERVER] " : "";
        String label = String.format(java.util.Locale.US,
            "%sClient/Server Commands Bandwidth/sec (Last 60s) | Current: %s/s (TX: %s, RX: %s) | Peak: %s/s",
            sourceTag, ModDataMonitor.formatBytes(curTotal), ModDataMonitor.formatBytes(tx[59]), ModDataMonitor.formatBytes(rx[59]),
            ModDataMonitor.formatBytes(maxB));
        textMgr.DrawString(FONT_UI, gx + 10, gy + 5, label, 0.35, 0.85, 1.0, 1.0);

        renderSourceButton(textMgr, gx, gy, gw, isServer);
        drawStackedBars(tx, rx, maxB, gx + 10, gy + 26, gw - 20, gh - 32);
    }

    private static void renderLgsGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        boolean isServer = ServerLgsMonitor.isServerSource();
        long[] duration = isServer ? ServerLgsMonitor.getHistoryDuration() : LoadGridSquareMonitor.getHistoryDuration();
        int[] squares = isServer ? ServerLgsMonitor.getHistorySquares() : LoadGridSquareMonitor.getHistorySquares();

        long maxNs = 1_000_000L;
        for (long v : duration) if (v > maxNs) maxNs = v;

        double curMs = duration[59] / 1_000_000.0;
        double peakMs = maxNs / 1_000_000.0;
        int curSq = squares[59];

        String sourceTag = isServer ? "[SERVER] " : "";
        String label = String.format(java.util.Locale.US,
            "%sLoadGridSquare Execution Time/sec (Last 60s) | Current: %.1fms (%d squares) | Peak: %.1fms",
            sourceTag, curMs, curSq, peakMs);
        textMgr.DrawString(FONT_UI, gx + 10, gy + 5, label, 0.45, 0.95, 0.55, 1.0);

        renderSourceButton(textMgr, gx, gy, gw, isServer);
        drawBars(duration, maxNs, gx + 10, gy + 26, gw - 20, gh - 32, 0.4f, 0.95f, 0.55f);
    }

    private static void renderNetworkGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        int[] pingHist = ServerNetMonitor.getHistoryPing();
        double[] lossHist = ServerNetMonitor.getHistoryLoss();

        int maxPing = 100;
        int currentPing = pingHist[59];
        int sumPing = 0;
        int pingCount = 0;
        for (int p : pingHist) {
            if (p > maxPing) maxPing = p;
            if (p > 0) {
                sumPing += p;
                pingCount++;
            }
        }
        int avgPing = (pingCount > 0) ? (sumPing / pingCount) : 0;
        double currentLoss = lossHist[59] * 100.0;

        String chunkStatus;
        if (ChunkDeliveryMonitor.isWaiting) {
            chunkStatus = String.format(java.util.Locale.US,
                "Chunks: %d/%d (%.0f%%) Waiting: %.0fms",
                ChunkDeliveryMonitor.loadedChunks, ChunkDeliveryMonitor.totalChunks,
                ChunkDeliveryMonitor.syncPct, ChunkDeliveryMonitor.currentWaitMs);
        } else if (ChunkDeliveryMonitor.totalDeliveries > 0) {
            chunkStatus = String.format(java.util.Locale.US,
                "Chunks: 100%% OK (Sync: %.0fms | Peak: %.0fms)",
                ChunkDeliveryMonitor.lastDeliveryTimeMs, ChunkDeliveryMonitor.peakDeliveryTimeMs);
        } else {
            chunkStatus = String.format(java.util.Locale.US,
                "Chunks: %d/%d (%.0f%%)",
                ChunkDeliveryMonitor.loadedChunks, ChunkDeliveryMonitor.totalChunks,
                ChunkDeliveryMonitor.syncPct);
        }

        String label = String.format(java.util.Locale.US,
            "Ping: %dms (Avg: %dms, Peak: %dms) | Loss: %.1f%% | %s",
            currentPing, avgPing, maxPing, currentLoss, chunkStatus);
        textMgr.DrawString(FONT_UI, gx + 10, gy + 5, label, 0.35, 0.9, 1.0, 1.0);

        // On-Screen HUD Toggle button
        int hudBtnW = 180;
        int hudBtnH = 20;
        int hudBtnX = gx + gw - hudBtnW - 6;
        int hudBtnY = gy + 3;
        boolean hudActive = ChunkDeliveryMonitor.showOnScreenHud;
        float hbr = hudActive ? 0.18f : 0.28f;
        float hbg = hudActive ? 0.50f : 0.20f;
        float hbb = hudActive ? 0.25f : 0.22f;
        SpriteRenderer.instance.renderRect(hudBtnX, hudBtnY, hudBtnW, hudBtnH, hbr, hbg, hbb, 1.0f);
        String hudBtnText = hudActive ? "On-Screen HUD: ON (Ctrl+H)" : "On-Screen HUD: OFF";
        textMgr.DrawString(FONT_UI, hudBtnX + 8, hudBtnY + 3, hudBtnText, 1.0, 1.0, 1.0, 1.0);

        // Ping Engine (Load) button
        int pingBtnW = 190;
        int pingBtnH = 20;
        int pingBtnX = hudBtnX - pingBtnW - 8;
        int pingBtnY = gy + 3;
        int mx = Mouse.getXA();
        int my = Mouse.getYA();
        boolean pingHover = (mx >= pingBtnX && mx <= pingBtnX + pingBtnW && my >= pingBtnY && my <= pingBtnY + pingBtnH);
        boolean pingWaiting = CommandPingMonitor.isWaiting;
        float pbr = pingWaiting ? 0.45f : (pingHover ? 0.22f : 0.16f);
        float pbg = pingWaiting ? 0.35f : (pingHover ? 0.38f : 0.28f);
        float pbb = pingWaiting ? 0.15f : (pingHover ? 0.55f : 0.42f);
        SpriteRenderer.instance.renderRect(pingBtnX, pingBtnY, pingBtnW, pingBtnH, pbr, pbg, pbb, 1.0f);
        String pingBtnText = pingWaiting ? "Pinging Engine..." : "Ping Engine (Ctrl+E)";
        textMgr.DrawString(FONT_UI, pingBtnX + 8, pingBtnY + 3, pingBtnText, 1.0, 1.0, 1.0, 1.0);

        float barWidth = (float) (gw - 20) / 60.0f;
        int bh = gh - 32;
        int by = gy + 26;
        for (int i = 0; i < 60; i++) {
            int p = pingHist[i];
            float norm = (float) Math.min(1.0, (double) p / (double) maxPing);
            int barH = Math.max(1, (int) (norm * (bh - 2)));
            int x = gx + 10 + (int) (i * barWidth);
            int y = by + bh - barH;
            int w = (int) Math.max(1.0f, barWidth - 1.0f);

            float r = (p < 70) ? 0.3f : ((p < 140) ? 1.0f : 1.0f);
            float g = (p < 70) ? 1.0f : ((p < 140) ? 0.85f : 0.35f);
            float b = (p < 70) ? 0.6f : ((p < 140) ? 0.2f : 0.25f);
            SpriteRenderer.instance.renderRect(x, y, w, barH, r, g, b, 0.85f);

            if (lossHist[i] > 0.001) {
                SpriteRenderer.instance.renderRect(x, by + 1, w, 4, 1.0f, 0.2f, 0.2f, 1.0f);
            }
        }
    }

    private static void renderServerWorldGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        int[] clientFps = ServerNetMonitor.getHistoryClientFps();
        int[] serverFps = ServerNetMonitor.getHistoryServerFps();

        int curClient = clientFps[59];
        int curServer = serverFps[59];

        String srvStr = (curServer > 0) ? (curServer + " FPS") : "(Waiting / No Admin)";
        String label = String.format(java.util.Locale.US,
            "Framerate (Last 60s) | Client: %d FPS | Server: %s",
            curClient, srvStr);
        textMgr.DrawString(FONT_UI, gx + 10, gy + 5, label, 0.4, 1.0, 0.6, 1.0);

        int maxVal = 75;
        for (int f : clientFps) if (f > maxVal) maxVal = f;
        for (int f : serverFps) if (f > maxVal) maxVal = f;

        float barWidth = (float) (gw - 20) / 60.0f;
        int bh = gh - 32;
        int by = gy + 26;
        for (int i = 0; i < 60; i++) {
            float cNorm = (float) Math.min(1.0, (double) clientFps[i] / (double) maxVal);
            int cH = Math.max(1, (int) (cNorm * (bh - 2)));
            int x = gx + 10 + (int) (i * barWidth);
            int w = (int) Math.max(1.0f, barWidth - 1.0f);

            SpriteRenderer.instance.renderRect(x, by + bh - cH, w, cH, 0.35f, 0.9f, 0.5f, 0.85f);

            int sF = serverFps[i];
            if (sF > 0) {
                float sNorm = (float) Math.min(1.0, (double) sF / (double) maxVal);
                int sY = by + bh - (int) (sNorm * (bh - 2));
                SpriteRenderer.instance.renderRect(x, sY, w, 2, 0.3f, 0.85f, 1.0f, 1.0f);
            }
        }
    }

    private static void renderPoolsGraph(TextManager textMgr, int gx, int gy, int gw, int gh) {
        boolean isServer = ServerPoolsMonitor.isServerSource();
        long[] history = isServer ? ServerPoolsMonitor.getHistoryPoolsUsed() : ServerNetMonitor.getHistoryPoolsUsed();
        long maxObj = 1000L;
        for (long v : history) if (v > maxObj) maxObj = v;

        ServerNetMonitor.PoolsSnapshot snap = isServer ? ServerPoolsMonitor.getPoolsSnapshot() : ServerNetMonitor.getPoolsSnapshot();
        String sourceTag = isServer ? "[SERVER] " : "";
        String label = String.format(java.util.Locale.US,
            "%sJVM Object Pools In-Use (Last 60s) | In-Use: %d | Free: %d | Total: %d",
            sourceTag, snap.usedObjects, snap.freeObjects, snap.totalObjects);
        textMgr.DrawString(FONT_UI, gx + 10, gy + 5, label, 0.75, 0.65, 1.0, 1.0);

        renderSourceButton(textMgr, gx, gy, gw, isServer);
        drawBars(history, maxObj, gx + 10, gy + 26, gw - 20, gh - 32, 0.65f, 0.55f, 0.95f);
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
        int lh = WIN_H - 165 - 46;

        SpriteRenderer.instance.renderRect(lx, ly, lw, lh, 0.05f, 0.05f, 0.07f, 1.0f);

        int rowY = ly + 8;
        if (activeTab == 0) {
            renderLuaList(textMgr, lx + 10, rowY);
        } else if (activeTab == 1) {
            renderModDataList(textMgr, lx + 10, rowY);
        } else if (activeTab == 2) {
            renderCommandList(textMgr, lx + 10, rowY);
        } else if (activeTab == 3) {
            renderLgsList(textMgr, lx + 10, rowY);
        } else if (activeTab == 4) {
            renderNetworkList(textMgr, lx + 10, rowY);
        } else if (activeTab == 5) {
            renderServerWorldList(textMgr, lx + 10, rowY);
        } else {
            renderPoolsList(textMgr, lx + 10, rowY);
        }
    }

    private static void renderFooter(TextManager textMgr, int mx, int my) {
        int fy = winY + WIN_H - 34;
        int fw = WIN_W - 24;

        // Separator line
        SpriteRenderer.instance.renderRect(winX + 12, fy - 3, fw, 1, 0.18f, 0.2f, 0.25f, 0.8f);

        int btnX = winX + 12;
        int btnY = fy;
        int btnW = 240;
        int btnH = 26;
        boolean hovered = (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH);

        float br = hovered ? 0.24f : 0.16f;
        float bg = hovered ? 0.36f : 0.22f;
        float bb = hovered ? 0.50f : 0.32f;
        SpriteRenderer.instance.renderRect(btnX, btnY, btnW, btnH, br, bg, bb, 1.0f);

        textMgr.DrawString(FONT_UI, btnX + 10, btnY + 4, getModeButtonLabel() + "  [+]", 1.0, 1.0, 1.0, 1.0);
        textMgr.DrawString(FONT_UI, btnX + btnW + 10, btnY + 4, "(Click to cycle)", 0.5, 0.55, 0.65, 1.0);
        textMgr.DrawString(FONT_UI, btnX + btnW + 130, btnY + 4, "|  " + getModeDescription(), 0.75, 0.8, 0.85, 1.0);

        String profStatus = profileAllTabs ? "[Profiling: ALL TABS]" : "[Profiling: ACTIVE TAB]";
        double psR = profileAllTabs ? 0.4 : 0.5;
        double psG = profileAllTabs ? 1.0 : 0.85;
        double psB = profileAllTabs ? 0.6 : 1.0;
        textMgr.DrawString(FONT_UI, winX + WIN_W - 220, btnY + 4, profStatus, psR, psG, psB, 1.0);
    }

    private static void renderLuaList(TextManager textMgr, int lx, int rowY) {
        int mode = ZBGamePerf.osdMode;
        if (mode == 0) mode = 1;
        boolean isServer = ServerLuaMonitor.isServerSource();

        String srvTag = isServer ? " [SERVER]" : "";
        String header;
        if (mode == 2) {
            header = "TIME (TOTAL)   CALLS   ORIGIN       FUNCTION / FILE" + srvTag;
        } else if (mode == 3) {
            header = "TIME (TOTAL)   CALLS   ORIGIN       FUNCTION / FILE (MOST CALLS)" + srvTag;
        } else {
            header = "TIME (LAST 1s) CALLS   ORIGIN       FUNCTION / FILE" + srvTag;
        }
        textMgr.DrawString(FONT_DATA, lx, rowY, header, 0.7, 0.75, 0.85, 1.0);
        rowY += 22;

        if (isServer) {
            if (!zombie.network.GameClient.client || zombie.network.GameClient.connection == null) {
                textMgr.DrawString(FONT_DATA, lx, rowY, "STATUS: Available only when connected to a Multiplayer Server", 0.9, 0.8, 0.4, 1.0);
                return;
            }
            List<PerfRenderer.FormattedCall> srvCalls = ServerLuaMonitor.getTopCalls();
            if (srvCalls.isEmpty()) {
                String waitMsg = ServerLuaMonitor.hasReceivedData
                    ? "(No significant Lua activity recorded on server)"
                    : "(Waiting for server Lua data...)";
                textMgr.DrawString(FONT_DATA, lx, rowY, waitMsg, 0.6, 0.6, 0.6, 1.0);
                return;
            }
            for (PerfRenderer.FormattedCall c : srvCalls) {
                String line = c.getFullLine(12);
                textMgr.DrawString(FONT_DATA, lx, rowY, line, c.r, c.g, c.b, 1.0);
                rowY += 20;
            }
            return;
        }

        List<PerfRenderer.FormattedCall> calls = PerfRenderer.getTopCalls(15, 1000);
        if (calls.isEmpty()) {
            String emptyMsg = (mode == 1) ? "(No Lua activity recorded in the last 1s)" : "(No Lua activity recorded)";
            textMgr.DrawString(FONT_DATA, lx, rowY, emptyMsg, 0.6, 0.6, 0.6, 1.0);
            return;
        }

        for (PerfRenderer.FormattedCall c : calls) {
            String line = c.getFullLine(12);
            textMgr.DrawString(FONT_DATA, lx, rowY, line, c.r, c.g, c.b, 1.0);
            rowY += 20;
        }
    }

    private static void renderModDataList(TextManager textMgr, int lx, int rowY) {
        int mode = ZBGamePerf.modDataOsdMode;
        if (mode == 0) mode = 1;
        boolean isServer = ServerModDataMonitor.isServerSource();
        String srvTag = isServer ? " [SERVER]" : "";

        String header = (mode == 2) ? "DIR   TAG (TX ONLY)           RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)" + srvTag
                      : (mode == 3) ? "DIR   TAG (RX ONLY)           RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)" + srvTag
                      :               "DIR   TAG                     RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)" + srvTag;
        textMgr.DrawString(FONT_DATA, lx, rowY, header, 0.7, 0.75, 0.85, 1.0);
        rowY += 22;

        if (isServer) {
            if (!zombie.network.GameClient.client || zombie.network.GameClient.connection == null) {
                textMgr.DrawString(FONT_DATA, lx, rowY, "STATUS: Available only when connected to a Multiplayer Server", 0.9, 0.8, 0.4, 1.0);
                return;
            }
            List<ModDataMonitor.ModDataEntry> srvEntries = ServerModDataMonitor.getEntries();
            if (srvEntries.isEmpty()) {
                String waitMsg = ServerModDataMonitor.hasReceivedData
                    ? "(No significant GlobalModData activity recorded on server)"
                    : "(Waiting for server GlobalModData data...)";
                textMgr.DrawString(FONT_DATA, lx, rowY, waitMsg, 0.6, 0.6, 0.6, 1.0);
                return;
            }
            int count = 0;
            for (ModDataMonitor.ModDataEntry e : srvEntries) {
                if (++count > 15) break;
                String prefix = e.isTx ? "[TX]" : "[RX]";
                String line = String.format(java.util.Locale.US,
                    "%-4s  %-22s  %3d/min (%4.1f/s)    %9s    %9s  (%d)",
                    prefix, e.tag, e.getCountLastMinute(), e.getRatePerSecond(),
                    ModDataMonitor.formatBytes(e.lastBytes), ModDataMonitor.formatBytes(e.totalBytes), e.totalCount);

                double r = e.isTx ? 0.35 : 0.4;
                double g = e.isTx ? 0.85 : 1.0;
                double b = e.isTx ? 1.0 : 0.45;
                textMgr.DrawString(FONT_DATA, lx, rowY, line, r, g, b, 1.0);
                rowY += 20;
            }
            return;
        }

        List<ModDataMonitor.ModDataEntry> entries = ModDataMonitor.getEntries(mode);
        if (entries.isEmpty()) {
            textMgr.DrawString(FONT_DATA, lx, rowY, "(No GlobalModData activity recorded)", 0.6, 0.6, 0.6, 1.0);
            return;
        }

        int count = 0;
        for (ModDataMonitor.ModDataEntry e : entries) {
            if (++count > 15) break;
            String prefix = e.isTx ? "[TX]" : "[RX]";
            String line = String.format(java.util.Locale.US,
                "%-4s  %-22s  %3d/min (%4.1f/s)    %9s    %9s  (%d)",
                prefix, e.tag, e.getCountLastMinute(), e.getRatePerSecond(),
                ModDataMonitor.formatBytes(e.lastBytes), ModDataMonitor.formatBytes(e.totalBytes), e.totalCount);

            double r = e.isTx ? 0.35 : 0.4;
            double g = e.isTx ? 0.85 : 1.0;
            double b = e.isTx ? 1.0 : 0.45;
            textMgr.DrawString(FONT_DATA, lx, rowY, line, r, g, b, 1.0);
            rowY += 20;
        }
    }

    private static void renderCommandList(TextManager textMgr, int lx, int rowY) {
        int mode = ZBGamePerf.commandOsdMode;
        if (mode == 0) mode = 1;
        boolean isServer = ServerCommandMonitor.isServerSource();
        String srvTag = isServer ? " [SERVER]" : "";

        String header = (mode == 2) ? "DIR   MODULE:COMMAND (TX ONLY)     RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)" + srvTag
                      : (mode == 3) ? "DIR   MODULE:COMMAND (RX ONLY)     RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)" + srvTag
                      :               "DIR   MODULE:COMMAND               RATE/MIN (RATE/S)   LAST SIZE    TOTAL BYTES  (PKTS)" + srvTag;
        textMgr.DrawString(FONT_DATA, lx, rowY, header, 0.7, 0.75, 0.85, 1.0);
        rowY += 22;

        if (isServer) {
            if (!zombie.network.GameClient.client || zombie.network.GameClient.connection == null) {
                textMgr.DrawString(FONT_DATA, lx, rowY, "STATUS: Available only when connected to a Multiplayer Server", 0.9, 0.8, 0.4, 1.0);
                return;
            }
            List<CommandMonitor.CommandEntry> srvEntries = ServerCommandMonitor.getEntries();
            if (srvEntries.isEmpty()) {
                String waitMsg = ServerCommandMonitor.hasReceivedData
                    ? "(No significant Client/Server Commands recorded on server)"
                    : "(Waiting for server Commands data...)";
                textMgr.DrawString(FONT_DATA, lx, rowY, waitMsg, 0.6, 0.6, 0.6, 1.0);
                return;
            }
            int count = 0;
            for (CommandMonitor.CommandEntry e : srvEntries) {
                if (++count > 15) break;
                String prefix = e.isTx ? "[TX]" : "[RX]";
                String target = e.module + ":" + e.command;
                String line = String.format(java.util.Locale.US,
                    "%-4s  %-26s  %3d/min (%4.1f/s)    %9s    %9s  (%d)",
                    prefix, target, e.getCountLastMinute(), e.getRatePerSecond(),
                    ModDataMonitor.formatBytes(e.lastBytes), ModDataMonitor.formatBytes(e.totalBytes), e.totalCount);

                double r = e.isTx ? 0.35 : 0.4;
                double g = e.isTx ? 0.85 : 1.0;
                double b = e.isTx ? 1.0 : 0.45;
                textMgr.DrawString(FONT_DATA, lx, rowY, line, r, g, b, 1.0);
                rowY += 20;
            }
            return;
        }

        List<CommandMonitor.CommandEntry> entries = CommandMonitor.getEntries(mode);
        if (entries.isEmpty()) {
            textMgr.DrawString(FONT_DATA, lx, rowY, "(No Client/Server Commands recorded)", 0.6, 0.6, 0.6, 1.0);
            return;
        }

        int count = 0;
        for (CommandMonitor.CommandEntry e : entries) {
            if (++count > 15) break;
            String prefix = e.isTx ? "[TX]" : "[RX]";
            String target = e.module + ":" + e.command;
            String line = String.format(java.util.Locale.US,
                "%-4s  %-26s  %3d/min (%4.1f/s)    %9s    %9s  (%d)",
                prefix, target, e.getCountLastMinute(), e.getRatePerSecond(),
                ModDataMonitor.formatBytes(e.lastBytes), ModDataMonitor.formatBytes(e.totalBytes), e.totalCount);

            double r = e.isTx ? 0.35 : 0.4;
            double g = e.isTx ? 0.85 : 1.0;
            double b = e.isTx ? 1.0 : 0.45;
            textMgr.DrawString(FONT_DATA, lx, rowY, line, r, g, b, 1.0);
            rowY += 20;
        }
    }

    private static void renderLgsList(TextManager textMgr, int lx, int rowY) {
        int mode = ZBGamePerf.loadGridSquareOsdMode;
        if (mode == 0) mode = 1;
        boolean isServer = ServerLgsMonitor.isServerSource();
        String srvTag = isServer ? " [SERVER]" : "";

        String header = (mode == 2) ? "ORIGIN  LOCATION:LINE                                           CALLS (RATE/S)   TOTAL TIME    AVG TIME    MAX SPIKE (TOTAL TIME)" + srvTag
                      : (mode == 3) ? "ORIGIN  LOCATION:LINE                                           CALLS (RATE/S)   TOTAL TIME    AVG TIME    MAX SPIKE (MOST CALLS)" + srvTag
                      :               "ORIGIN  LOCATION:LINE                                           CALLS (RATE/S)   TOTAL TIME    AVG TIME    MAX SPIKE (RECENT)" + srvTag;
        textMgr.DrawString(FONT_DATA, lx, rowY, header, 0.7, 0.75, 0.85, 1.0);
        rowY += 22;

        if (isServer) {
            if (!zombie.network.GameClient.client || zombie.network.GameClient.connection == null) {
                textMgr.DrawString(FONT_DATA, lx, rowY, "STATUS: Available only when connected to a Multiplayer Server", 0.9, 0.8, 0.4, 1.0);
                return;
            }
            List<ServerLgsMonitor.FormattedLgsEntry> srvEntries = ServerLgsMonitor.getEntries();
            if (srvEntries.isEmpty()) {
                String waitMsg = ServerLgsMonitor.hasReceivedData
                    ? "(No significant LoadGridSquare activity recorded on server)"
                    : "(Waiting for server LoadGridSquare data...)";
                textMgr.DrawString(FONT_DATA, lx, rowY, waitMsg, 0.6, 0.6, 0.6, 1.0);
                return;
            }
            int count = 0;
            for (ServerLgsMonitor.FormattedLgsEntry e : srvEntries) {
                if (++count > 15) break;
                String displayLoc = e.loc != null && e.loc.length() > 46 ? e.loc.substring(0, 43) + "..." : e.loc;
                String line = String.format(java.util.Locale.US,
                    "%-6s  %-46s  %6d (%4.1f/s)   %7.1fms    %6.1fus    %6.1fms",
                    e.prefix, displayLoc, e.totalCount, e.ratePerSec, e.totalMs, e.avgUs, e.maxMs);

                double r, g, b;
                if (e.totalCount == 0) {
                    r = 0.5; g = 0.8; b = 0.7;
                } else if (e.totalMs >= 50.0 || e.maxMs >= 10.0) {
                    r = 1.0; g = 0.4; b = 0.4;
                } else if (e.totalMs >= 10.0 || e.totalCount >= 500) {
                    r = 1.0; g = 0.85; b = 0.3;
                } else {
                    r = 0.4; g = 1.0; b = 0.5;
                }
                textMgr.DrawString(FONT_DATA, lx, rowY, line, r, g, b, 1.0);
                rowY += 20;
            }
            return;
        }

        List<LoadGridSquareMonitor.CallbackEntry> entries = LoadGridSquareMonitor.getEntries(mode);
        if (entries.isEmpty()) {
            textMgr.DrawString(FONT_DATA, lx, rowY, "(No LoadGridSquare callbacks registered)", 0.6, 0.6, 0.6, 1.0);
            return;
        }

        int count = 0;
        for (LoadGridSquareMonitor.CallbackEntry e : entries) {
            if (++count > 15) break;
            String prefixStr = e.info.prefix != null ? e.info.prefix.toString() : "[UNK]";
            double totalMs = e.totalDurationNs / 1_000_000.0;
            double avgUs = e.totalCount > 0 ? (e.totalDurationNs / 1_000.0) / e.totalCount : 0.0;
            double maxMs = e.maxDurationNs / 1_000_000.0;

            String loc = e.info.getFormattedLocation();
            String displayLoc = loc.length() > 46 ? loc.substring(0, 43) + "..." : loc;
            String line = String.format(java.util.Locale.US,
                "%-6s  %-46s  %6d (%4.1f/s)   %7.1fms    %6.1fus    %6.1fms",
                prefixStr, displayLoc, e.totalCount, e.getRatePerSecond(), totalMs, avgUs, maxMs);

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
            textMgr.DrawString(FONT_DATA, lx, rowY, line, r, g, b, 1.0);
            rowY += 20;
        }
    }

    private static void renderTableHeader(TextManager textMgr, int lx, int rowY, int lw, String title) {
        SpriteRenderer.instance.renderRect(lx, rowY, lw, 20, 0.13f, 0.16f, 0.22f, 0.9f);
        textMgr.DrawString(FONT_UI, lx + 16, rowY + 2, title, 1.0, 0.75, 0.35, 1.0);
        textMgr.DrawString(FONT_UI, lx + 400, rowY + 2, "Client", 1.0, 0.75, 0.35, 1.0);
        textMgr.DrawString(FONT_UI, lx + 710, rowY + 2, "Server", 1.0, 0.75, 0.35, 1.0);
    }

    private static void renderTableRow(TextManager textMgr, int lx, int rowY, int lw,
                                       String label, String clientVal, String serverVal, boolean isEven) {
        if (isEven) {
            SpriteRenderer.instance.renderRect(lx, rowY, lw, 18, 0.08f, 0.09f, 0.12f, 0.45f);
        }
        textMgr.DrawString(FONT_DATA, lx + 20, rowY + 1, label, 0.85, 0.88, 0.92, 1.0);

        boolean cNil = "nil".equals(clientVal) || "-".equals(clientVal);
        double cr = cNil ? 0.5 : 0.95;
        double cg = cNil ? 0.5 : 0.95;
        double cb = cNil ? 0.5 : 0.95;
        textMgr.DrawString(FONT_DATA, lx + 400, rowY + 1, clientVal, cr, cg, cb, 1.0);

        boolean sNil = "nil".equals(serverVal) || "-".equals(serverVal) || serverVal.startsWith("No stream");
        double sr = sNil ? 0.5 : 0.95;
        double sg = sNil ? 0.5 : 0.95;
        double sb = sNil ? 0.5 : 0.95;
        textMgr.DrawString(FONT_DATA, lx + 710, rowY + 1, serverVal, sr, sg, sb, 1.0);
    }

    private static String fmtNum(double val) {
        if (val < 0) return "nil";
        return String.format(java.util.Locale.US, "%d", (long) Math.floor(val));
    }

    private static String fmtKB(double bytes) {
        if (bytes < 0) return "nil";
        return String.format(java.util.Locale.US, "%d", (long) Math.floor(bytes / 1024.0));
    }

    private static String fmtPct(double pct) {
        if (pct < 0) return "nil";
        return String.format(java.util.Locale.US, "%d", (long) Math.floor(pct * 100.0));
    }

    private static void renderNetworkList(TextManager textMgr, int lx, int rowY) {
        ServerNetMonitor.RakNetSnapshot r = ServerNetMonitor.getRakNetSnapshot();
        int lw = WIN_W - 24;

        if (!r.isConnected) {
            textMgr.DrawString(FONT_DATA, lx + 20, rowY + 10,
                "STATUS: Single Player / Not connected to a Multiplayer Server", 0.9, 0.8, 0.4, 1.0);
            textMgr.DrawString(FONT_DATA, lx + 20, rowY + 32,
                "(Network & RakNet metrics become active when connected to a LAN or Dedicated Server)", 0.6, 0.6, 0.6, 1.0);

            rowY += 50;
            renderTableHeader(textMgr, lx, rowY, lw, "Engine Round-Trip Ping/Pong (Load)");
            rowY += 20;
            renderTableRow(textMgr, lx, rowY, lw, "Engine Ping/Pong (Load) :", "Unavailable in Single Player", "-", true);
            rowY += 18;

            rowY += 4;
            renderTableHeader(textMgr, lx, rowY, lw, "Chunk Delivery & Streaming (Perimeter 100%)");
            rowY += 20;
            int row = 0;
            String syncVal = String.format(java.util.Locale.US, "%d / %d (%.1f%%)",
                ChunkDeliveryMonitor.loadedChunks, ChunkDeliveryMonitor.totalChunks, ChunkDeliveryMonitor.syncPct);
            String syncState = ChunkDeliveryMonitor.isWaiting ? "WAITING FOR LOAD" : "100% SYNCHRONIZED";
            renderTableRow(textMgr, lx, rowY, lw, "Perimeter Status :", syncVal, syncState, (row++ % 2 == 0));
            rowY += 18;

            String waitTime = ChunkDeliveryMonitor.isWaiting
                ? String.format(java.util.Locale.US, "%.0f ms (loading)", ChunkDeliveryMonitor.currentWaitMs)
                : String.format(java.util.Locale.US, "%.0f ms (last sync)", ChunkDeliveryMonitor.lastDeliveryTimeMs);
            String peakTime = String.format(java.util.Locale.US, "Peak: %.0f ms | Avg: %.0f ms",
                ChunkDeliveryMonitor.peakDeliveryTimeMs, ChunkDeliveryMonitor.avgDeliveryTimeMs);
            renderTableRow(textMgr, lx, rowY, lw, "Wait Time 100% Chunks :", waitTime, peakTime, (row++ % 2 == 0));
            rowY += 18;

            String inFlight = ChunkDeliveryMonitor.inFlightRequests + " pending requests";
            String queueStr = ChunkDeliveryMonitor.loadQueueSize + " chunks in decompression queue";
            renderTableRow(textMgr, lx, rowY, lw, "Chunk Queue (Disk) :", inFlight, queueStr, (row++ % 2 == 0));
            return;
        }

        int mode = netTabMode;
        if (mode == 1) {
            int row = 0;
            renderTableHeader(textMgr, lx, rowY, lw, "Network");
            rowY += 20;

            renderTableRow(textMgr, lx, rowY, lw, "RX (P) :", fmtNum(r.clientGameRxPackets), fmtNum(r.serverGameRxPackets), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "TX (P) :", fmtNum(r.clientGameTxPackets), fmtNum(r.serverGameTxPackets), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "RX (KB) :", fmtKB(r.clientGameRxKb), fmtKB(r.serverGameRxKb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "TX (KB) :", fmtKB(r.clientGameTxKb), fmtKB(r.serverGameTxKb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "RAKNET RX (KB) :", fmtKB(r.clientWireRxKb), fmtKB(r.serverWireRxKb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "RAKNET TX (KB) :", fmtKB(r.clientWireTxKb), fmtKB(r.serverWireTxKb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "RAKNET Resent (KB) :", fmtKB(r.clientResentKb), fmtKB(r.serverResentKb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "RAKNET Loss (%) :", fmtPct(r.clientLossPct), fmtPct(r.serverLossPct), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "VOIP RX (KB) :", fmtKB(r.clientVoipRxKb), fmtKB(r.serverVoipRxKb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "VOIP TX (KB) :", fmtKB(r.clientVoipTxKb), fmtKB(r.serverVoipTxKb), (row++ % 2 == 0));
            rowY += 18;

            rowY += 4;
            renderTableHeader(textMgr, lx, rowY, lw, "RakNet Diagnostics");
            rowY += 20;

            String pingCli = String.format(java.util.Locale.US, "%d ms (Avg: %d, Low: %d)", r.lastPing, r.avgPing, r.lowestPing);
            renderTableRow(textMgr, lx, rowY, lw, "Ping / Latency :", pingCli, "-", (row++ % 2 == 0));
            rowY += 18;

            String resendQueue = r.resendMsgs + " msgs in queue";
            renderTableRow(textMgr, lx, rowY, lw, "Resend Buffer Queue :", resendQueue, "-", (row++ % 2 == 0));
            rowY += 18;

            String cong = r.isCongested ? "ACTIVE (Throttled)" : "None (Unrestricted)";
            String congSrv = (r.bpsLimit > 0) ? (ModDataMonitor.formatBytes(r.bpsLimit) + "/s") : "-";
            renderTableRow(textMgr, lx, rowY, lw, "Congestion Control :", cong, congSrv, (row++ % 2 == 0));
            rowY += 18;

            String pingEngineVal;
            if (CommandPingMonitor.isWaiting) {
                pingEngineVal = "Waiting for Pong...";
            } else if (CommandPingMonitor.totalPongs > 0) {
                pingEngineVal = String.format(java.util.Locale.US, "%d ms (Avg: %.0f, Min: %d, Max: %d)",
                    CommandPingMonitor.lastRoundTripMs, CommandPingMonitor.avgRoundTripMs,
                    CommandPingMonitor.minRoundTripMs, CommandPingMonitor.maxRoundTripMs);
            } else if (CommandPingMonitor.timedOut) {
                pingEngineVal = "Timeout (> 5s)";
            } else {
                pingEngineVal = "Not tested (Click button)";
            }
            String pingEngineSrv = CommandPingMonitor.isWaiting ? "Processing..." : (CommandPingMonitor.totalPongs > 0 ? "Active (Round-Trip)" : "-");
            renderTableRow(textMgr, lx, rowY, lw, "Engine Ping/Pong (Load) :", pingEngineVal, pingEngineSrv, (row++ % 2 == 0));
            rowY += 18;

            rowY += 4;
            renderTableHeader(textMgr, lx, rowY, lw, "Chunk Delivery & Streaming (Perimeter 100%)");
            rowY += 20;

            String syncVal = String.format(java.util.Locale.US, "%d / %d (%.1f%%)",
                ChunkDeliveryMonitor.loadedChunks, ChunkDeliveryMonitor.totalChunks, ChunkDeliveryMonitor.syncPct);
            String syncState = ChunkDeliveryMonitor.isWaiting ? "WAITING FOR DELIVERY" : "100% SYNCHRONIZED";
            renderTableRow(textMgr, lx, rowY, lw, "Perimeter Status :", syncVal, syncState, (row++ % 2 == 0));
            rowY += 18;

            String waitTime = ChunkDeliveryMonitor.isWaiting
                ? String.format(java.util.Locale.US, "%.0f ms (waiting)", ChunkDeliveryMonitor.currentWaitMs)
                : String.format(java.util.Locale.US, "%.0f ms (last sync)", ChunkDeliveryMonitor.lastDeliveryTimeMs);
            String peakTime = String.format(java.util.Locale.US, "Peak: %.0f ms | Avg: %.0f ms",
                ChunkDeliveryMonitor.peakDeliveryTimeMs, ChunkDeliveryMonitor.avgDeliveryTimeMs);
            renderTableRow(textMgr, lx, rowY, lw, "Wait Time 100% Chunks :", waitTime, peakTime, (row++ % 2 == 0));
            rowY += 18;

            String inFlight = ChunkDeliveryMonitor.inFlightRequests + " in-flight packets (WorldStreamer)";
            String queueStr = ChunkDeliveryMonitor.loadQueueSize + " chunks in decompression queue";
            renderTableRow(textMgr, lx, rowY, lw, "Chunk Queue (Net/Disk) :", inFlight, queueStr, (row++ % 2 == 0));
            rowY += 18;
        } else {
            textMgr.DrawString(FONT_DATA, lx + 20, rowY,
                "--- RAKNET LOW-LEVEL UDP TRANSPORT & PHYSICAL WIRE METRICS ---", 0.35, 0.85, 1.0, 1.0);
            rowY += 20;

            String lossColor = (r.clientLossPct > 0.02) ? "HIGH" : "GOOD";
            String l1 = String.format(java.util.Locale.US,
                "Latency: %d ms (Avg: %d ms, Lowest: %d ms)     | Packet Loss (1s): %5.2f%% (%s)",
                r.lastPing, r.avgPing, r.lowestPing, r.clientLossPct * 100.0, lossColor);
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, l1, 1.0, 1.0, 1.0, 1.0);
            rowY += 19;

            String l2 = String.format(java.util.Locale.US,
                "Actual Wire Traffic:  TX %s/s  |  RX %s/s     (Physical UDP bytes on wire)",
                ModDataMonitor.formatBytes((long) r.clientWireTxKb), ModDataMonitor.formatBytes((long) r.clientWireRxKb));
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, l2, 0.4, 1.0, 0.5, 1.0);
            rowY += 19;

            String l3 = String.format(java.util.Locale.US,
                "Packet Resends:       %s resent      | Resend Queue: %d messages in buffer",
                ModDataMonitor.formatBytes((long) r.clientResentKb), r.resendMsgs);
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, l3, (r.resendMsgs > 5 ? 1.0 : 0.8), (r.resendMsgs > 5 ? 0.4 : 0.8), 0.8, 1.0);
            rowY += 19;

            String cong = r.isCongested ? "ACTIVE (Throttling connection)" : "None (Bandwidth unrestricted)";
            String l4 = String.format(java.util.Locale.US,
                "Congestion Control:   %s  | Bps Limit: %s",
                cong, (r.bpsLimit > 0 ? ModDataMonitor.formatBytes(r.bpsLimit) + "/s" : "Unlimited"));
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, l4, 0.8, 0.85, 0.9, 1.0);
            rowY += 24;

            textMgr.DrawString(FONT_DATA, lx + 20, rowY,
                "--- HIGHER LEVEL PROTOCOL OVERVIEW ---", 0.7, 0.75, 0.85, 1.0);
            rowY += 20;
            String l5 = String.format(java.util.Locale.US,
                "Game Application Packets:  TX: %5.1f pkts/s  |  RX: %5.1f pkts/s",
                r.clientGameTxPackets, r.clientGameRxPackets);
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, l5, 0.9, 0.9, 0.9, 1.0);
            rowY += 19;
            String l6 = String.format(java.util.Locale.US,
                "Game Application Bandwidth: TX: %s/s   |  RX: %s/s",
                ModDataMonitor.formatBytes((long) r.clientGameTxKb), ModDataMonitor.formatBytes((long) r.clientGameRxKb));
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, l6, 0.9, 0.9, 0.9, 1.0);
            rowY += 19;

            rowY += 6;
            textMgr.DrawString(FONT_DATA, lx + 20, rowY,
                "--- CHUNK DELIVERY & STREAMING (100% PERIMETER) ---", 0.7, 0.75, 0.85, 1.0);
            rowY += 20;
            String chunkDeliveryInfo = String.format(java.util.Locale.US,
                "Perimeter: %d/%d (%.1f%%) | Current Wait: %.0fms | Last: %.0fms | Peak: %.0fms | Avg: %.0fms",
                ChunkDeliveryMonitor.loadedChunks, ChunkDeliveryMonitor.totalChunks, ChunkDeliveryMonitor.syncPct,
                ChunkDeliveryMonitor.currentWaitMs, ChunkDeliveryMonitor.lastDeliveryTimeMs,
                ChunkDeliveryMonitor.peakDeliveryTimeMs, ChunkDeliveryMonitor.avgDeliveryTimeMs);
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, chunkDeliveryInfo, 0.4, 0.95, 0.65, 1.0);
            rowY += 19;
            String chunkQueueInfo = String.format(java.util.Locale.US,
                "In-flight requests (sentRequests): %d | Decompression queue: %d",
                ChunkDeliveryMonitor.inFlightRequests, ChunkDeliveryMonitor.loadQueueSize);
            textMgr.DrawString(FONT_DATA, lx + 20, rowY, chunkQueueInfo, 0.8, 0.85, 0.9, 1.0);
            rowY += 19;

            String pingEngineRaw;
            if (CommandPingMonitor.isWaiting) {
                pingEngineRaw = "Waiting for Pong...";
            } else if (CommandPingMonitor.totalPongs > 0) {
                pingEngineRaw = String.format(java.util.Locale.US, "%d ms (Avg: %.0f ms, Min: %d ms, Max: %d ms)",
                    CommandPingMonitor.lastRoundTripMs, CommandPingMonitor.avgRoundTripMs,
                    CommandPingMonitor.minRoundTripMs, CommandPingMonitor.maxRoundTripMs);
            } else if (CommandPingMonitor.timedOut) {
                pingEngineRaw = "Timeout (> 5s)";
            } else {
                pingEngineRaw = "Not tested (Click button)";
            }
            textMgr.DrawString(FONT_DATA, lx + 20, rowY,
                "Engine Ping/Pong (Load): " + pingEngineRaw, 0.35, 0.9, 1.0, 1.0);
            rowY += 19;
        }
    }

    private static void renderServerWorldList(TextManager textMgr, int lx, int rowY) {
        ServerNetMonitor.ServerWorldSnapshot s = ServerNetMonitor.getServerWorldSnapshot();
        int lw = WIN_W - 24;
        int mode = serverTabMode;
        int row = 0;

        if (mode == 1 || mode == 2) {
            renderTableHeader(textMgr, lx, rowY, lw, "Performance");
            rowY += 20;

            renderTableRow(textMgr, lx, rowY, lw, "Free (MB) :", fmtNum(s.clientMemFreeMb), fmtNum(s.serverMemFreeMb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Used (MB) :", fmtNum(s.clientMemUsedMb), fmtNum(s.serverMemUsedMb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Total (MB) :", fmtNum(s.clientMemTotalMb), fmtNum(s.serverMemTotalMb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Max (MB) :", fmtNum(s.clientMemMaxMb), fmtNum(s.serverMemMaxMb), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "FPS :", fmtNum(s.clientFps), fmtNum(s.serverFps), (row++ % 2 == 0));
            rowY += 18;

            String srvTick = (s.serverTickAvgMs >= 0)
                ? String.format(java.util.Locale.US, "%.1f ms (Min: %.1f, Max: %.1f)", s.serverTickAvgMs, s.serverTickMinMs, s.serverTickMaxMs)
                : "nil";
            renderTableRow(textMgr, lx, rowY, lw, "Tick Period (ms) :", "-", srvTick, (row++ % 2 == 0));
            rowY += 18;
            if (mode == 1) rowY += 4;
        }

        if (mode == 1 || mode == 3) {
            renderTableHeader(textMgr, lx, rowY, lw, "Characters");
            rowY += 20;

            renderTableRow(textMgr, lx, rowY, lw, "Players Connected :", fmtNum(s.clientPlayers), fmtNum(s.serverPlayers), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Animals Objects :", fmtNum(s.clientAnimalObjects), fmtNum(s.serverAnimalObjects), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Animals Instances :", fmtNum(s.clientAnimalInstances), fmtNum(s.serverAnimalInstances), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Animals Owned :", fmtNum(s.clientAnimalOwned), fmtNum(s.serverAnimalOwned), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Zombies Total :", fmtNum(s.clientZombiesTotal), fmtNum(s.serverZombiesTotal), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Zombies Loaded :", fmtNum(s.clientZombiesLoaded), fmtNum(s.serverZombiesLoaded), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Zombies Simulated :", fmtNum(s.clientZombiesSimulated), fmtNum(s.serverZombiesSimulated), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Zombies Culled :", fmtNum(s.clientZombiesCulled), fmtNum(s.serverZombiesCulled), (row++ % 2 == 0));
            rowY += 18;
            if (mode == 1) rowY += 4;

            renderTableHeader(textMgr, lx, rowY, lw, "World & Desyncs");
            rowY += 20;

            renderTableRow(textMgr, lx, rowY, lw, "Loaded Map Cells (300x300) :", fmtNum(s.clientLoadedCells), fmtNum(s.serverLoadedCells), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Player Teleports (Desync) :", "-", fmtNum(s.playerTeleports), (row++ % 2 == 0));
            rowY += 18;
            renderTableRow(textMgr, lx, rowY, lw, "Zombie Teleports (Desync) :", "-", fmtNum(s.zombieTeleports), (row++ % 2 == 0));
            rowY += 18;
        }
    }

    private static void renderPoolsList(TextManager textMgr, int lx, int rowY) {
        boolean isServer = ServerPoolsMonitor.isServerSource();
        ServerNetMonitor.PoolsSnapshot snap = isServer ? ServerPoolsMonitor.getPoolsSnapshot() : ServerNetMonitor.getPoolsSnapshot();
        String srvTag = isServer ? " [SERVER]" : "";

        textMgr.DrawString(FONT_DATA, lx, rowY,
            "POOL OBJECT TYPE                               IN USE      FREE     TOTAL     REUSED (CACHE)   NEW ALLOCS" + srvTag, 0.7, 0.75, 0.85, 1.0);
        rowY += 22;

        if (isServer) {
            if (!zombie.network.GameClient.client || zombie.network.GameClient.connection == null) {
                textMgr.DrawString(FONT_DATA, lx, rowY, "STATUS: Available only when connected to a Multiplayer Server", 0.9, 0.8, 0.4, 1.0);
                return;
            }
            if (snap.items.isEmpty()) {
                String waitMsg = ServerPoolsMonitor.hasReceivedData
                    ? "(No Object Pools registered on server)"
                    : "(Waiting for server Object Pools data...)";
                textMgr.DrawString(FONT_DATA, lx, rowY, waitMsg, 0.6, 0.6, 0.6, 1.0);
                return;
            }
        } else {
            if (snap.items.isEmpty()) {
                textMgr.DrawString(FONT_DATA, lx, rowY, "(No Object Pools registered)", 0.6, 0.6, 0.6, 1.0);
                return;
            }
        }

        List<ServerNetMonitor.PoolItem> list = new ArrayList<>(snap.items);
        if (poolsTabMode == 2) {
            list.sort((a, b) -> Long.compare(b.reused, a.reused));
        } else if (poolsTabMode == 3) {
            list.sort((a, b) -> Long.compare(b.alloc, a.alloc));
        } else {
            list.sort((a, b) -> Integer.compare(b.used, a.used));
        }

        int count = 0;
        for (ServerNetMonitor.PoolItem item : list) {
            if (++count > 15) break;

            long totalOps = item.reused + item.alloc;
            double hitRate = (totalOps > 0) ? (item.reused * 100.0 / totalOps) : 100.0;
            String hitStr = String.format(java.util.Locale.US, "%d (%4.1f%%)", item.reused, hitRate);

            String line = String.format(java.util.Locale.US,
                "%-42s  %7d   %7d   %7d   %14s   %10d",
                item.name, item.used, item.free, item.total, hitStr, item.alloc);

            double r = (hitRate < 80.0 && totalOps > 50) ? 1.0 : 0.5;
            double g = (hitRate < 80.0 && totalOps > 50) ? 0.6 : 1.0;
            double b = (hitRate < 80.0 && totalOps > 50) ? 0.4 : 0.65;
            textMgr.DrawString(FONT_DATA, lx, rowY, line, r, g, b, 1.0);
            rowY += 20;
        }
    }

    private static void renderChunkHud(TextManager textMgr) {
        int hx = ChunkDeliveryMonitor.hudX;
        int hy = ChunkDeliveryMonitor.hudY;
        int hw = ChunkDeliveryMonitor.HUD_W;
        int hh = ChunkDeliveryMonitor.HUD_H;

        // Background
        SpriteRenderer.instance.renderRect(hx, hy, hw, hh, 0.07f, 0.08f, 0.12f, 0.92f);

        boolean waiting = ChunkDeliveryMonitor.isWaiting;
        // Status accent line on left
        float ar = waiting ? 1.0f : 0.25f;
        float ag = waiting ? 0.75f : 0.95f;
        float ab = waiting ? 0.2f : 0.45f;
        SpriteRenderer.instance.renderRect(hx, hy, 4, hh, ar, ag, ab, 1.0f);

        ServerNetMonitor.RakNetSnapshot r = ServerNetMonitor.getRakNetSnapshot();
        int ping = r.isConnected ? r.lastPing : -1;
        String pingStr = (ping >= 0) ? (ping + "ms") : "SP";

        String text;
        if (waiting) {
            text = String.format(java.util.Locale.US,
                "[CHUNKS: %d/%d (%.0f%%)] Waiting: %.0fms (Peak: %.0fms) | Ping: %s",
                ChunkDeliveryMonitor.loadedChunks, ChunkDeliveryMonitor.totalChunks,
                ChunkDeliveryMonitor.syncPct, ChunkDeliveryMonitor.currentWaitMs,
                ChunkDeliveryMonitor.peakDeliveryTimeMs, pingStr);
        } else if (ChunkDeliveryMonitor.totalDeliveries > 0) {
            text = String.format(java.util.Locale.US,
                "[CHUNKS: 100%% OK] Delivered: %.0fms (Peak: %.0fms) | Ping: %s",
                ChunkDeliveryMonitor.lastDeliveryTimeMs, ChunkDeliveryMonitor.peakDeliveryTimeMs, pingStr);
        } else {
            text = String.format(java.util.Locale.US,
                "[CHUNKS: 100%% OK] Synchronized | Ping: %s", pingStr);
        }

        double tr = waiting ? 1.0 : 0.45;
        double tg = waiting ? 0.9 : 1.0;
        double tb = waiting ? 0.4 : 0.6;
        textMgr.DrawString(FONT_UI, hx + 10, hy + 5, text, tr, tg, tb, 1.0);

        // Close button [x]
        int closeX = hx + hw - 18;
        int closeY = hy + 4;
        SpriteRenderer.instance.renderRect(closeX, closeY, 14, 14, 0.35f, 0.15f, 0.15f, 0.9f);
        textMgr.DrawString(FONT_UI, closeX + 3, closeY, "x", 0.9, 0.9, 0.9, 1.0);
    }
}
