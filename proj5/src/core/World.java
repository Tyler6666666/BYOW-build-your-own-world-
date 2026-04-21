package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;
import tileengine.TETile;
import tileengine.Tileset;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {

    private static final int MIN_ROOM_WIDTH = 3;
    private static final int MAX_ROOM_WIDTH = 8;
    private static final int MIN_ROOM_HEIGHT = 3;
    private static final int MAX_ROOM_HEIGHT = 8;
    private static final int HALLWAY_WIDTH = 1;
    private static final int MARGIN = 3;

    private final long seed;
    private final Random rand;
    private final int WIDTH;
    private final int HEIGHT;

    private TETile[][] tiles;
    private List<Room> rooms;
    private List<Hallway> hallways;

    // Construct the world. (1) no seed (2) with a seed (3) seed + width + height
    public World () {
        this(new Random().nextLong());
    }

    public World (long seed) {
        this(seed, 50, 50);
    }

    public World (int WIDTH, int HEIGHT) {
        this(new Random().nextLong(), WIDTH, HEIGHT);
    }

    public World (long seed, int WIDTH, int HEIGHT) {
        this.seed = seed;
        rand = new Random(this.seed);
        this.WIDTH = WIDTH;
        this.HEIGHT = HEIGHT;
        tiles = new TETile[WIDTH][HEIGHT];
        rooms = new ArrayList<>();
        hallways = new ArrayList<>();
        initializeEmptyWorld();
        generateRooms(0.1,2);
        generateHallways();
        connectRooms();
        generateWalls();
    }

    // Initialize the empty world by fill in the tiles with NOTING.
    public void initializeEmptyWorld() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                tiles[x][y] = Tileset.NOTHING;
            }
        }
    }

    // Randomly place rooms on the map til the total floors goes above maxDensity * total number of Tiles in the world.
    // Gap is an optional parameter that separate the generated rooms by gap length
    public void generateRooms() {
        generateRooms(0.30, 2);
    }

    public void generateRooms(double maxDensity, int gap) {
        int floorSum = 0;
        int maxAttempts = 50000;
        int attempts = 0;

        while (floorSum < HEIGHT * WIDTH * maxDensity && attempts < maxAttempts) {
            attempts++;
            int roomWidth = rand.nextInt(MAX_ROOM_WIDTH - MIN_ROOM_WIDTH + 1) + MIN_ROOM_WIDTH;
            int roomHeight = rand.nextInt(MAX_ROOM_HEIGHT - MIN_ROOM_HEIGHT + 1) + MIN_ROOM_HEIGHT;

            int x = rand.nextInt(WIDTH - roomWidth - 2);
            int y = rand.nextInt(HEIGHT - roomHeight - 2 * MARGIN - 2) + MARGIN;

            Room candidate = new Room(x, y, roomWidth, roomHeight);
            boolean overlaps = false;
            for (Room room : rooms) {
                if (candidate.intersect(room, gap)) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                rooms.add(candidate);

                for (int i = x; i < x + roomWidth; i++) {
                    for (int j = y; j < y + roomHeight; j++) {
                        tiles[i][j] = Tileset.FLOOR;
                    }
                }

                floorSum += roomWidth * roomHeight;
            }
        }
//        System.out.printf("The %d rooms sum up to %d Floor out of  %d Tiles", rooms.size(), floorSum, WIDTH * HEIGHT);
    }

    // Calculate and generate the pairwise shortest hallway between each room
    public void generateHallways() {
        hallways = new Graph(rooms).mstHallways();
    }

    public void generateWalls() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (tiles[x][y] == Tileset.FLOOR) {
                    addWallsAroundFloorTile(x, y);
                }
            }
        }
    }

    private void addWallsAroundFloorTile(int x, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;

                if (inBounds(nx, ny) && tiles[nx][ny] == Tileset.NOTHING) {
                    tiles[nx][ny] = Tileset.WALL;
                }
            }
        }
    }

    // Connect Rooms with hallways
    public void connectRooms() {
        for (Hallway hallway : hallways) {
            Room a = rooms.get(hallway.u);
            Room b = rooms.get(hallway.v);
            connectTwoRooms(a, b);
        }
    }

    public void connectTwoRooms(Room a, Room b) {
        int aLeft = a.getX();
        int aRight = a.getX() + a.getWidth() - 1;
        int aBottom = a.getY();
        int aTop = a.getY() + a.getHeight() - 1;

        int bLeft = b.getX();
        int bRight = b.getX() + b.getWidth() - 1;
        int bBottom = b.getY();
        int bTop = b.getY() + b.getHeight() - 1;

        int overlapLeft = Math.max(aLeft, bLeft);
        int overlapRight = Math.min(aRight, bRight);

        int overlapBottom = Math.max(aBottom, bBottom);
        int overlapTop = Math.min(aTop, bTop);

        if (overlapLeft <= overlapRight) {
            int hallX = (overlapLeft + overlapRight) / 2;

            int startY;
            int endY;

            if (aTop < bBottom) {
                startY = aTop;
                endY = bBottom;
            } else {
                startY = bTop;
                endY = aBottom;
            }

            carveVerticalHallway(startY, endY, hallX);
            return;
        }

        if (overlapBottom <= overlapTop) {
            int hallY = (overlapBottom + overlapTop) / 2;

            int startX;
            int endX;

            if (aRight < bLeft) {
                startX = aRight;
                endX = bLeft;
            } else {
                startX = bRight;
                endX = aLeft;
            }

            carveHorizontalHallway(startX, endX, hallY);
            return;
        }

        int[] points = closestBoundaryPoints(a, b);
        int x1 = points[0];
        int y1 = points[1];
        int x2 = points[2];
        int y2 = points[3];

        if (rand.nextBoolean()) {
            carveHorizontalHallway(x1, x2, y1);
            carveVerticalHallway(y1, y2, x2);
        } else {
            carveVerticalHallway(y1, y2, x1);
            carveHorizontalHallway(x1, x2, y2);
        }
    }

    private int[] closestBoundaryPoints(Room a, Room b) {
        int bestX1 = -1;
        int bestY1 = -1;
        int bestX2 = -1;
        int bestY2 = -1;
        int bestDist = Integer.MAX_VALUE;

        for (int x1 = a.getX(); x1 < a.getX() + a.getWidth(); x1++) {
            for (int y1 = a.getY(); y1 < a.getY() + a.getHeight(); y1++) {
                if (isBoundaryTile(a, x1, y1)) {
                    continue;
                }

                for (int x2 = b.getX(); x2 < b.getX() + b.getWidth(); x2++) {
                    for (int y2 = b.getY(); y2 < b.getY() + b.getHeight(); y2++) {
                        if (isBoundaryTile(b, x2, y2)) {
                            continue;
                        }

                        int dist = Math.abs(x1 - x2) + Math.abs(y1 - y2);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestX1 = x1;
                            bestY1 = y1;
                            bestX2 = x2;
                            bestY2 = y2;
                        }
                    }
                }
            }
        }

        return new int[]{bestX1, bestY1, bestX2, bestY2};
    }

    private boolean isBoundaryTile(Room room, int x, int y) {
        int left = room.getX();
        int right = room.getX() + room.getWidth() - 1;
        int bottom = room.getY();
        int top = room.getY() + room.getHeight() - 1;

        return x != left && x != right && y != bottom && y != top;
    }

    private void carveHorizontalHallway(int x1, int x2, int y) {
        int start = Math.min(x1, x2);
        int end = Math.max(x1, x2);

        for (int x = start; x <= end; x++) {
            for (int w = 0; w < HALLWAY_WIDTH; w++) {
                if (inBounds(x, y + w)) {
                    tiles[x][y + w] = Tileset.FLOOR;
                }
            }
        }
    }


    private void carveVerticalHallway(int y1, int y2, int x) {
        int start = Math.min(y1, y2);
        int end = Math.max(y1, y2);

        for (int y = start; y <= end; y++) {
            for (int w = 0; w < HALLWAY_WIDTH; w++) {
                if (inBounds(x + w, y)) {
                    tiles[x + w][y] = Tileset.FLOOR;
                }
            }
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    public int countFloors() {
        int count = 0;

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (tiles[x][y] == Tileset.FLOOR) {
                    count++;
                }
            }
        }

        return count;
    }

    public long getSeed() {return seed;}

    public TETile[][] getTiles() {return tiles;}

    public void setTiles(TETile[][] tiles) {this.tiles = tiles;}

    public int getWIDTH() {return WIDTH;}

    public int getHEIGHT() {return HEIGHT;}

    public List<Room> getRooms() {
        return rooms;
    }

    static void main(String[] args) {

        int WORLD_WIDTH = 50;
        int WORLD_HEIGHT = 50;

        TERenderer ter = new TERenderer();
        ter.initialize(WORLD_WIDTH, WORLD_HEIGHT);

        World demoWorld = new World(42);
        System.out.printf("Total floor is %f", ((double)demoWorld.countFloors())/((double) WORLD_WIDTH * WORLD_HEIGHT));

        StdDraw.clear(new Color(0, 0, 0));
        ter.drawTiles(demoWorld.getTiles());
        StdDraw.show();

    }
}
