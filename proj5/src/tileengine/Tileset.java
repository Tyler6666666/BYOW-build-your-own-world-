package tileengine;

import java.awt.Color;

/**
 * Contains constant tile objects, to avoid having to remake the same tiles in different parts of
 * the code.
 *
 * The character set here is intentionally plain ASCII so the starter StdDraw renderer does not
 * produce font-width artifacts on different machines.
 */
public class Tileset {
    public static final TETile AVATAR = new TETile('@', Color.white, Color.black, "you", 0);
    public static final TETile WALL = new TETile('#', new Color(216, 128, 128), Color.darkGray,
            "wall", 1);
    public static final TETile FLOOR = new TETile('.', new Color(128, 192, 128), Color.black,
            "floor", 2);
    public static final TETile NOTHING = new TETile(' ', Color.black, Color.black, "nothing", 3);
    public static final TETile GRASS = new TETile('"', Color.green, Color.black, "grass", 4);
    public static final TETile WATER = new TETile('~', Color.blue, Color.black, "water", 5);
    public static final TETile FLOWER = new TETile('*', Color.magenta, Color.pink, "flower", 6);
    public static final TETile LOCKED_DOOR = new TETile('+', Color.orange, Color.black,
            "locked door", 7);
    public static final TETile UNLOCKED_DOOR = new TETile('/', Color.orange, Color.black,
            "unlocked door", 8);
    public static final TETile SAND = new TETile(':', Color.yellow, Color.black, "sand", 9);
    public static final TETile MOUNTAIN = new TETile('^', Color.gray, Color.black, "mountain", 10);
    public static final TETile TREE = new TETile('T', Color.green, Color.black, "tree", 11);
    public static final TETile CELL = new TETile('O', Color.white, Color.black, "cell", 12);
    public static final TETile COIN = new TETile('$', new Color(241, 196, 15), Color.black,
            "coin", 13);
    public static final TETile EXIT = new TETile('E', new Color(93, 214, 122), Color.black,
            "exit", 14);
    public static final TETile BOGEYMAN = new TETile('B', new Color(231, 76, 60), Color.black,
            "bogeyman", 15);
}
