package core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// TODO: finish graph and find minimum spaning tree
public class Graph {

    private final List<Room> rooms;
    private final List<Hallway> hallways;

    public Graph() {
        this.rooms = new ArrayList<>();
        this.hallways = new ArrayList<>();
    }

    public Graph(List<Room> rooms) {
        this.rooms = new ArrayList<>(rooms);
        this.hallways = new ArrayList<>();
        buildCompleteGraph();
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public List<Hallway> gethallways() {
        return hallways;
    }

    /**
     * Builds a complete undirected graph:
     * every pair of rooms gets exactly one weighted Hallway.
     */
    private void buildCompleteGraph() {
        hallways.clear();
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                Room a = rooms.get(i);
                Room b = rooms.get(j);
                int weight = a.l1DistanceTo(b);
                hallways.add(new Hallway(i, j, weight));
            }
        }
    }

    /**
     * Returns the MST as a list of hallways using Kruskal's algorithm.
     */
    public List<Hallway> mstHallways() {
        List<Hallway> sortedHallways = new ArrayList<>(hallways);
        sortedHallways.sort(Comparator.comparingInt(e -> e.weight));

        UnionFind uf = new UnionFind(rooms.size());
        List<Hallway> msthallways = new ArrayList<>();

        for (Hallway e : sortedHallways) {
            if (!uf.connected(e.u, e.v)) {
                uf.union(e.u, e.v);
                msthallways.add(e);

                if (msthallways.size() == rooms.size() - 1) {
                    break;
                }
            }
        }

        return msthallways;
    }

    private static class UnionFind {
        private final int[] parent;
        private final int[] rank;

        UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                rank[i] = 0;
            }
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        boolean connected(int a, int b) {
            return find(a) == find(b);
        }

        void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);

            if (rootA == rootB) {
                return;
            }

            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
        }
    }
}
