package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TETile;
import tileengine.Tileset;

import java.util.Random;

public class World {

    private Random rand;
    private TETile[][] tiles;
    private final int WIDTH;
    private final int HEIGHT;

    // Construct the world. (1) with a seed (2) no seed
    public World () {
        rand = new Random();
        WIDTH = 50;
        HEIGHT = 50;
        tiles = new TETile[WIDTH][HEIGHT];
        initializeEmptyTiles();
    }

    public World (long seed, int WIDTH, int HEIGHT) {
        rand = new Random(seed);
        this.WIDTH = WIDTH;
        this.HEIGHT = HEIGHT;
        tiles = new TETile[WIDTH][HEIGHT];
        initializeEmptyTiles();
    }

    public void initializeEmptyTiles() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                tiles[x][y] = Tileset.NOTHING;
            }
        }
    }

    public TETile[][] getTiles() {return tiles;}

    public void setTiles(TETile[][] tiles) {this.tiles = tiles;}

    public int WIDTH() {return WIDTH;}

    public int getHEIGHT() {return HEIGHT;}
}
