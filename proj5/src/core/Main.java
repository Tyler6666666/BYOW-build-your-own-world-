package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;
import tileengine.TETile;

import java.awt.*;

public class Main {
    private static final int WIDTH = 50;
    private static final int HEIGHT = 50;

    public static void main(String[] args) {

        // Initialzie the Renderer
        TERenderer ter = new TERenderer();
        ter.initialize(WIDTH, HEIGHT);

        // TODO: REPL for menu and game update
        while (true) {
            // TODO: Menu page, including
            //  (1) new game,
            //  (2) load game,
            //  (3) quit, with input/output read

            // TODO: How to read keyboard input?

            // TODO: Initialize world
            WorldGenerator g = new WorldGenerator(42);
            World demoWorld = g.generate();
            World determinedWorld = g.generate(1919810);
            StdDraw.clear(new Color(0, 0, 0));

            // TODO: Draw world
            ter.drawTiles(demoWorld.getTiles());
            StdDraw.show();

            // TODO: Constantly update world

            // TODO: detect quit

            break; // FIXME: Remove this line after you finish repl break condition
        }
    }
}
