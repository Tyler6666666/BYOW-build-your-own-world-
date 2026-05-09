package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;
import tileengine.TETile;
import tileengine.Tileset;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class World {

    private static final int MIN_ROOM_WIDTH = 3;
    private static final int MAX_ROOM_WIDTH = 8;
    private static final int MIN_ROOM_HEIGHT = 3;
    private static final int MAX_ROOM_HEIGHT = 8;
    private static final int HALLWAY_WIDTH = 1;
    private static final int MARGIN = 3;
    private static final int STARTING_HP = 5;
    private static final int MIN_COINS = 4;
    private static final int MAX_COINS = 9;
    private static final int MIN_SCORE = 0;
    private static final int MIN_MONSTER_SPAWN_DISTANCE = 6;
    private static final int HALLWAY_ENTRANCE_BUFFER = 2;
    private static final int MONSTER_FREEZE_MOVES_AFTER_HIT = 2;
    private static final double MIN_VISIBLE_BRIGHTNESS = 0.28;
    private static final double VISIBLE_FADE_PADDING = 1.5;
    private static final int[][] ECHO_DIRECTIONS = new int[][]{
            {0, 1}, {1, 1}, {1, 0}, {1, -1},
            {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
    };

    private final long seed;
    private Difficulty difficulty;
    private final SaveableRandom rand;
    private final int WIDTH;
    private final int HEIGHT;

    private TETile[][] tiles;
    private List<Room> rooms;
    private List<Hallway> hallways;
    private List<Position> coinPositions;
    private List<MonsterState> monsters;
    private int avatarX;
    private int avatarY;
    private TETile avatarStandingOn;
    private Position exitPosition;
    private int score;
    private int hp;
    private int moveCount;
    private boolean gameWon;
    private boolean gameOver;
    private boolean lineOfSightEnabled;
    private boolean[][] visibleMask;

    // Construct the world. (1) no seed (2) with a seed (3) seed + width + height
    public World () {
        this(new Random().nextLong());
    }

    public World (long seed) {
        this(seed, 50, 50, Difficulty.NORMAL);
    }

    public World (int WIDTH, int HEIGHT) {
        this(new Random().nextLong(), WIDTH, HEIGHT, Difficulty.NORMAL);
    }

    public World (long seed, int WIDTH, int HEIGHT) {
        this(seed, WIDTH, HEIGHT, Difficulty.NORMAL);
    }

    public World(long seed, int WIDTH, int HEIGHT, Difficulty difficulty) {
        this.seed = seed;
        this.difficulty = difficulty;
        rand = new SaveableRandom(this.seed);
        this.WIDTH = WIDTH;
        this.HEIGHT = HEIGHT;
        tiles = new TETile[WIDTH][HEIGHT];
        rooms = new ArrayList<>();
        hallways = new ArrayList<>();
        coinPositions = new ArrayList<>();
        monsters = new ArrayList<>();
        avatarX = -1;
        avatarY = -1;
        avatarStandingOn = Tileset.FLOOR;
        exitPosition = null;
        score = 0;
        hp = STARTING_HP;
        moveCount = 0;
        gameWon = false;
        gameOver = false;
        lineOfSightEnabled = false;
        visibleMask = new boolean[WIDTH][HEIGHT];
        initializeEmptyWorld();
        generateRooms(0.10,2);
        generateHallways();
        connectRooms();
        generateWalls();
        placeAvatar();
        placeMonsters();
        placeCoins();
        refreshVisibility();
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
                candidate.setId(rooms.size());
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
                if (tiles[x][y] == Tileset.FLOOR || tiles[x][y] == Tileset.AVATAR) {
                    count++;
                }
            }
        }

        return count;
    }

    private void placeAvatar() {
        if (!rooms.isEmpty()) {
            Room startingRoom = rooms.get(0);
            for (int y = startingRoom.getY(); y < startingRoom.getY() + startingRoom.getHeight(); y++) {
                for (int x = startingRoom.getX(); x < startingRoom.getX() + startingRoom.getWidth(); x++) {
                    if (tiles[x][y].equals(Tileset.FLOOR)) {
                        avatarX = x;
                        avatarY = y;
                        avatarStandingOn = tiles[x][y];
                        tiles[x][y] = Tileset.AVATAR;
                        return;
                    }
                }
            }
        }

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (tiles[x][y].equals(Tileset.FLOOR)) {
                    avatarX = x;
                    avatarY = y;
                    avatarStandingOn = tiles[x][y];
                    tiles[x][y] = Tileset.AVATAR;
                    return;
                }
            }
        }
        throw new IllegalStateException("World generation produced no valid floor tile for avatar spawn.");
    }

    private void placeMonsters() {
        Position avatarPosition = new Position(avatarX, avatarY);

        for (Room room : rooms) {
            if (room.contains(avatarX, avatarY)) {
                continue;
            }

            List<Position> roomTiles = getRoomFloorPositions(room);
            if (roomTiles.isEmpty()) {
                continue;
            }

            roomTiles.sort(Comparator.comparingInt((Position p) -> heuristic(p, avatarPosition))
                    .reversed()
                    .thenComparingInt((Position p) -> p.y)
                    .thenComparingInt(p -> p.x));

            int maxForRoom = Math.min(
                    difficulty.getMaxMonstersPerRoom(),
                    Math.max(1, roomTiles.size() / 5)
            );
            maxForRoom = Math.min(maxForRoom, Math.max(1, roomTiles.size() / 2));
            maxForRoom = Math.min(maxForRoom, roomTiles.size());
            int minForRoom = Math.min(
                    difficulty.getMinMonstersPerRoom(),
                    maxForRoom
            );
            if (maxForRoom <= 0) {
                continue;
            }

            int targetMonsters = minForRoom;
            if (maxForRoom > minForRoom) {
                targetMonsters += rand.nextInt(maxForRoom - minForRoom + 1);
            }

            List<Position> preferredTiles = prioritizeMonsterTiles(roomTiles, avatarPosition);
            maxForRoom = Math.min(maxForRoom, preferredTiles.size());
            minForRoom = Math.min(minForRoom, maxForRoom);
            if (maxForRoom <= 0) {
                continue;
            }
            targetMonsters = Math.min(targetMonsters, maxForRoom);
            int placed = 0;
            for (Position candidate : preferredTiles) {
                if (placed >= targetMonsters) {
                    break;
                }
                if (!tiles[candidate.x][candidate.y].equals(Tileset.FLOOR)) {
                    continue;
                }
                monsters.add(new MonsterState(candidate.x, candidate.y, Tileset.FLOOR,
                        false, room.getId(), -1));
                tiles[candidate.x][candidate.y] = Tileset.BOGEYMAN;
                placed += 1;
            }
        }

        if (monsters.isEmpty()) {
            throw new IllegalStateException("World generation produced no valid floor tile for monster spawn.");
        }
    }

    private List<Position> getRoomFloorPositions(Room room) {
        List<Position> positions = new ArrayList<>();
        for (int x = room.getX(); x < room.getX() + room.getWidth(); x++) {
            for (int y = room.getY(); y < room.getY() + room.getHeight(); y++) {
                if (!tiles[x][y].equals(Tileset.FLOOR)) {
                    continue;
                }
                if (x == avatarX && y == avatarY) {
                    continue;
                }
                positions.add(new Position(x, y));
            }
        }
        return positions;
    }

    private List<Position> prioritizeMonsterTiles(List<Position> roomTiles, Position avatarPosition) {
        List<Position> preferred = new ArrayList<>();
        List<Position> entranceSafe = new ArrayList<>();
        List<Position> fallback = new ArrayList<>();
        Room room = roomTiles.isEmpty() ? null : findRoomForPosition(roomTiles.get(0).x, roomTiles.get(0).y);
        List<Position> entrances = room == null ? new ArrayList<>() : getRoomEntranceTiles(room);

        for (Position tile : roomTiles) {
            boolean farFromAvatar = heuristic(tile, avatarPosition) >= MIN_MONSTER_SPAWN_DISTANCE;
            boolean farFromEntrance = !isNearRoomEntrance(tile, entrances);
            if (farFromAvatar && farFromEntrance) {
                preferred.add(tile);
            } else if (farFromEntrance) {
                entranceSafe.add(tile);
            } else {
                fallback.add(tile);
            }
        }
        preferred.addAll(entranceSafe);
        preferred.addAll(fallback);
        return preferred;
    }

    private List<Position> getRoomInteriorTiles(Room room) {
        List<Position> roomTiles = getRoomFloorPositions(room);
        List<Position> entrances = getRoomEntranceTiles(room);
        List<Position> interiorTiles = new ArrayList<>();
        for (Position tile : roomTiles) {
            if (!isNearRoomEntrance(tile, entrances)) {
                interiorTiles.add(tile);
            }
        }
        return interiorTiles.isEmpty() ? roomTiles : interiorTiles;
    }

    private List<Position> getRoomEntranceTiles(Room room) {
        List<Position> entrances = new ArrayList<>();
        for (int x = room.getX(); x < room.getX() + room.getWidth(); x++) {
            for (int y = room.getY(); y < room.getY() + room.getHeight(); y++) {
                if (!tiles[x][y].equals(Tileset.FLOOR)) {
                    continue;
                }
                for (Position neighbor : neighbors(new Position(x, y))) {
                    if (!room.contains(neighbor.x, neighbor.y) && tiles[neighbor.x][neighbor.y].equals(Tileset.FLOOR)) {
                        entrances.add(new Position(x, y));
                        break;
                    }
                }
            }
        }
        return entrances;
    }

    private boolean isNearRoomEntrance(Position tile, List<Position> entrances) {
        for (Position entrance : entrances) {
            if (heuristic(tile, entrance) <= HALLWAY_ENTRANCE_BUFFER) {
                return true;
            }
        }
        return false;
    }

    public boolean moveAvatar(int dx, int dy, int coinValue, int monsterPenalty) {
        return moveAvatar(dx, dy, coinValue);
    }

    public boolean moveAvatar(int dx, int dy, int coinValue) {
        if (gameWon || gameOver) {
            return false;
        }

        int nextX = avatarX + dx;
        int nextY = avatarY + dy;
        if (!inBounds(nextX, nextY) || !isWalkableTile(tiles[nextX][nextY])) {
            return false;
        }

        moveCount += 1;

        tiles[avatarX][avatarY] = avatarStandingOn;
        avatarX = nextX;
        avatarY = nextY;
        avatarStandingOn = tiles[nextX][nextY];

        if (avatarStandingOn.equals(Tileset.COIN)) {
            score += Math.max(0, coinValue);
            coinPositions.remove(new Position(nextX, nextY));
            avatarStandingOn = Tileset.FLOOR;
            if (coinPositions.isEmpty() && exitPosition == null) {
                spawnExit();
            }
        }

        if (avatarStandingOn.equals(Tileset.EXIT) && coinPositions.isEmpty()) {
            gameWon = true;
        }

        tiles[avatarX][avatarY] = Tileset.AVATAR;
        refreshVisibility();
        return true;
    }

    public void stepMonsters(int monsterPenalty) {
        advanceMonsters(monsterPenalty);
        refreshVisibility();
    }

    public boolean canAvatarMove(int dx, int dy) {
        if (gameWon || gameOver) {
            return false;
        }
        int nextX = avatarX + dx;
        int nextY = avatarY + dy;
        return inBounds(nextX, nextY) && isWalkableTile(tiles[nextX][nextY]);
    }

    public List<Position> findClickablePath(int targetX, int targetY) {
        if (!inBounds(targetX, targetY) || !isTileVisible(targetX, targetY)
                || !isClickablePathTile(targetX, targetY)) {
            return new ArrayList<>();
        }

        if (targetX == avatarX && targetY == avatarY) {
            return new ArrayList<>();
        }

        boolean[][] visited = new boolean[WIDTH][HEIGHT];
        Position[][] parent = new Position[WIDTH][HEIGHT];
        ArrayDeque<Position> frontier = new ArrayDeque<>();
        Position start = new Position(avatarX, avatarY);
        Position target = new Position(targetX, targetY);

        frontier.add(start);
        visited[avatarX][avatarY] = true;

        while (!frontier.isEmpty()) {
            Position current = frontier.removeFirst();
            if (current.equals(target)) {
                break;
            }

            for (Position neighbor : neighbors(current)) {
                if (visited[neighbor.x][neighbor.y] || !isClickablePathTile(neighbor.x, neighbor.y)) {
                    continue;
                }
                visited[neighbor.x][neighbor.y] = true;
                parent[neighbor.x][neighbor.y] = current;
                frontier.addLast(neighbor);
            }
        }

        if (!visited[targetX][targetY]) {
            return new ArrayList<>();
        }

        return reconstructPath(parent, target);
    }

    private List<Position> reconstructPath(Position[][] parent, Position target) {
        ArrayDeque<Position> reversed = new ArrayDeque<>();
        Position current = target;
        while (current != null && !(current.x == avatarX && current.y == avatarY)) {
            reversed.addFirst(current);
            current = parent[current.x][current.y];
        }
        return new ArrayList<>(reversed);
    }

    public boolean restoreAvatarPosition(int x, int y) {
        if (!inBounds(x, y)) {
            return false;
        }

        if (!(tiles[x][y].equals(Tileset.FLOOR) || tiles[x][y].equals(Tileset.AVATAR))) {
            if (!tiles[x][y].equals(Tileset.COIN) && !tiles[x][y].equals(Tileset.EXIT)) {
            return false;
        }
        }

        if (inBounds(avatarX, avatarY) && tiles[avatarX][avatarY].equals(Tileset.AVATAR)) {
            tiles[avatarX][avatarY] = avatarStandingOn;
        }

        avatarX = x;
        avatarY = y;
        avatarStandingOn = tiles[x][y];
        tiles[avatarX][avatarY] = Tileset.AVATAR;
        refreshVisibility();
        return true;
    }

    public String getTileDescriptionAt(int x, int y) {
        if (!inBounds(x, y) || !isTileVisible(x, y)) {
            return "";
        }
        return tiles[x][y].description();
    }

    private void placeCoins() {
        List<Position> floorPositions = getAllFloorPositions();
        floorPositions.sort(Comparator.comparingInt((Position p) -> p.y).thenComparingInt(p -> p.x));

        int targetCoins = Math.min(floorPositions.size(),
                rand.nextInt(MAX_COINS - MIN_COINS + 1) + MIN_COINS);
        int placed = 0;
        int attempts = 0;

        while (placed < targetCoins && attempts < floorPositions.size() * 3) {
            attempts += 1;
            Position candidate = floorPositions.get(rand.nextInt(floorPositions.size()));
            if (tiles[candidate.x][candidate.y].equals(Tileset.FLOOR)) {
                tiles[candidate.x][candidate.y] = Tileset.COIN;
                coinPositions.add(candidate);
                placed += 1;
            }
        }
    }

    private void spawnExit() {
        List<Position> floorPositions = getAllFloorPositions();
        List<Position> candidates = new ArrayList<>();
        for (Position candidate : floorPositions) {
            if ((candidate.x != avatarX || candidate.y != avatarY)
                    && !isMonsterAt(candidate.x, candidate.y)) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        Position chosen = candidates.get(rand.nextInt(candidates.size()));
        tiles[chosen.x][chosen.y] = Tileset.EXIT;
        exitPosition = chosen;
    }

    private List<Position> getAllFloorPositions() {
        List<Position> floors = new ArrayList<>();
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (tiles[x][y].equals(Tileset.FLOOR)) {
                    floors.add(new Position(x, y));
                }
            }
        }
        return floors;
    }

    private boolean isWalkableTile(TETile tile) {
        return tile.equals(Tileset.FLOOR) || tile.equals(Tileset.COIN) || tile.equals(Tileset.EXIT);
    }

    private boolean isClickablePathTile(int x, int y) {
        if (!inBounds(x, y) || !isTileVisible(x, y)) {
            return false;
        }
        if (x == avatarX && y == avatarY) {
            return true;
        }
        return isWalkableTile(tiles[x][y]);
    }

    public void restoreState(int avatarX, int avatarY, int score, int hp, int moveCount,
                             List<Position> remainingCoins, Position restoredExit,
                             List<MonsterSnapshot> monsterSnapshots) {
        clearDynamicTiles();
        exitPosition = null;

        coinPositions.clear();
        for (Position coin : remainingCoins) {
            if (inBounds(coin.x, coin.y)) {
                tiles[coin.x][coin.y] = Tileset.COIN;
                coinPositions.add(coin);
            }
        }

        if (restoredExit != null && inBounds(restoredExit.x, restoredExit.y)) {
            exitPosition = restoredExit;
            tiles[restoredExit.x][restoredExit.y] = Tileset.EXIT;
        }

        this.score = score;
        this.hp = hp;
        this.moveCount = moveCount;
        this.gameWon = false;
        this.gameOver = hp <= 0;
        restoreMonsters(monsterSnapshots);
        restoreAvatarPosition(avatarX, avatarY);
        refreshVisibility();
        if (avatarStandingOn.equals(Tileset.EXIT) && coinPositions.isEmpty()) {
            gameWon = true;
        }
    }

    private void clearDynamicTiles() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (tiles[x][y].equals(Tileset.COIN) || tiles[x][y].equals(Tileset.EXIT)
                        || tiles[x][y].equals(Tileset.BOGEYMAN)) {
                    tiles[x][y] = Tileset.FLOOR;
                }
            }
        }
        monsters.clear();
        if (inBounds(avatarX, avatarY) && tiles[avatarX][avatarY].equals(Tileset.AVATAR)) {
            tiles[avatarX][avatarY] = Tileset.FLOOR;
        }
    }

    private void restoreMonsters(List<MonsterSnapshot> monsterSnapshots) {
        monsters.clear();
        for (MonsterSnapshot snapshot : monsterSnapshots) {
            if (!inBounds(snapshot.x, snapshot.y)
                    || tiles[snapshot.x][snapshot.y].equals(Tileset.WALL)
                    || tiles[snapshot.x][snapshot.y].equals(Tileset.NOTHING)) {
                continue;
            }

            Room room = findRoomForPosition(snapshot.x, snapshot.y);
            if (room == null) {
                continue;
            }

            MonsterState monster = new MonsterState(snapshot.x, snapshot.y,
                    tiles[snapshot.x][snapshot.y], snapshot.active, room.getId(),
                    snapshot.frozenUntilMoveCount);
            monsters.add(monster);
            tiles[snapshot.x][snapshot.y] = Tileset.BOGEYMAN;
        }
    }

    private void advanceMonsters(int monsterPenalty) {
        if (gameWon || gameOver) {
            return;
        }

        boolean tookDamageThisTurn = false;
        for (MonsterState monster : monsters) {
            if (!monster.active) {
                continue;
            }

            if (moveCount <= monster.frozenUntilMoveCount) {
                continue;
            }

            Position nextStep = chooseMonsterStep(monster);
            if (nextStep == null) {
                continue;
            }

            if (nextStep.x == avatarX && nextStep.y == avatarY) {
                if (!tookDamageThisTurn) {
                    hp -= 1;
                    score = Math.max(MIN_SCORE, score - Math.max(0, monsterPenalty));
                    tookDamageThisTurn = true;
                    freezeVisibleMonsters();
                    if (hp <= 0) {
                        gameOver = true;
                    }
                }
                continue;
            }

            tiles[monster.x][monster.y] = monster.standingOn;
            monster.x = nextStep.x;
            monster.y = nextStep.y;
            monster.standingOn = tiles[nextStep.x][nextStep.y];
            tiles[monster.x][monster.y] = Tileset.BOGEYMAN;
        }
    }

    private void freezeVisibleMonsters() {
        int frozenUntil = moveCount + MONSTER_FREEZE_MOVES_AFTER_HIT;
        for (MonsterState monster : monsters) {
            if (monster.active && isWithinLineOfSight(monster.x, monster.y)) {
                monster.frozenUntilMoveCount = Math.max(monster.frozenUntilMoveCount, frozenUntil);
            }
        }
    }

    private void refreshVisibility() {
        visibleMask = new boolean[WIDTH][HEIGHT];
        if (!inBounds(avatarX, avatarY)) {
            return;
        }

        revealEchoArea(avatarX, avatarY);
        for (int[] direction : ECHO_DIRECTIONS) {
            traceEchoRay(direction[0], direction[1]);
        }

        for (MonsterState monster : monsters) {
            if (!monster.active && visibleMask[monster.x][monster.y]) {
                monster.active = true;
            }
        }
    }

    private void traceEchoRay(int dx, int dy) {
        int x = avatarX;
        int y = avatarY;

        for (int step = 0; step < difficulty.getEchoRange(); step += 1) {
            x += dx;
            y += dy;
            if (!inBounds(x, y)) {
                return;
            }

            if (tiles[x][y].equals(Tileset.WALL)) {
                visibleMask[x][y] = true;
                return;
            }
            revealEchoArea(x, y);
        }
    }

    private void revealEchoArea(int centerX, int centerY) {
        for (int dx = -1; dx <= 1; dx += 1) {
            for (int dy = -1; dy <= 1; dy += 1) {
                if (Math.abs(dx) + Math.abs(dy) > 1) {
                    continue;
                }

                int x = centerX + dx;
                int y = centerY + dy;
                if (inBounds(x, y)) {
                    visibleMask[x][y] = true;
                }
            }
        }
    }

    private boolean isWithinLineOfSight(int x, int y) {
        return inBounds(x, y) && visibleMask[x][y];
    }

    public boolean isTileVisible(int x, int y) {
        return inBounds(x, y) && (!lineOfSightEnabled || visibleMask[x][y]);
    }

    public TETile[][] getVisibleTiles() {
        if (!lineOfSightEnabled) {
            return TETile.copyOf(tiles);
        }

        TETile[][] maskedTiles = TETile.copyOf(tiles);
        for (int x = 0; x < WIDTH; x += 1) {
            for (int y = 0; y < HEIGHT; y += 1) {
                if (!visibleMask[x][y]) {
                    maskedTiles[x][y] = Tileset.NOTHING;
                } else if (visibleMask[x][y]) {
                    maskedTiles[x][y] = applyVisibilityFade(maskedTiles[x][y], x, y);
                }
            }
        }
        return maskedTiles;
    }

    private TETile applyVisibilityFade(TETile tile, int x, int y) {
        if (tile.equals(Tileset.AVATAR) || tile.equals(Tileset.NOTHING)) {
            return tile;
        }

        double distance = Math.hypot(x - avatarX, y - avatarY);
        double maxDistance = difficulty.getEchoRange() + VISIBLE_FADE_PADDING;
        double normalizedDistance = Math.min(1.0, distance / maxDistance);
        double brightness = 1.0 - normalizedDistance * (1.0 - MIN_VISIBLE_BRIGHTNESS);
        return TETile.withBrightness(tile, brightness);
    }

    private Position chooseMonsterStep(MonsterState monster) {
        boolean primaryChaser = isPrimaryChaser(monster);
        if (!primaryChaser) {
            return choosePatrolStep(monster);
        }

        if (!isAvatarInMonsterRoom(monster)) {
            return choosePatrolStep(monster);
        }

        Position optimalStep = nextMonsterStep(monster);
        List<Position> candidates = getMonsterNeighborCandidates(monster, true);

        if (candidates.isEmpty()) {
            return null;
        }

        List<Position> safeCandidates = new ArrayList<>();
        for (Position candidate : candidates) {
            if (!wouldSealPlayerEscape(monster, candidate)
                    && !wouldOvercrowdPlayer(monster, candidate, primaryChaser)) {
                safeCandidates.add(candidate);
            }
        }
        if (!safeCandidates.isEmpty()) {
            candidates = safeCandidates;
        }

        int currentDistance = heuristic(new Position(monster.x, monster.y), new Position(avatarX, avatarY));
        double totalWeight = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i += 1) {
            Position candidate = candidates.get(i);
            int nextDistance = heuristic(candidate, new Position(avatarX, avatarY));

            double weight;
            if (optimalStep != null && candidate.equals(optimalStep)) {
                weight = difficulty.getOptimalWeight();
            } else if (nextDistance < currentDistance) {
                weight = difficulty.getCloserWeight();
            } else if (nextDistance == currentDistance) {
                weight = difficulty.getSideWeight();
            } else {
                weight = difficulty.getAwayWeight();
            }

            weights[i] = weight;
            totalWeight += weight;
        }

        double roll = rand.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i += 1) {
            cumulative += weights[i];
            if (roll <= cumulative) {
                return candidates.get(i);
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    private boolean isPrimaryChaser(MonsterState monster) {
        Room playerRoom = findRoomForPosition(avatarX, avatarY);
        if (playerRoom == null || monster.roomId != playerRoom.getId()) {
            return true;
        }

        MonsterState bestMonster = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MonsterState candidate : monsters) {
            if (!candidate.active || candidate.roomId != playerRoom.getId()) {
                continue;
            }
            int distance = heuristic(new Position(candidate.x, candidate.y), new Position(avatarX, avatarY));
            if (bestMonster == null || distance < bestDistance) {
                bestMonster = candidate;
                bestDistance = distance;
            }
        }
        return bestMonster == null || bestMonster == monster;
    }

    private boolean wouldOvercrowdPlayer(MonsterState movingMonster, Position candidate, boolean primaryChaser) {
        int candidateDistance = heuristic(candidate, new Position(avatarX, avatarY));
        if (candidateDistance > 1) {
            return false;
        }

        if (!primaryChaser) {
            return true;
        }

        int adjacentAttackers = 0;
        for (MonsterState monster : monsters) {
            int monsterDistance;
            if (monster == movingMonster) {
                monsterDistance = candidateDistance;
            } else {
                monsterDistance = heuristic(new Position(monster.x, monster.y), new Position(avatarX, avatarY));
            }
            if (monsterDistance <= 1) {
                adjacentAttackers += 1;
            }
        }
        return adjacentAttackers > 1;
    }

    private Position choosePatrolStep(MonsterState monster) {
        List<Position> candidates = getMonsterNeighborCandidates(monster, false);
        if (candidates.isEmpty()) {
            return null;
        }

        if (isAvatarInMonsterRoom(monster)) {
            List<Position> safeCandidates = new ArrayList<>();
            for (Position candidate : candidates) {
                if (heuristic(candidate, new Position(avatarX, avatarY)) > 1
                        && !wouldSealPlayerEscape(monster, candidate)) {
                    safeCandidates.add(candidate);
                }
            }
            if (!safeCandidates.isEmpty()) {
                candidates = safeCandidates;
            }
        }

        Room room = getRoomById(monster.roomId);
        List<Position> interiorTiles = room == null ? new ArrayList<>() : getRoomInteriorTiles(room);
        if (interiorTiles.isEmpty()) {
            return candidates.get(rand.nextInt(candidates.size()));
        }

        candidates.sort(Comparator.comparingInt((Position p) -> distanceToNearest(p, interiorTiles))
                .thenComparingInt((Position p) -> heuristic(p, new Position(avatarX, avatarY)))
                .reversed());
        return candidates.get(rand.nextInt(Math.min(2, candidates.size())));
    }

    private List<Position> getMonsterNeighborCandidates(MonsterState monster, boolean canAttackAvatar) {
        List<Position> candidates = new ArrayList<>();
        for (Position neighbor : neighbors(new Position(monster.x, monster.y))) {
            if (canAttackAvatar && neighbor.x == avatarX && neighbor.y == avatarY && isAvatarInMonsterRoom(monster)) {
                candidates.add(neighbor);
            } else if (isMonsterWalkable(neighbor.x, neighbor.y, monster)) {
                candidates.add(neighbor);
            }
        }
        return candidates;
    }

    private boolean wouldSealPlayerEscape(MonsterState movingMonster, Position candidate) {
        if (wouldBlockRoomExitPath(movingMonster, candidate)) {
            return true;
        }
        int currentEscapeRoutes = countPlayerEscapeRoutes(null, null);
        if (currentEscapeRoutes <= 0) {
            return false;
        }
        int nextEscapeRoutes = countPlayerEscapeRoutes(movingMonster, candidate);
        return nextEscapeRoutes <= 0;
    }

    private boolean wouldBlockRoomExitPath(MonsterState movingMonster, Position candidate) {
        Room playerRoom = findRoomForPosition(avatarX, avatarY);
        if (playerRoom == null) {
            return false;
        }

        List<Position> exits = getRoomEntranceTiles(playerRoom);
        if (exits.isEmpty()) {
            return false;
        }

        return !hasPathToAnyExit(playerRoom, exits, movingMonster, candidate);
    }

    private boolean hasPathToAnyExit(Room room, List<Position> exits,
                                     MonsterState movingMonster, Position movedPosition) {
        boolean[][] visited = new boolean[WIDTH][HEIGHT];
        ArrayDeque<Position> frontier = new ArrayDeque<>();
        Position start = new Position(avatarX, avatarY);

        frontier.add(start);
        visited[start.x][start.y] = true;

        while (!frontier.isEmpty()) {
            Position current = frontier.removeFirst();
            if (isExitTile(current, exits)) {
                return true;
            }

            for (Position neighbor : neighbors(current)) {
                if (visited[neighbor.x][neighbor.y] || !isPlayerReachableInRoom(neighbor, room, movingMonster, movedPosition)) {
                    continue;
                }
                visited[neighbor.x][neighbor.y] = true;
                frontier.addLast(neighbor);
            }
        }

        return false;
    }

    private boolean isExitTile(Position position, List<Position> exits) {
        for (Position exit : exits) {
            if (exit.equals(position)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlayerReachableInRoom(Position tile, Room room,
                                            MonsterState movingMonster, Position movedPosition) {
        if (!room.contains(tile.x, tile.y)) {
            return false;
        }
        if (movedPosition != null && movedPosition.equals(tile)) {
            return false;
        }
        if (movingMonster != null && movingMonster.x == tile.x && movingMonster.y == tile.y) {
            return isWalkableTile(movingMonster.standingOn);
        }
        if (isMonsterAt(tile.x, tile.y, movingMonster)) {
            return false;
        }
        TETile tileType = tiles[tile.x][tile.y];
        return tileType.equals(Tileset.AVATAR) || isWalkableTile(tileType);
    }

    private int countPlayerEscapeRoutes(MonsterState movingMonster, Position movedPosition) {
        int routes = 0;
        for (Position neighbor : neighbors(new Position(avatarX, avatarY))) {
            if (!isPlayerEscapeTile(neighbor.x, neighbor.y, movingMonster, movedPosition)) {
                continue;
            }
            routes += 1;
        }
        return routes;
    }

    private boolean isPlayerEscapeTile(int x, int y, MonsterState movingMonster, Position movedPosition) {
        if (!inBounds(x, y)) {
            return false;
        }
        if (movingMonster != null && x == movingMonster.x && y == movingMonster.y) {
            return movedPosition != null && isWalkableTile(movingMonster.standingOn);
        }
        TETile tile = tiles[x][y];
        if (!(tile.equals(Tileset.FLOOR) || tile.equals(Tileset.COIN) || tile.equals(Tileset.EXIT))) {
            return false;
        }
        if (movedPosition != null && movedPosition.x == x && movedPosition.y == y) {
            return false;
        }
        return !isMonsterAt(x, y, movingMonster);
    }

    private Position nextMonsterStep(MonsterState monster) {
        if (!isAvatarInMonsterRoom(monster)) {
            return null;
        }

        PriorityQueue<PathNode> frontier = new PriorityQueue<>(
                Comparator.comparingInt((PathNode n) -> n.priority)
                        .thenComparingInt(n -> n.position.y)
                        .thenComparingInt(n -> n.position.x)
        );
        boolean[][] visited = new boolean[WIDTH][HEIGHT];
        Position[][] parent = new Position[WIDTH][HEIGHT];
        int[][] cost = new int[WIDTH][HEIGHT];

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                cost[x][y] = Integer.MAX_VALUE;
            }
        }

        Position start = new Position(monster.x, monster.y);
        Position target = new Position(avatarX, avatarY);
        frontier.add(new PathNode(start, 0, heuristic(start, target)));
        cost[monster.x][monster.y] = 0;

        while (!frontier.isEmpty()) {
            PathNode current = frontier.poll();
            Position currentPos = current.position;

            if (visited[currentPos.x][currentPos.y]) {
                continue;
            }
            visited[currentPos.x][currentPos.y] = true;

            if (currentPos.equals(target)) {
                break;
            }

            for (Position neighbor : neighbors(currentPos)) {
                if (!isMonsterWalkable(neighbor.x, neighbor.y, monster) && !neighbor.equals(target)) {
                    continue;
                }
                int nextCost = current.cost + 1;
                if (nextCost < cost[neighbor.x][neighbor.y]) {
                    cost[neighbor.x][neighbor.y] = nextCost;
                    parent[neighbor.x][neighbor.y] = currentPos;
                    frontier.add(new PathNode(neighbor, nextCost, nextCost + heuristic(neighbor, target)));
                }
            }
        }

        if (parent[target.x][target.y] == null) {
            return null;
        }

        Position step = target;
        while (parent[step.x][step.y] != null && !parent[step.x][step.y].equals(start)) {
            step = parent[step.x][step.y];
        }
        return step;
    }

    private int heuristic(Position from, Position to) {
        return Math.abs(from.x - to.x) + Math.abs(from.y - to.y);
    }

    private List<Position> neighbors(Position position) {
        List<Position> result = new ArrayList<>();
        int[][] deltas = new int[][]{{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        for (int[] delta : deltas) {
            int nx = position.x + delta[0];
            int ny = position.y + delta[1];
            if (inBounds(nx, ny)) {
                result.add(new Position(nx, ny));
            }
        }
        return result;
    }

    private boolean isMonsterWalkable(int x, int y, MonsterState movingMonster) {
        TETile tile = tiles[x][y];
        return isTileInMonsterRoom(x, y, movingMonster)
                && isMonsterRoomInteriorTile(x, y, movingMonster)
                && (tile.equals(Tileset.FLOOR) || tile.equals(Tileset.COIN) || tile.equals(Tileset.EXIT))
                && !isMonsterAt(x, y, movingMonster);
    }

    private boolean isTileInMonsterRoom(int x, int y, MonsterState monster) {
        Room room = getRoomById(monster.roomId);
        return room != null && room.contains(x, y);
    }

    private boolean isMonsterRoomInteriorTile(int x, int y, MonsterState monster) {
        Room room = getRoomById(monster.roomId);
        if (room == null) {
            return false;
        }
        List<Position> interiorTiles = getRoomInteriorTiles(room);
        if (interiorTiles.isEmpty()) {
            return true;
        }
        return interiorTiles.contains(new Position(x, y));
    }

    private boolean isAvatarInMonsterRoom(MonsterState monster) {
        return isTileInMonsterRoom(avatarX, avatarY, monster);
    }

    private boolean isMonsterAt(int x, int y) {
        return isMonsterAt(x, y, null);
    }

    private boolean isMonsterAt(int x, int y, MonsterState ignoredMonster) {
        for (MonsterState monster : monsters) {
            if (monster != ignoredMonster && monster.x == x && monster.y == y) {
                return true;
            }
        }
        return false;
    }

    private Room findRoomForPosition(int x, int y) {
        for (Room room : rooms) {
            if (room.contains(x, y)) {
                return room;
            }
        }
        return null;
    }

    private Room getRoomById(int roomId) {
        for (Room room : rooms) {
            if (room.getId() == roomId) {
                return room;
            }
        }
        return null;
    }

    private int distanceToNearest(Position source, List<Position> targets) {
        int best = Integer.MAX_VALUE;
        for (Position target : targets) {
            best = Math.min(best, heuristic(source, target));
        }
        return best;
    }

    public long getSeed() {return seed;}

    public TETile[][] getTiles() {return tiles;}

    public void setTiles(TETile[][] tiles) {this.tiles = tiles;}

    public int getWIDTH() {return WIDTH;}

    public int getHEIGHT() {return HEIGHT;}

    public int getAvatarX() {
        return avatarX;
    }

    public int getAvatarY() {
        return avatarY;
    }

    public int getScore() {
        return score;
    }

    public int getHp() {
        return hp;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        if (difficulty == null) {
            return;
        }
        this.difficulty = difficulty;
        refreshVisibility();
    }

    public boolean isLineOfSightEnabled() {
        return lineOfSightEnabled;
    }

    public void setLineOfSightEnabled(boolean lineOfSightEnabled) {
        this.lineOfSightEnabled = lineOfSightEnabled;
        refreshVisibility();
    }

    public void toggleLineOfSight() {
        setLineOfSightEnabled(!lineOfSightEnabled);
    }

    public long getRandomState() {
        return rand.getState();
    }

    public void setRandomState(long randomState) {
        rand.setState(randomState);
    }

    public Position getExitPosition() {
        return exitPosition;
    }

    public int getBogeymanX() {
        return monsters.isEmpty() ? -1 : monsters.get(0).x;
    }

    public int getBogeymanY() {
        return monsters.isEmpty() ? -1 : monsters.get(0).y;
    }

    public boolean isChasing() {
        for (MonsterState monster : monsters) {
            if (monster.active) {
                return true;
            }
        }
        return false;
    }

    public boolean isGameWon() {
        return gameWon;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public List<Position> getCoinPositions() {
        return new ArrayList<>(coinPositions);
    }

    public List<MonsterSnapshot> getMonsterSnapshots() {
        List<MonsterSnapshot> snapshots = new ArrayList<>();
        for (MonsterState monster : monsters) {
            snapshots.add(new MonsterSnapshot(
                    monster.x,
                    monster.y,
                    monster.active,
                    monster.frozenUntilMoveCount
            ));
        }
        return snapshots;
    }

    public int getActiveMonsterCount() {
        int activeMonsters = 0;
        for (MonsterState monster : monsters) {
            if (monster.active) {
                activeMonsters += 1;
            }
        }
        return activeMonsters;
    }

    public int getMonsterCount() {
        return monsters.size();
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public static class Position {
        private final int x;
        private final int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Position)) {
                return false;
            }
            Position other = (Position) o;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    public static class MonsterSnapshot {
        private final int x;
        private final int y;
        private final boolean active;
        private final int frozenUntilMoveCount;

        public MonsterSnapshot(int x, int y, boolean active) {
            this(x, y, active, -1);
        }

        public MonsterSnapshot(int x, int y, boolean active, int frozenUntilMoveCount) {
            this.x = x;
            this.y = y;
            this.active = active;
            this.frozenUntilMoveCount = frozenUntilMoveCount;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public boolean isActive() {
            return active;
        }

        public int getFrozenUntilMoveCount() {
            return frozenUntilMoveCount;
        }
    }

    private static class MonsterState {
        private int x;
        private int y;
        private TETile standingOn;
        private boolean active;
        private final int roomId;
        private int frozenUntilMoveCount;

        MonsterState(int x, int y, TETile standingOn, boolean active, int roomId,
                     int frozenUntilMoveCount) {
            this.x = x;
            this.y = y;
            this.standingOn = standingOn;
            this.active = active;
            this.roomId = roomId;
            this.frozenUntilMoveCount = frozenUntilMoveCount;
        }
    }

    private static class PathNode {
        private final Position position;
        private final int cost;
        private final int priority;

        PathNode(Position position, int cost, int priority) {
            this.position = position;
            this.cost = cost;
            this.priority = priority;
        }
    }

    /**
     * Mirrors java.util.Random's 48-bit LCG so the world RNG can be saved and restored exactly.
     */
    private static class SaveableRandom {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;

        private long state;

        SaveableRandom(long seed) {
            setSeed(seed);
        }

        long getState() {
            return state;
        }

        void setState(long state) {
            this.state = state & MASK;
        }

        private void setSeed(long seed) {
            state = (seed ^ MULTIPLIER) & MASK;
        }

        private int nextBits(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            return (int) (state >>> (48 - bits));
        }

        int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }

            if ((bound & -bound) == bound) {
                return (int) ((bound * (long) nextBits(31)) >> 31);
            }

            int bits;
            int value;
            do {
                bits = nextBits(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }

        boolean nextBoolean() {
            return nextBits(1) != 0;
        }

        double nextDouble() {
            long high = (long) nextBits(26) << 27;
            long low = nextBits(27);
            return (high + low) / (double) (1L << 53);
        }
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
