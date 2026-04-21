package core;

import java.util.Random;

public class WorldGenerator {
    Random random;

    public WorldGenerator(long seed) {
        random = new Random(seed);
    }

    public WorldGenerator() {
        random = new Random();
    }

    // TODO: Complete world generation
    public World generate(long seed){
        return new World(seed, 50, 50);
    }

    // TODO: Complete world generation
    public World generate(){
        return new World(random.nextLong(), 50, 50);
    }

    // TODO: Randomly place rooms on the map
    public

}
