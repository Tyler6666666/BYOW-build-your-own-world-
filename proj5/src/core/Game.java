package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import java.awt.*;

public class Game {

    // TODO: Initialize game
    public Game() {}

    // Game loop
    public void run() {
        // Setup World dimension
        int WORLD_HEIGHT = 50;
        int WORLD_WIDTH = 50;

        // Initialize the Renderer
        TERenderer ter = new TERenderer();
        ter.initialize(WORLD_HEIGHT, WORLD_WIDTH);

        // TODO: REPL for menu and game update
        while (true) {
            // TODO: Menu page, including
            //  (1) new game,
            //  (2) load game,
            //  (3) quit, with input/output read

            // TODO: How to read keyboard input?

            // TODO: If 1/2, Initialize world
            World demoWorld = new World();
            World determinedWorld = new World(1919810L, WORLD_WIDTH,  WORLD_HEIGHT);
            StdDraw.clear(new Color(0, 0, 0));

            // TODO: Get into the game. Write this in World.java
            ter.drawTiles(demoWorld.getTiles());
            StdDraw.show();

            break; // FIXME: Remove this line after you finish repl break condition
        }

    }
    // TODO: initialize world / menu / renderer
    private void setup() {

    }

    // TODO: execute user input, update the world,
    //  including tiles, user/character/map status,
    //  HUD, and other stuff
    private void handleKey(char key) {

    }

    // TODO: draw the world to the canvas
    private void render() {

    }
}
