package dev.xyat.taczworkshop.crafting;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Capacity allocation shared by material previews and crafting; one stock unit can satisfy only one input. */
public final class MaterialPlanner {
    public record Stock(long count, int priority, boolean[] matches) {}
    private MaterialPlanner() {}

    public static long[] allocate(long[] needs, List<Stock> stocks) {
        long total = 0;
        for (long need : needs) {
            if (need < 0 || need > Long.MAX_VALUE / 1024 - total) return null;
            total += need;
        }
        int sink = stocks.size() + needs.length + 1;
        List<List<Edge>> graph = new ArrayList<>();
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        Edge[] stockEdges = new Edge[stocks.size()];
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (stock.count < 0 || stock.priority < 0 || stock.priority > 2 || stock.matches.length != needs.length) return null;
            // First maximize RS use, then player use. One extra RS item wins over every possible backpack saving.
            long cost = stock.priority == 0 ? 0 : total + (stock.priority == 1 ? 1 : 2);
            stockEdges[i] = add(graph, 0, i + 1, stock.count, cost);
            for (int j = 0; j < needs.length; j++) if (stock.matches[j]) add(graph, i + 1, stocks.size() + j + 1, total, 0);
        }
        for (int j = 0; j < needs.length; j++) add(graph, stocks.size() + j + 1, sink, needs[j], 0);
        long flow = 0;
        while (flow < total) {
            long[] distance = new long[sink + 1];
            Arrays.fill(distance, Long.MAX_VALUE);
            int[] previous = new int[sink + 1], previousEdge = new int[sink + 1];
            boolean[] queued = new boolean[sink + 1];
            var queue = new ArrayDeque<Integer>();
            distance[0] = 0; queue.add(0); queued[0] = true;
            while (!queue.isEmpty()) {
                int node = queue.remove(); queued[node] = false;
                for (int i = 0; i < graph.get(node).size(); i++) {
                    Edge edge = graph.get(node).get(i);
                    if (edge.capacity <= 0 || distance[edge.to] <= distance[node] + edge.cost) continue;
                    distance[edge.to] = distance[node] + edge.cost;
                    previous[edge.to] = node; previousEdge[edge.to] = i;
                    if (!queued[edge.to]) { queue.add(edge.to); queued[edge.to] = true; }
                }
            }
            if (distance[sink] == Long.MAX_VALUE) return null;
            long amount = total - flow;
            for (int node = sink; node != 0; node = previous[node]) amount = Math.min(amount, graph.get(previous[node]).get(previousEdge[node]).capacity);
            for (int node = sink; node != 0; node = previous[node]) {
                Edge edge = graph.get(previous[node]).get(previousEdge[node]);
                edge.capacity -= amount; graph.get(node).get(edge.reverse).capacity += amount;
            }
            flow += amount;
        }
        long[] used = new long[stocks.size()];
        for (int i = 0; i < stocks.size(); i++) used[i] = stocks.get(i).count - stockEdges[i].capacity;
        return used;
    }

    private static Edge add(List<List<Edge>> graph, int from, int to, long capacity, long cost) {
        Edge edge = new Edge(to, graph.get(to).size(), capacity, cost);
        graph.get(from).add(edge); graph.get(to).add(new Edge(from, graph.get(from).size() - 1, 0, -cost));
        return edge;
    }

    private static final class Edge {
        final int to, reverse;
        final long cost;
        long capacity;
        Edge(int to, int reverse, long capacity, long cost) { this.to = to; this.reverse = reverse; this.capacity = capacity; this.cost = cost; }
    }
}
