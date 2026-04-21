package core;

public class Room {
    // (x,y) are the coordinates for bottom left corner of the room.
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private int id;

    public Room(int x, int y, int width, int height, int id) {
        this(x, y, width, height);
        this.id = id;
    }

    public Room(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }



    public boolean intersect(Room other, int gap) {
        return this.x - gap < other.x + other.width
                && this.x + this.width > other.x - gap
                && this.y - gap < other.y + other.height
                && this.y + this.height > other.y - gap;
    }

    public boolean intersect(Room other) {
        return intersect(other, 2);
    }

    public int l1DistanceTo(Room other) {
        int thisLeft = this.x;
        int thisRight = this.x + this.width - 1;
        int thisBottom = this.y;
        int thisTop = this.y + this.height - 1;

        int otherLeft = other.x;
        int otherRight = other.x + other.width - 1;
        int otherBottom = other.y;
        int otherTop = other.y + other.height - 1;

        int xGap = 0;
        if (thisRight < otherLeft) {
            xGap = otherLeft - thisRight;
        } else if (otherRight < thisLeft) {
            xGap = thisLeft - otherRight;
        }

        int yGap = 0;
        if (thisTop < otherBottom) {
            yGap = otherBottom - thisTop;
        } else if (otherTop < thisBottom) {
            yGap = thisBottom - otherTop;
        }

        return xGap + yGap;
    }

    public int getX() {return x;}

    public int getY() {
        return y;
    }

    public int getWidth() {return width;}

    public int getHeight() {
        return height;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


}
