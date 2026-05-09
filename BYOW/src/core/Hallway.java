package core;

public class Hallway {
    public final int u;
    public final int v;
    public final int weight;

    public Hallway(int u, int v, int weight) {
        this.u = u;
        this.v = v;
        this.weight = weight;
    }

    @Override
    public String toString() {
        return "(" + u + ", " + v + ") weight=" + weight;
    }

}
