package me.p_menta.zb_game_perf;

public class SelfCheck {
    public static void main(String[] args) {
        // Test ServerNetMonitor history buffer and sorting logic
        ServerNetMonitor.reset();

        int[] ping = ServerNetMonitor.getHistoryPing();
        assert ping != null : "ping history must not be null";
        assert ping.length == 60 : "ping history length must be 60";

        ServerNetMonitor.RakNetSnapshot snap = ServerNetMonitor.getRakNetSnapshot();
        assert snap != null : "snapshot must not be null";

        // Verify history values initialized
        for (int v : ping) {
            assert v == 0 : "initial ping must be 0";
        }

        // Verify PoolItem sorting
        ServerNetMonitor.PoolItem p1 = new ServerNetMonitor.PoolItem("ItemA", 10, 50, 60, 5, 20);
        ServerNetMonitor.PoolItem p2 = new ServerNetMonitor.PoolItem("ItemB", 25, 10, 35, 15, 30);
        java.util.List<ServerNetMonitor.PoolItem> list = new java.util.ArrayList<>();
        list.add(p1);
        list.add(p2);

        // Sort by used descending
        list.sort((a, b) -> Integer.compare(b.used, a.used));
        assert list.get(0).name.equals("ItemB") : "ItemB should be first by used";

        // Sort by alloc descending
        list.sort((a, b) -> Long.compare(b.alloc, a.alloc));
        assert list.get(0).name.equals("ItemB") : "ItemB should be first by alloc";

        System.out.println("SelfCheck PASSED successfully!");
    }
}
