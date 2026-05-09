package core;

public enum Difficulty {
    EASY("Easy", 1.35, 1.15, 1.0, 0.95, 7, 1, 1),
    NORMAL("Normal", 2.6, 1.55, 1.0, 0.75, 6, 1, 2),
    HARD("Hard", 5.2, 1.8, 0.85, 0.28, 5, 1, 3),
    NIGHTMARE("Nightmare", 7.4, 1.95, 0.7, 0.16, 4, 2, 4);

    private final String displayName;
    private final double optimalWeight;
    private final double closerWeight;
    private final double sideWeight;
    private final double awayWeight;
    private final int echoRange;
    private final int minMonstersPerRoom;
    private final int maxMonstersPerRoom;

    Difficulty(String displayName, double optimalWeight, double closerWeight,
               double sideWeight, double awayWeight, int echoRange,
               int minMonstersPerRoom, int maxMonstersPerRoom) {
        this.displayName = displayName;
        this.optimalWeight = optimalWeight;
        this.closerWeight = closerWeight;
        this.sideWeight = sideWeight;
        this.awayWeight = awayWeight;
        this.echoRange = echoRange;
        this.minMonstersPerRoom = minMonstersPerRoom;
        this.maxMonstersPerRoom = maxMonstersPerRoom;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getOptimalWeight() {
        return optimalWeight;
    }

    public double getCloserWeight() {
        return closerWeight;
    }

    public double getSideWeight() {
        return sideWeight;
    }

    public double getAwayWeight() {
        return awayWeight;
    }

    public int getEchoRange() {
        return echoRange;
    }

    public int getMinMonstersPerRoom() {
        return minMonstersPerRoom;
    }

    public int getMaxMonstersPerRoom() {
        return maxMonstersPerRoom;
    }

    public Difficulty next() {
        Difficulty[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static Difficulty fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        for (Difficulty difficulty : values()) {
            if (difficulty.name().equalsIgnoreCase(raw)
                    || difficulty.displayName.equalsIgnoreCase(raw)) {
                return difficulty;
            }
        }
        return NORMAL;
    }
}
