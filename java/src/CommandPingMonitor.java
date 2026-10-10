package me.p_menta.zb_game_perf;

import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

public class CommandPingMonitor {
    public static volatile boolean isWaiting = false;
    public static volatile boolean timedOut = false;
    public static volatile long lastSentMs = 0;
    public static volatile long lastRoundTripMs = -1;
    public static volatile long minRoundTripMs = -1;
    public static volatile long maxRoundTripMs = -1;
    public static volatile double avgRoundTripMs = -1.0;
    public static volatile int totalPongs = 0;
    private static long sumRoundTripMs = 0;
    public static volatile String status = "Not tested";

    // ponytail: 5s timeout heuristic for server response under heavy tick stall; upgrade path is configurable sandbox option
    private static final long TIMEOUT_MS = 5000L;

    public static synchronized void sendPing() {
        if (isWaiting) {
            return;
        }
        try {
            if (!GameClient.client || GameClient.connection == null) {
                status = "Requires Multiplayer Server";
                return;
            }
            IsoPlayer player = IsoPlayer.getInstance();
            lastSentMs = System.currentTimeMillis();
            isWaiting = true;
            timedOut = false;
            status = "Waiting for Pong...";
            GameClient.instance.sendClientCommand(player, "ZBGamePerf", "Ping", (se.krka.kahlua.vm.KahluaTable) null);
        } catch (Throwable t) {
            isWaiting = false;
            status = "Error sending ping";
        }
    }

    public static synchronized void onPongReceived() {
        if (!isWaiting) {
            return;
        }
        long now = System.currentTimeMillis();
        long rtt = Math.max(0, now - lastSentMs);
        lastRoundTripMs = rtt;
        if (minRoundTripMs < 0 || rtt < minRoundTripMs) {
            minRoundTripMs = rtt;
        }
        if (rtt > maxRoundTripMs) {
            maxRoundTripMs = rtt;
        }
        totalPongs++;
        sumRoundTripMs += rtt;
        avgRoundTripMs = (double) sumRoundTripMs / totalPongs;
        isWaiting = false;
        timedOut = false;
        status = rtt + " ms";
    }

    public static synchronized void update() {
        if (isWaiting && (System.currentTimeMillis() - lastSentMs > TIMEOUT_MS)) {
            isWaiting = false;
            timedOut = true;
            status = "Timeout (> 5s)";
        }
    }

    public static synchronized void reset() {
        isWaiting = false;
        timedOut = false;
        lastSentMs = 0;
        lastRoundTripMs = -1;
        minRoundTripMs = -1;
        maxRoundTripMs = -1;
        avgRoundTripMs = -1.0;
        totalPongs = 0;
        sumRoundTripMs = 0;
        status = "Not tested";
    }
}
