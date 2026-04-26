package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;
import utils.FileUtils;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.swing.JOptionPane;

public class Game {
    private static final int WORLD_WIDTH = 50;
    private static final int WORLD_HEIGHT = 50;
    private static final int HUD_ROWS = 5;
    private static final String PROJECT_SAVE_DIR = "src/core";
    private static final String SOURCE_ROOT_SAVE_DIR = "core";
    private static final String LEGACY_LOCAL_SAVE_DIR = "proj5/src/core";
    private static final int MAX_VISIBLE_SAVES = 8;
    private static final int BASE_COIN_SCORE = 10;
    private static final long COIN_DECAY_INTERVAL_MILLIS = 30_000L;
    private static final long MILLIS_PER_MOVE = 1_000L;
    private static final int MONSTER_SCORE_PENALTY = 3;
    private static final int PATH_ANIMATION_DELAY_MILLIS = 90;
    private static final SimpleDateFormat SAVE_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final TERenderer renderer;
    private Difficulty selectedDifficulty;

    public Game() {
        renderer = new TERenderer();
        selectedDifficulty = Difficulty.NORMAL;
    }

    public void run() {
        setup();

        while (true) {
            showMainMenu();
            handleMenuChoice(waitForMenuChoice());
        }
    }

    private void setup() {
        ensureSaveDirectory();
        renderer.initialize(WORLD_WIDTH, WORLD_HEIGHT + HUD_ROWS, 0, 0);
    }

    private void handleMenuChoice(char key) {
        switch (key) {
            case 'n':
                Long seed = promptForSeed();
                if (seed != null) {
                    World world = new World(seed, WORLD_WIDTH, WORLD_HEIGHT, selectedDifficulty);
                    saveWorld(world);
                    showWorld(world);
                }
                break;
            case 'l':
                loadMostRecentSave();
                break;
            case 'q':
                System.exit(0);
                break;
            default:
                break;
        }
    }

    private void showLoadMenu() {
        List<SaveEntry> saves = getSaveEntries();
        if (saves.isEmpty()) {
            showMenuNotice("No saves yet. The save folder is ready for new worlds.");
            return;
        }

        int selected = 0;
        boolean needsRedraw = true;
        while (true) {
            if (needsRedraw) {
                drawLoadScreen(saves, selected);
                needsRedraw = false;
            }

            if (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (key == 'w') {
                    int nextSelected = Math.max(0, selected - 1);
                    needsRedraw = nextSelected != selected;
                    selected = nextSelected;
                } else if (key == 's') {
                    int nextSelected = Math.min(saves.size() - 1, selected + 1);
                    needsRedraw = nextSelected != selected;
                    selected = nextSelected;
                } else if (key == 'd') {
                    if (confirmDeleteSave(saves.get(selected))) {
                        deleteSave(saves.get(selected));
                        saves = getSaveEntries();
                        if (saves.isEmpty()) {
                            showMenuNotice("All saves deleted.");
                            return;
                    }
                    selected = Math.min(selected, saves.size() - 1);
                    needsRedraw = true;
                }
                } else if (key == 'e' || key == '\n' || key == '\r') {
                    SaveData data = readSaveData(saves.get(selected));
                    selectedDifficulty = data.difficulty;
                    showWorld(buildWorldFromSave(data));
                    return;
                } else if (key == 'b') {
                    return;
                } else if (key == 'q') {
                    System.exit(0);
                }
            }

            StdDraw.pause(16);
        }
    }

    private void showWorld(World world) {
        int lastMouseX = mouseTileX();
        int lastMouseY = mouseTileY();
        boolean needsRedraw = true;
        boolean mouseWasPressed = false;
        boolean pendingQuitPrefix = false;
        World.Position selectedTarget = null;
        List<World.Position> selectedPath = new ArrayList<>();
        long lastDisplayedSecond = -1;

        while (true) {
            long elapsedMillis = elapsedMillisForMoveCount(world.getMoveCount());
            long elapsedSeconds = elapsedMillis / 1000;
            if (elapsedSeconds != lastDisplayedSecond) {
                lastDisplayedSecond = elapsedSeconds;
                needsRedraw = true;
            }

            if (world.isGameWon() || world.isGameOver()) {
                EndScreenAction action = showEndingScreen(world, elapsedMillis);
                if (action == EndScreenAction.NEW_GAME) {
                    Long seed = promptForSeed();
                    if (seed != null) {
                        world = new World(seed, WORLD_WIDTH, WORLD_HEIGHT, selectedDifficulty);
                        saveWorld(world);
                        selectedTarget = null;
                        selectedPath = new ArrayList<>();
                        lastDisplayedSecond = -1;
                        needsRedraw = true;
                        mouseWasPressed = false;
                        pendingQuitPrefix = false;
                        lastMouseX = mouseTileX();
                        lastMouseY = mouseTileY();
                    } else {
                        needsRedraw = true;
                    }
                    continue;
                }
                if (action == EndScreenAction.MAIN_MENU) {
                    return;
                }
                System.exit(0);
            }

            if (needsRedraw) {
                render(world, lastMouseX, lastMouseY, elapsedMillis, selectedPath, selectedTarget);
                StdDraw.show();
                needsRedraw = false;
            }

            while (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (pendingQuitPrefix) {
                    if (key == 'q') {
                        saveWorld(world);
                        System.exit(0);
                    }
                    pendingQuitPrefix = false;
                }

                if (key == ':') {
                    pendingQuitPrefix = true;
                    continue;
                }

                if (key == 'r') {
                    world = new World(world.getSeed(), WORLD_WIDTH, WORLD_HEIGHT, world.getDifficulty());
                    selectedTarget = null;
                    selectedPath = new ArrayList<>();
                    lastDisplayedSecond = -1;
                    needsRedraw = true;
                    lastMouseX = mouseTileX();
                    lastMouseY = mouseTileY();
                    continue;
                }

                if (key == 'm') {
                    if (confirmSaveBeforeLeaving(world, elapsedMillis, "return to the main menu")) {
                        return;
                    }
                }
                if (key == 't') {
                    Difficulty chosenDifficulty = showDifficultyMenu(
                            world.getDifficulty(),
                            "Choose Difficulty",
                            "Update the current world's line of sight and monster behavior.",
                            "Press E to apply or B to keep the current difficulty."
                    );
                    if (chosenDifficulty != null) {
                        world.setDifficulty(chosenDifficulty);
                        selectedDifficulty = chosenDifficulty;
                        selectedTarget = null;
                        selectedPath = new ArrayList<>();
                        needsRedraw = true;
                        lastMouseX = mouseTileX();
                        lastMouseY = mouseTileY();
                    }
                }
                if (key == 'v') {
                    world.toggleLineOfSight();
                    selectedTarget = null;
                    selectedPath = new ArrayList<>();
                    needsRedraw = true;
                    lastMouseX = mouseTileX();
                    lastMouseY = mouseTileY();
                }

                int[] direction = directionForKey(key);
                if (direction != null && !world.isGameOver() && !world.isGameWon()
                        && performPlayerStep(world, direction[0], direction[1])) {
                    selectedTarget = null;
                    selectedPath = new ArrayList<>();
                    lastDisplayedSecond = -1;
                    needsRedraw = true;
                    lastMouseX = mouseTileX();
                    lastMouseY = mouseTileY();
                }
            }

            int mouseX = mouseTileX();
            int mouseY = mouseTileY();
            if (mouseX != lastMouseX || mouseY != lastMouseY) {
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                needsRedraw = true;
            }

            boolean mousePressed = StdDraw.isMousePressed();
            if (mousePressed && !mouseWasPressed) {
                if (isClickableWorldTile(mouseX, mouseY) && world.isTileVisible(mouseX, mouseY)) {
                    World.Position clickedTarget = new World.Position(mouseX, mouseY);
                    if (selectedTarget != null && selectedTarget.equals(clickedTarget)
                            && !selectedPath.isEmpty()) {
                        followSelectedPath(world, selectedPath, lastMouseX, lastMouseY, selectedTarget);
                        selectedTarget = null;
                        selectedPath = new ArrayList<>();
                        lastDisplayedSecond = -1;
                        lastMouseX = mouseTileX();
                        lastMouseY = mouseTileY();
                    } else {
                        selectedPath = world.findClickablePath(mouseX, mouseY);
                        selectedTarget = selectedPath.isEmpty() ? null : clickedTarget;
                    }
                    needsRedraw = true;
                } else {
                    selectedTarget = null;
                    selectedPath = new ArrayList<>();
                    needsRedraw = true;
                }
            }
            mouseWasPressed = mousePressed;

            StdDraw.pause(16);
        }
    }

    private void render(World world, int mouseX, int mouseY, long elapsedMillis,
                        List<World.Position> selectedPath, World.Position selectedTarget) {
        StdDraw.clear(new Color(8, 11, 18));
        renderer.drawTiles(world.getVisibleTiles());
        drawPathOverlay(selectedPath, selectedTarget);
        drawHud(world, mouseX, mouseY, elapsedMillis);
    }

    private void drawHud(World world, int mouseX, int mouseY, long elapsedMillis) {
        double hudCenterY = WORLD_HEIGHT + HUD_ROWS / 2.0;

        StdDraw.setPenColor(new Color(12, 19, 31));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, hudCenterY, WORLD_WIDTH / 2.0, HUD_ROWS / 2.0);

        StdDraw.setPenColor(new Color(28, 42, 61));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT + 0.1, WORLD_WIDTH / 2.0, 0.18);

        double topRow = WORLD_HEIGHT + 3.85;
        double middleRow = WORLD_HEIGHT + 2.55;
        double bottomRow = WORLD_HEIGHT + 1.25;

        String objective = world.getCoinPositions().isEmpty()
                ? "Objective: Exit ready"
                : "Objective: " + world.getCoinPositions().size() + " coins left";
        if (world.isGameWon()) {
            objective = "Objective: Exit reached";
        } else if (world.isGameOver()) {
            objective = "Objective: Game over";
        }

        String hovered = world.getTileDescriptionAt(mouseX, mouseY);
        if (!world.isTileVisible(mouseX, mouseY) && mouseX >= 0 && mouseX < WORLD_WIDTH
                && mouseY >= 0 && mouseY < WORLD_HEIGHT) {
            hovered = "unknown";
        } else if (hovered.isEmpty()) {
            hovered = "outside world";
        }

        StdDraw.setFont(new Font("Arial", Font.BOLD, 10));
        StdDraw.setPenColor(new Color(230, 236, 243));
        StdDraw.textLeft(1.0, topRow,
                "Seed: " + world.getSeed()
                        + "   Difficulty: " + world.getDifficulty().getDisplayName()
                        + "   LOS: " + (world.isLineOfSightEnabled() ? "On" : "Off"));
        StdDraw.textRight(WORLD_WIDTH - 1.0, topRow, "Hover: " + hovered);

        StdDraw.setFont(new Font("Arial", Font.PLAIN, 10));
        StdDraw.setPenColor(new Color(183, 196, 210));
        StdDraw.textLeft(1.0, middleRow,
                "HP: " + world.getHp() + "   Score: " + world.getScore()
                        + "   Time: " + formatElapsedTime(elapsedMillis));
        StdDraw.textRight(WORLD_WIDTH - 1.0, middleRow,
                "Echo: " + world.getDifficulty().getEchoRange()
                        + "   Monsters: " + world.getActiveMonsterCount() + "/" + world.getMonsterCount()
                        + "   Moves: " + world.getMoveCount());

        StdDraw.setPenColor(new Color(241, 196, 15));
        StdDraw.textLeft(1.0, bottomRow,
                "Coin: " + currentCoinValue(world.getMoveCount()) + "   " + objective);
        StdDraw.setPenColor(new Color(173, 186, 199));
        StdDraw.textRight(WORLD_WIDTH - 1.0, bottomRow,
                "Move: WASD   Path: double click   Difficulty: T   LOS: V   Menu: M   Save+Quit: :Q   Restart: R");
    }

    private void drawPathOverlay(List<World.Position> selectedPath, World.Position selectedTarget) {
        if (selectedPath == null || selectedPath.isEmpty()) {
            return;
        }

        StdDraw.setPenColor(new Color(52, 152, 219));
        for (World.Position step : selectedPath) {
            StdDraw.filledCircle(step.getX() + 0.5, step.getY() + 0.5, 0.16);
        }

        if (selectedTarget != null) {
            StdDraw.setPenColor(new Color(241, 196, 15));
            StdDraw.setPenRadius(0.004);
            StdDraw.circle(selectedTarget.getX() + 0.5, selectedTarget.getY() + 0.5, 0.34);
            StdDraw.setPenRadius();
        }
    }

    private EndScreenAction showEndingScreen(World world, long elapsedMillis) {
        boolean needsRedraw = true;

        while (true) {
            if (needsRedraw) {
                drawEndingScreen(world, elapsedMillis);
                needsRedraw = false;
            }

            if (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (key == 'n') {
                    return EndScreenAction.NEW_GAME;
                }
                if (key == 'm') {
                    return EndScreenAction.MAIN_MENU;
                }
                if (key == 'q') {
                    return EndScreenAction.QUIT;
                }
            }

            StdDraw.pause(16);
        }
    }

    private void drawEndingScreen(World world, long elapsedMillis) {
        StdDraw.clear(new Color(8, 11, 18));
        drawMenuBackdrop();

        boolean won = world.isGameWon();
        Color titleColor = won ? new Color(93, 214, 122) : new Color(231, 76, 60);
        String title = won ? "Run Complete" : "Game Over";
        String subtitle = won
                ? "You reached the exit with every coin collected."
                : "The bogeymen caught up to you this time.";

        StdDraw.setPenColor(titleColor);
        StdDraw.setFont(new Font("Georgia", Font.BOLD, 28));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 4.0, title);

        StdDraw.setPenColor(new Color(226, 232, 240));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 14));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 7.0, subtitle);

        StdDraw.setPenColor(new Color(18, 30, 46));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 + 1.0, 17.5, 9.8);

        StdDraw.setPenColor(new Color(241, 196, 15));
        StdDraw.setFont(new Font("Arial", Font.BOLD, 15));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 + 7.0, "Final Summary");

        StdDraw.setPenColor(new Color(215, 224, 234));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 14));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 + 3.8,
                "Time: " + formatElapsedTime(elapsedMillis));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 + 1.4,
                "Score: " + world.getScore());
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 - 1.0,
                "Difficulty: " + world.getDifficulty().getDisplayName());
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 - 3.4,
                "Moves: " + world.getMoveCount() + "   HP: " + world.getHp());

        StdDraw.setPenColor(new Color(244, 246, 247));
        StdDraw.setFont(new Font("Arial", Font.BOLD, 16));
        StdDraw.text(WORLD_WIDTH / 2.0, 11.0, "[N] New Game");

        StdDraw.setPenColor(new Color(173, 186, 199));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 13));
        StdDraw.text(WORLD_WIDTH / 2.0, 8.2, "[M] Main Menu    [Q] Quit");
        StdDraw.show();
    }

    private boolean performPlayerStep(World world, int dx, int dy) {
        if (!world.canAvatarMove(dx, dy)) {
            return false;
        }

        boolean moved = world.moveAvatar(dx, dy, currentCoinValue(world.getMoveCount()));
        if (!moved) {
            return false;
        }

        world.stepMonsters(MONSTER_SCORE_PENALTY);
        return true;
    }

    private void followSelectedPath(World world, List<World.Position> path,
                                    int mouseX, int mouseY, World.Position selectedTarget) {
        if (path == null || path.isEmpty()) {
            return;
        }

        List<World.Position> remainingPath = new ArrayList<>(path);
        for (World.Position step : path) {
            int dx = step.getX() - world.getAvatarX();
            int dy = step.getY() - world.getAvatarY();
            if (!performPlayerStep(world, dx, dy)
                    || world.isGameOver() || world.isGameWon()) {
                long elapsedMillis = elapsedMillisForMoveCount(world.getMoveCount());
                render(world, mouseX, mouseY, elapsedMillis, remainingPath, selectedTarget);
                StdDraw.show();
                return;
            }

            remainingPath.remove(0);
            long elapsedMillis = elapsedMillisForMoveCount(world.getMoveCount());
            render(world, mouseX, mouseY, elapsedMillis, remainingPath, selectedTarget);
            StdDraw.show();
            StdDraw.pause(PATH_ANIMATION_DELAY_MILLIS);
        }
    }

    private boolean isClickableWorldTile(int x, int y) {
        return x >= 0 && x < WORLD_WIDTH && y >= 0 && y < WORLD_HEIGHT;
    }

    private int[] directionForKey(char key) {
        switch (key) {
            case 'w':
                return new int[]{0, 1};
            case 'a':
                return new int[]{-1, 0};
            case 's':
                return new int[]{0, -1};
            case 'd':
                return new int[]{1, 0};
            default:
                return null;
        }
    }

    private void showMainMenu() {
        StdDraw.clear(new Color(8, 11, 18));
        drawMenuBackdrop();

        StdDraw.setPenColor(new Color(241, 196, 15));
        StdDraw.setFont(new Font("Georgia", Font.BOLD, 28));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 2.0, "BYOW");

        StdDraw.setPenColor(new Color(215, 224, 234));
        StdDraw.setFont(new Font("Georgia", Font.BOLD, 16));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 5.0, "Blind Search Prototype");

        drawMenuCard();
        drawMenuOption(WORLD_HEIGHT - 9.0, "[N] New Game", "Enter a seed and generate a fresh world.");
        drawMenuOption(WORLD_HEIGHT - 17.0, "[L] Load Game", "Open a save and continue from its stored difficulty.");
        drawMenuOption(WORLD_HEIGHT - 25.0, "[Q] Quit", "Close the game window.");

        StdDraw.setPenColor(new Color(130, 146, 166));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 13));
        StdDraw.text(WORLD_WIDTH / 2.0, 5.0,
                "New Game starts right after seed entry. Press T in-world to change difficulty.");
        StdDraw.show();
    }

    private void drawMenuBackdrop() {
        StdDraw.setPenColor(new Color(14, 22, 34));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0,
                WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0);

        StdDraw.setPenColor(new Color(23, 39, 58));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 14.0, 18.0, 12.0);

        StdDraw.setPenColor(new Color(11, 70, 92));
        StdDraw.filledRectangle(9.0, 8.0, 8.0, 5.0);
        StdDraw.filledRectangle(42.0, 11.0, 6.0, 4.0);

        StdDraw.setPenColor(new Color(241, 196, 15));
        for (int i = 0; i < 10; i += 1) {
            double x = 6.0 + i * 4.2;
            double y = 46.0 + (i % 3);
            StdDraw.filledCircle(x, y, 0.12);
        }
    }

    private void drawMenuCard() {
        StdDraw.setPenColor(new Color(18, 30, 46));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 - 1.5, 18.0, 13.5);
    }

    private void drawMenuOption(double y, String title, String subtitle) {
        StdDraw.setPenColor(new Color(244, 246, 247));
        StdDraw.setFont(new Font("Arial", Font.BOLD, 18));
        StdDraw.text(WORLD_WIDTH / 2.0, y, title);

        StdDraw.setPenColor(new Color(161, 178, 195));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 13));
        StdDraw.text(WORLD_WIDTH / 2.0, y - 2.6, subtitle);
    }

    private char waitForMenuChoice() {
        while (true) {
            if (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (key == 'n' || key == 'l' || key == 'q') {
                    return key;
                }
            }
            StdDraw.pause(16);
        }
    }

    private Difficulty showDifficultyMenu(Difficulty currentDifficulty, String title,
                                          String subtitle, String footer) {
        Difficulty selected = currentDifficulty;
        boolean needsRedraw = true;

        while (true) {
            if (needsRedraw) {
                drawDifficultyScreen(selected, title, subtitle, footer);
                needsRedraw = false;
            }

            if (StdDraw.hasNextKeyTyped()) {
                char key = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (key == 'w') {
                    selected = previousDifficulty(selected);
                    needsRedraw = true;
                } else if (key == 's') {
                    selected = selected.next();
                    needsRedraw = true;
                } else if (key == 'e' || key == '\n' || key == '\r') {
                    return selected;
                } else if (key == 'b') {
                    return null;
                }
            }

            StdDraw.pause(16);
        }
    }

    private void drawDifficultyScreen(Difficulty selected, String title,
                                      String subtitle, String footer) {
        StdDraw.clear(new Color(8, 11, 18));
        drawMenuBackdrop();

        StdDraw.setPenColor(new Color(241, 196, 15));
        StdDraw.setFont(new Font("Georgia", Font.BOLD, 24));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 3.0, title);

        StdDraw.setPenColor(new Color(226, 232, 240));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 14));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 6.0,
                subtitle);

        StdDraw.setPenColor(new Color(18, 30, 46));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 - 2.0, 18.0, 12.5);

        double y = WORLD_HEIGHT - 13.0;
        for (Difficulty difficulty : Difficulty.values()) {
            boolean isSelected = difficulty == selected;
            if (isSelected) {
                StdDraw.setPenColor(new Color(64, 112, 170));
                StdDraw.filledRectangle(WORLD_WIDTH / 2.0, y - 0.6, 15.0, 1.8);
                StdDraw.setPenColor(new Color(241, 196, 15));
                StdDraw.setPenRadius(0.003);
                StdDraw.rectangle(WORLD_WIDTH / 2.0, y - 0.6, 15.0, 1.8);
                StdDraw.setPenRadius();
            }

            StdDraw.setPenColor(isSelected ? new Color(245, 247, 250) : new Color(198, 210, 223));
            StdDraw.setFont(new Font("Arial", isSelected ? Font.BOLD : Font.PLAIN, 15));
            StdDraw.text(WORLD_WIDTH / 2.0, y, difficulty.getDisplayName());
            y -= 4.8;
        }

        StdDraw.setPenColor(new Color(161, 178, 195));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 13));
        StdDraw.text(WORLD_WIDTH / 2.0, 5.0, footer);
        StdDraw.show();
    }

    private Difficulty previousDifficulty(Difficulty current) {
        Difficulty[] values = Difficulty.values();
        int index = current.ordinal() - 1;
        if (index < 0) {
            index = values.length - 1;
        }
        return values[index];
    }

    private Long promptForSeed() {
        StringBuilder seedBuilder = new StringBuilder();
        String helperText = "Type digits, press S to start, B to go back. Difficulty can be changed in-world with T.";
        boolean needsRedraw = true;

        while (true) {
            if (needsRedraw) {
                drawSeedScreen(seedBuilder.toString(), helperText);
                needsRedraw = false;
            }

            if (StdDraw.hasNextKeyTyped()) {
                char key = StdDraw.nextKeyTyped();
                char lower = Character.toLowerCase(key);

                if (Character.isDigit(key)) {
                    seedBuilder.append(key);
                    needsRedraw = true;
                } else if (lower == 'b') {
                    return null;
                } else if (lower == 's') {
                    if (seedBuilder.isEmpty()) {
                        helperText = "Seed cannot be empty.";
                        needsRedraw = true;
                    } else {
                        try {
                            return Long.parseLong(seedBuilder.toString());
                        } catch (NumberFormatException e) {
                            helperText = "Seed is too large for a Java long.";
                            needsRedraw = true;
                        }
                    }
                } else if (key == '\b' || key == 127) {
                    if (!seedBuilder.isEmpty()) {
                        seedBuilder.deleteCharAt(seedBuilder.length() - 1);
                        needsRedraw = true;
                    }
                }
            }

            StdDraw.pause(16);
        }
    }

    private void drawSeedScreen(String seedText, String helperText) {
        StdDraw.clear(new Color(8, 11, 18));
        drawMenuBackdrop();

        StdDraw.setPenColor(new Color(241, 196, 15));
        StdDraw.setFont(new Font("Georgia", Font.BOLD, 24));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 3.0, "Start A New World");

        StdDraw.setPenColor(new Color(226, 232, 240));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 15));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 4.0, "Enter a numeric seed to reproduce the same map later.");

        StdDraw.setPenColor(new Color(18, 30, 46));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 + 1.5, 15.5, 4.0);

        StdDraw.setPenColor(new Color(245, 247, 250));
        StdDraw.setFont(new Font("Consolas", Font.BOLD, 22));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 + 1.5,
                seedText.isEmpty() ? "_" : seedText + "_");

        StdDraw.setPenColor(new Color(161, 178, 195));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 14));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 - 6.0, helperText);
        StdDraw.show();
    }

    private void drawLoadScreen(List<SaveEntry> saves, int selected) {
        StdDraw.clear(new Color(8, 11, 18));
        drawMenuBackdrop();

        StdDraw.setPenColor(new Color(241, 196, 15));
        StdDraw.setFont(new Font("Georgia", Font.BOLD, 24));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 3.0, "Load Game");

        StdDraw.setPenColor(new Color(226, 232, 240));
        StdDraw.setFont(new Font("Arial", Font.PLAIN, 14));
        StdDraw.text(WORLD_WIDTH / 2.0, WORLD_HEIGHT - 6.0, "Use W/S to move, E to open, D to delete, B to go back.");

        StdDraw.setPenColor(new Color(18, 30, 46));
        StdDraw.filledRectangle(WORLD_WIDTH / 2.0, WORLD_HEIGHT / 2.0 - 2.0, 18.5, 14.0);

        int startIndex = Math.max(0, selected - MAX_VISIBLE_SAVES + 1);
        startIndex = Math.min(startIndex, Math.max(0, saves.size() - MAX_VISIBLE_SAVES));
        int endIndex = Math.min(saves.size(), startIndex + MAX_VISIBLE_SAVES);

        double y = WORLD_HEIGHT - 12.0;
        for (int i = startIndex; i < endIndex; i += 1) {
            SaveEntry save = saves.get(i);
            boolean isSelected = i == selected;

            if (isSelected) {
                StdDraw.setPenColor(new Color(64, 112, 170));
                StdDraw.filledRectangle(WORLD_WIDTH / 2.0, y - 0.8, 16.0, 1.95);
                StdDraw.setPenColor(new Color(241, 196, 15));
                StdDraw.setPenRadius(0.0035);
                StdDraw.rectangle(WORLD_WIDTH / 2.0, y - 0.8, 16.0, 1.95);
                StdDraw.setPenRadius();
            }

            StdDraw.setPenColor(isSelected ? new Color(245, 247, 250) : new Color(198, 210, 223));
            StdDraw.setFont(new Font("Arial", isSelected ? Font.BOLD : Font.PLAIN, 14));
            StdDraw.textLeft(9.2, y, save.title);

            StdDraw.setPenColor(isSelected ? new Color(214, 224, 236) : new Color(138, 156, 173));
            StdDraw.setFont(new Font("Arial", Font.PLAIN, 12));
            StdDraw.textLeft(9.2, y - 1.4, save.subtitle);

            StdDraw.setPenColor(isSelected ? new Color(241, 196, 15) : new Color(173, 186, 199));
            StdDraw.setFont(new Font("Arial", Font.PLAIN, 11));
            StdDraw.textRight(40.5, y, save.savedAt);

            y -= 4.0;
        }

        StdDraw.show();
    }

    private void showMenuNotice(String message) {
        showMainMenu();
        StdDraw.setPenColor(new Color(231, 76, 60));
        StdDraw.setFont(new Font("Arial", Font.BOLD, 14));
        StdDraw.text(WORLD_WIDTH / 2.0, 7.0, message);
        StdDraw.show();
        StdDraw.pause(1400);
    }

    private void loadMostRecentSave() {
        List<SaveEntry> saves = getSaveEntries();
        if (saves.isEmpty()) {
            showMenuNotice("No saves yet. Start a run and use :Q to create one.");
            return;
        }

        SaveData data = readSaveData(saves.get(0));
        selectedDifficulty = data.difficulty;
        showWorld(buildWorldFromSave(data));
    }

    private void saveWorld(World world) {
        ensureSaveDirectory();

        String filename = "save-" + world.getSeed() + ".txt";
        StringBuilder coins = new StringBuilder();
        List<World.Position> coinPositions = world.getCoinPositions();
        for (int i = 0; i < coinPositions.size(); i += 1) {
            World.Position coin = coinPositions.get(i);
            if (i > 0) {
                coins.append("|");
            }
            coins.append(coin.getX()).append(":").append(coin.getY());
        }

        String exit = "";
        if (world.getExitPosition() != null) {
            exit = world.getExitPosition().getX() + ":" + world.getExitPosition().getY();
        }

        StringBuilder monsters = new StringBuilder();
        List<World.MonsterSnapshot> monsterSnapshots = world.getMonsterSnapshots();
        for (int i = 0; i < monsterSnapshots.size(); i += 1) {
            World.MonsterSnapshot monster = monsterSnapshots.get(i);
            if (i > 0) {
                monsters.append("|");
            }
            monsters.append(monster.getX()).append(":")
                    .append(monster.getY()).append(":")
                    .append(monster.isActive()).append(":")
                    .append(monster.getFrozenUntilMoveCount());
        }

        long elapsedMillis = elapsedMillisForMoveCount(world.getMoveCount());
        String contents = world.getSeed()
                + ";" + world.getAvatarX()
                + ";" + world.getAvatarY()
                + ";" + world.getScore()
                + ";" + world.getHp()
                + ";" + world.getMoveCount()
                + ";" + elapsedMillis
                + ";" + world.getDifficulty().name()
                + ";" + monsters
                + ";" + exit
                + ";" + coins
                + ";" + world.getRandomState()
                + ";" + world.isLineOfSightEnabled();
        File saveDir = resolveSaveDirectory();
        FileUtils.writeFile(new File(saveDir, filename).getPath(), contents);
    }

    private List<SaveEntry> getSaveEntries() {
        ensureSaveDirectory();
        List<SaveEntry> saves = new ArrayList<>();
        File saveDir = resolveSaveDirectory();

        File[] files = saveDir.listFiles((dir, name) ->
                name.startsWith("save-") && name.endsWith(".txt"));
        if (files == null) {
            return saves;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File file : files) {
            try {
                String content = FileUtils.readFile(file.getPath()).trim();
                SaveData data = parseSaveData(content);
                saves.add(new SaveEntry(
                        data.seed,
                        "Seed " + data.seed,
                        "Difficulty " + data.difficulty.getDisplayName()
                                + "  Score " + data.score
                                + "  HP " + data.hp
                                + "  Time " + formatElapsedTime(data.elapsedMillis),
                        formatTimestamp(file.lastModified()),
                        file
                ));
            } catch (RuntimeException ignored) {
                // Ignore malformed save files so one bad file does not break the menu.
            }
        }

        return saves;
    }

    private void ensureSaveDirectory() {
        File saveDir = resolveSaveDirectory();
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
    }

    private File resolveSaveDirectory() {
        File[] candidates = {
                new File(PROJECT_SAVE_DIR),
                new File(SOURCE_ROOT_SAVE_DIR),
                new File(LEGACY_LOCAL_SAVE_DIR)
        };

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate;
            }
        }

        return new File(PROJECT_SAVE_DIR);
    }

    private void deleteSave(SaveEntry save) {
        if (save.file.exists()) {
            save.file.delete();
        }
    }

    private String formatTimestamp(long lastModified) {
        return SAVE_TIME_FORMAT.format(new Date(lastModified));
    }

    private boolean confirmDeleteSave(SaveEntry save) {
        int choice = JOptionPane.showConfirmDialog(
                null,
                "Delete this save?\n" + save.title,
                "Delete Save",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
    }

    private boolean confirmSaveBeforeLeaving(World world, long elapsedMillis, String action) {
        int choice = JOptionPane.showConfirmDialog(
                null,
                "Do you want to save the current seed before you " + action + "?\nSeed: " + world.getSeed(),
                "Save Before Leaving",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
            return false;
        }

        if (choice == JOptionPane.YES_OPTION) {
            saveWorld(world);
        }

        return true;
    }

    private SaveData readSaveData(SaveEntry save) {
        String content = FileUtils.readFile(save.file.getPath()).trim();
        return parseSaveData(content);
    }

    private World buildWorldFromSave(SaveData data) {
        World world = new World(data.seed, WORLD_WIDTH, WORLD_HEIGHT, data.difficulty);
        world.restoreState(
                data.avatarX,
                        data.avatarY,
                        data.score,
                        data.hp,
                        data.moveCount,
                        data.remainingCoins,
                        data.exitPosition,
                        data.monsters
                );
        world.setRandomState(data.randomState);
        world.setLineOfSightEnabled(data.lineOfSightEnabled);
        return world;
    }

    private SaveData parseSaveData(String content) {
        if (!content.contains(";")) {
            String[] legacyParts = content.split(",");
            long legacySeed = Long.parseLong(legacyParts[0].trim());
            World previewWorld = new World(legacySeed, WORLD_WIDTH, WORLD_HEIGHT);
            int avatarX = previewWorld.getAvatarX();
            int avatarY = previewWorld.getAvatarY();

            if (legacyParts.length >= 3) {
                avatarX = Integer.parseInt(legacyParts[1].trim());
                avatarY = Integer.parseInt(legacyParts[2].trim());
            }

            return new SaveData(legacySeed, avatarX, avatarY, 0, 5, 0, 0L,
                    previewWorld.getDifficulty(), previewWorld.getMonsterSnapshots(),
                    previewWorld.getExitPosition(), previewWorld.getCoinPositions(),
                    previewWorld.getRandomState(), previewWorld.isLineOfSightEnabled());
        }

        String[] parts = content.split(";", -1);
        long seed = Long.parseLong(parts[0].trim());
        Difficulty difficulty = Difficulty.NORMAL;
        if (parts.length >= 8 && !parts[7].isEmpty()) {
            difficulty = Difficulty.fromString(parts[7].trim());
        }

        World previewWorld = new World(seed, WORLD_WIDTH, WORLD_HEIGHT, difficulty);
        int avatarX = previewWorld.getAvatarX();
        int avatarY = previewWorld.getAvatarY();
        int score = 0;
        int hp = 5;
        int moveCount = 0;
        long elapsedMillis = 0L;
        List<World.MonsterSnapshot> monsters = previewWorld.getMonsterSnapshots();
        World.Position exitPosition = previewWorld.getExitPosition();
        List<World.Position> remainingCoins = previewWorld.getCoinPositions();
        long randomState = previewWorld.getRandomState();
        boolean lineOfSightEnabled = previewWorld.isLineOfSightEnabled();

        if (parts.length >= 3) {
            avatarX = Integer.parseInt(parts[1].trim());
            avatarY = Integer.parseInt(parts[2].trim());
        }
        if (parts.length >= 4 && !parts[3].isEmpty()) {
            score = Integer.parseInt(parts[3].trim());
        }
        if (parts.length >= 5 && !parts[4].isEmpty()) {
            hp = Integer.parseInt(parts[4].trim());
        }
        if (parts.length >= 6 && !parts[5].isEmpty()) {
            moveCount = Integer.parseInt(parts[5].trim());
        }
        if (parts.length >= 7 && !parts[6].isEmpty()) {
            elapsedMillis = Long.parseLong(parts[6].trim());
        }
        boolean legacyMonsterSave = parts.length >= 11
                && isLegacyMonsterFormat(parts[8], parts[9], parts[10]);
        if (legacyMonsterSave) {
            monsters = parseLegacyMonster(parts[8], parts[9], parts[10]);
            if (parts.length >= 12 && !parts[11].isBlank()) {
                exitPosition = parsePosition(parts[11]);
            } else {
                exitPosition = null;
            }
            if (parts.length >= 13) {
                remainingCoins = parseCoinPositions(parts[12]);
            } else if (parts.length >= 7) {
                remainingCoins = parseCoinPositions(parts[6]);
            }
            if (parts.length >= 14 && !parts[13].isBlank()) {
                randomState = Long.parseLong(parts[13].trim());
            }
            if (parts.length >= 15 && !parts[14].isBlank()) {
                lineOfSightEnabled = Boolean.parseBoolean(parts[14].trim());
            }
        } else {
            if (parts.length >= 9) {
                monsters = parseMonsterSnapshots(parts[8]);
            }
            if (parts.length >= 10 && !parts[9].isBlank()) {
                exitPosition = parsePosition(parts[9]);
            } else {
                exitPosition = null;
            }
            if (parts.length >= 11) {
                remainingCoins = parseCoinPositions(parts[10]);
            } else if (parts.length >= 7) {
                remainingCoins = parseCoinPositions(parts[6]);
            }
            if (parts.length >= 12 && !parts[11].isBlank()) {
                randomState = Long.parseLong(parts[11].trim());
            }
            if (parts.length >= 13 && !parts[12].isBlank()) {
                lineOfSightEnabled = Boolean.parseBoolean(parts[12].trim());
            }
        }

        return new SaveData(seed, avatarX, avatarY, score, hp, moveCount, elapsedMillis,
                difficulty, monsters, exitPosition, remainingCoins, randomState, lineOfSightEnabled);
    }

    private boolean isLegacyMonsterFormat(String xPart, String yPart, String chasingPart) {
        try {
            Integer.parseInt(xPart.trim());
            Integer.parseInt(yPart.trim());
            String normalized = chasingPart.trim().toLowerCase();
            return normalized.equals("true") || normalized.equals("false");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private List<World.MonsterSnapshot> parseLegacyMonster(String xPart, String yPart, String chasingPart) {
        List<World.MonsterSnapshot> monsters = new ArrayList<>();
        monsters.add(new World.MonsterSnapshot(
                Integer.parseInt(xPart.trim()),
                Integer.parseInt(yPart.trim()),
                Boolean.parseBoolean(chasingPart.trim())
        ));
        return monsters;
    }

    private List<World.MonsterSnapshot> parseMonsterSnapshots(String encodedMonsters) {
        List<World.MonsterSnapshot> monsters = new ArrayList<>();
        if (encodedMonsters == null || encodedMonsters.isBlank()) {
            return monsters;
        }

        String[] entries = encodedMonsters.split("\\|");
        for (String entry : entries) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                monsters.add(new World.MonsterSnapshot(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Boolean.parseBoolean(parts[2].trim())
                ));
            } else if (parts.length >= 4) {
                monsters.add(new World.MonsterSnapshot(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Boolean.parseBoolean(parts[2].trim()),
                        Integer.parseInt(parts[3].trim())
                ));
            }
        }
        return monsters;
    }

    private List<World.Position> parseCoinPositions(String encodedCoins) {
        List<World.Position> coins = new ArrayList<>();
        if (encodedCoins == null || encodedCoins.isBlank()) {
            return coins;
        }

        String[] entries = encodedCoins.split("\\|");
        for (String entry : entries) {
            if (entry.isBlank()) {
                continue;
            }
            String[] coordinates = entry.split(":");
            if (coordinates.length == 2) {
                coins.add(new World.Position(
                        Integer.parseInt(coordinates[0].trim()),
                        Integer.parseInt(coordinates[1].trim())
                ));
            }
        }
        return coins;
    }

    private World.Position parsePosition(String encodedPosition) {
        if (encodedPosition == null || encodedPosition.isBlank()) {
            return null;
        }
        String[] coordinates = encodedPosition.split(":");
        if (coordinates.length != 2) {
            return null;
        }
        return new World.Position(
                Integer.parseInt(coordinates[0].trim()),
                Integer.parseInt(coordinates[1].trim())
        );
    }

    private String formatElapsedTime(long elapsedMillis) {
        long totalSeconds = Math.max(0, elapsedMillis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private long elapsedMillisForMoveCount(int moveCount) {
        return Math.max(0L, moveCount) * MILLIS_PER_MOVE;
    }

    private int currentCoinValue(int moveCount) {
        long elapsedMillis = elapsedMillisForMoveCount(moveCount);
        long decaySteps = Math.max(0L, elapsedMillis) / COIN_DECAY_INTERVAL_MILLIS;
        return Math.max(0, BASE_COIN_SCORE - (int) decaySteps);
    }

    private int mouseTileX() {
        return (int) StdDraw.mouseX();
    }

    private int mouseTileY() {
        return (int) StdDraw.mouseY();
    }

    private enum EndScreenAction {
        NEW_GAME,
        MAIN_MENU,
        QUIT
    }

    private static class SaveEntry {
        private final long seed;
        private final String title;
        private final String subtitle;
        private final String savedAt;
        private final File file;

        SaveEntry(long seed, String title, String subtitle, String savedAt, File file) {
            this.seed = seed;
            this.title = title;
            this.subtitle = subtitle;
            this.savedAt = savedAt;
            this.file = file;
        }
    }

    private static class SaveData {
        private final long seed;
        private final int avatarX;
        private final int avatarY;
        private final int score;
        private final int hp;
        private final int moveCount;
        private final long elapsedMillis;
        private final Difficulty difficulty;
        private final List<World.MonsterSnapshot> monsters;
        private final World.Position exitPosition;
        private final List<World.Position> remainingCoins;
        private final long randomState;
        private final boolean lineOfSightEnabled;

        SaveData(long seed, int avatarX, int avatarY, int score, int hp, int moveCount,
                 long elapsedMillis, Difficulty difficulty, List<World.MonsterSnapshot> monsters,
                 World.Position exitPosition, List<World.Position> remainingCoins,
                 long randomState, boolean lineOfSightEnabled) {
            this.seed = seed;
            this.avatarX = avatarX;
            this.avatarY = avatarY;
            this.score = score;
            this.hp = hp;
            this.moveCount = moveCount;
            this.elapsedMillis = elapsedMillis;
            this.difficulty = difficulty;
            this.monsters = monsters;
            this.exitPosition = exitPosition;
            this.remainingCoins = remainingCoins;
            this.randomState = randomState;
            this.lineOfSightEnabled = lineOfSightEnabled;
        }
    }
}
