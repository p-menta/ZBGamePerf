# ZBGamePerf

High-performance real-time diagnostic and profiling suite for Project Zomboid (Build 42+), built on top of the ZombieBuddy bytecode instrumentation framework.

Provides deep insights into Lua execution, network bandwidth, network command round-trips, packet loss, entity counts, object pooling, and game engine internals on both client and dedicated server.

---

## Changes Compared to the Original Project (Fork Improvements)

This fork extends the original ZBGamePerf with dedicated server profiling, unified diagnostic UI, and engine metrics:

1. **Unified Multi-Tab Diagnostic Interface**:
   - Consolidated standalone overlay windows into a single, cohesive dashboard with 7 specialized tabs (Lua Calls, GlobalModData, Commands, LoadGridSquare, Network, Server/World, Object Pools).
   - Movable window with tab hotkeys (`Ctrl+1` through `Ctrl+7`), reset (`Ctrl+0`), and HUD overlay shortcut (`Ctrl+H`).
   - Clean mod path formatting (`Mod_Name:/path/to/file.lua:line`) that keeps the UI organized without overflowing columns.

2. **Full Client vs Server Telemetry (On-Demand & Zero Passive Overhead)**:
   - Client and Server toggle buttons on every relevant tab to inspect either client execution or server-side telemetry directly inside the game.
   - Zero-overhead architecture: server measurement hooks remain dormant until requested by an admin client UI. If the client closes the window or disconnects, the server automatically pauses profiling after 5 seconds to conserve CPU.

3. **Server Engine Ping & Network Diagnostics**:
   - Engine-level ping/pong round-trip test (`ReqPing` / `ReqPong`) measuring true network + Lua thread queue response latency, distinct from OS/ICMP ping.
   - Real-time RakNet UDP metrics (latency, loss rate, resend buffer, wire throughput).
   - Real-time chunk delivery stall detector and on-screen HUD (`Ctrl+H`).

4. **Object Pool & Memory Optimization Monitoring**:
   - Real-time tracking of JVM object pools (`IsoZombie`, `IsoPlayer`, vectors, network buffers).
   - Inspection of pool cache hits vs allocations to diagnose garbage collection stutter.

---

## Installation

### Client (Singleplayer / Multiplayer Admin)
1. Install **ZombieBuddy** in your game directory or mod folder as required by your B42 setup.
2. Place `ZBGamePerf` into your Zomboid mods folder (`Zomboid/mods/ZBGamePerf`).
3. Enable `ZBGamePerf` in the Mods menu in-game.

### Dedicated Server (Linux / Windows)
1. Copy `ZombieBuddy.jar` and `ZBGamePerf.jar` to your server's `Project Zomboid Dedicated Server/java/` directory.
2. In your server startup config (`ProjectZomboid64.json` on Linux, or server batch file):
   - Add the `-javaagent` argument:
     ```
     -javaagent:java/ZombieBuddy.jar=patches_jar=java/ZBGamePerf.jar:me.p_menta.zb_game_perf
     ```
   - Ensure `ZBGamePerf.jar` and `ZombieBuddy.jar` are included in the classpath (`-Djava.class.path=...`).
3. Start the server.

---

## How to Use

### Hotkeys
- **`Ctrl + F11`**: Toggle the Unified Performance Monitor UI open/closed.
- **`Ctrl + 1` to `Ctrl + 7`**: Switch tabs directly.
- **`Ctrl + P`**: Toggle profiling scope:
  - `ACTIVE TAB`: Profiles only the currently viewed tab (recommended for minimal overhead).
  - `ALL TABS`: Concurrently profiles all tabs.
- **`Ctrl + 0`**: Reset all accumulators and graphs across all monitors.
- **`Ctrl + H`**: Toggle the On-Screen Chunk Delivery HUD.

### Tabs Overview
1. **Lua Calls**: Real-time breakdown of Lua function execution time, call frequency, and spikes per mod and file.
2. **GlobalModData**: Bandwidth analysis (TX / RX) per mod data tag with 60-second activity graphs.
3. **Commands**: Client/Server command packet rates, payload sizes, and call frequency.
4. **LoadGridSquare**: Callbacks triggered during map chunk / grid square loading, identifying heavy world-gen listeners.
5. **Network**: RakNet UDP stats, packet loss, latency, engine ping round-trip test, and chunk delivery queue.
6. **Server/World**: Client vs Server FPS, tick delta, JVM RAM usage, loaded cells, zombies, animals, and vehicle counts.
7. **Object Pools**: Active object pool usage, reuse rate, and fresh allocation rates to diagnose GC pressure.
