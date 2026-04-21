package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;

import java.awt.*;
import java.util.Random;

public class WorldTest {

    public static void main(String[] args) {
//        boostrap();
        save5BDeliverables();
    }

    public static void save5BDeliverables() {
        long[] seeds = new long[5];

        Random r = new Random(56);
        for (int i = 0; i < 5; i += 1) {
            seeds[i] = (Math.abs(r.nextLong()));
        }

        int WORLD_WIDTH = 50;
        int WORLD_HEIGHT = 50;

        TERenderer ter = new TERenderer();
        ter.initialize(WORLD_WIDTH, WORLD_HEIGHT);

        for (int i = 0; i < seeds.length; i++) {
            World world = new World(seeds[i], WORLD_WIDTH, WORLD_HEIGHT);
            StdDraw.clear(new Color(0, 0, 0));
            ter.drawTiles(world.getTiles());
            StdDraw.show();
            StdDraw.save(String.format("proj5/5B-deliverables/world%d.png", i+1));
        }
    }

    public static void boostrap() {
        int width = 50;
        int height = 50;
        int numWorlds = 500;
        int numBootstrapSamples = 2000;
        double targetDensity = 0.40;
        double tolerance = 0.03;

        double[] densities = new double[numWorlds];
        Random seedGenerator = new Random(2026);

        for (int i = 0; i < numWorlds; i++) {
            long seed = seedGenerator.nextLong();
            World world = new World(seed, width, height);
            world.generateRooms();
            world.connectRooms();
            world.generateWalls();

            densities[i] = world.countFloors() / (double) (width * height);
        }

        double mean = mean(densities);
        double min = min(densities);
        double max = max(densities);
        double std = stddev(densities, mean);

        int withinTarget = 0;
        for (double d : densities) {
            if (Math.abs(d - targetDensity) <= tolerance) {
                withinTarget++;
            }
        }

        double[] bootstrapMeans = bootstrapMeans(densities, numBootstrapSamples, 12345L);
        sort(bootstrapMeans);

        double ciLow = percentileSorted(bootstrapMeans, 0.025);
        double ciHigh = percentileSorted(bootstrapMeans, 0.975);

        System.out.println("Number of worlds: " + numWorlds);
        System.out.println("Target density: " + targetDensity);
        System.out.println("Mean density: " + mean);
        System.out.println("Std dev: " + std);
        System.out.println("Min density: " + min);
        System.out.println("Max density: " + max);
        System.out.println("Worlds within ±" + tolerance + " of target: "
                + withinTarget + " / " + numWorlds);
        System.out.println("Bootstrap 95% CI for mean density: ["
                + ciLow + ", " + ciHigh + "]");
    }

    private static double[] bootstrapMeans(double[] data, int numSamples, long seed) {
        Random rand = new Random(seed);
        double[] means = new double[numSamples];
        int n = data.length;

        for (int i = 0; i < numSamples; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                int idx = rand.nextInt(n);
                sum += data[idx];
            }
            means[i] = sum / n;
        }

        return means;
    }

    private static double mean(double[] arr) {
        double sum = 0.0;
        for (double x : arr) {
            sum += x;
        }
        return sum / arr.length;
    }

    private static double stddev(double[] arr, double mean) {
        double sumSq = 0.0;
        for (double x : arr) {
            double diff = x - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / arr.length);
    }

    private static double min(double[] arr) {
        double m = arr[0];
        for (double x : arr) {
            if (x < m) {
                m = x;
            }
        }
        return m;
    }

    private static double max(double[] arr) {
        double m = arr[0];
        for (double x : arr) {
            if (x > m) {
                m = x;
            }
        }
        return m;
    }

    private static void sort(double[] arr) {
        java.util.Arrays.sort(arr);
    }

    private static double percentileSorted(double[] sortedArr, double p) {
        double index = p * (sortedArr.length - 1);
        int lo = (int) Math.floor(index);
        int hi = (int) Math.ceil(index);

        if (lo == hi) {
            return sortedArr[lo];
        }

        double weight = index - lo;
        return sortedArr[lo] * (1.0 - weight) + sortedArr[hi] * weight;
    }
}