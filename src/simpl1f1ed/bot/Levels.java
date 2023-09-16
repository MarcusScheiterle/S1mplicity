package simpl1f1ed.bot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class Levels {
    private static Map<Integer, String> rankNames = new Hashtable<>();

    // Define leveling parameters
    private static int startingLevel = 1;
    private static int startingPoints = 5;
    private static double base = 1.025; // Adjust this value for scaling
    private static int maxLevel = 100; // Set an upper limit for levels

    static {
        rankNames.put(0, "Citizen");
        rankNames.put(10, "Beginner");
        rankNames.put(20, "Novice");
        rankNames.put(30, "Apprentice");
        rankNames.put(40, "Journeyman");
        rankNames.put(50, "Expert");
        rankNames.put(60, "Legend");
        rankNames.put(70, "Master");
        rankNames.put(80, "Grandmaster");
        rankNames.put(90, "Champion");
        rankNames.put(100, "High Champion");
    }

    public static String getLevelName(int currentLevel) {
        String levelName = "Unknown";

        List<Integer> sortedKeys = new ArrayList<>(rankNames.keySet());
        Collections.sort(sortedKeys);

        for (Integer key : sortedKeys) {
            if (currentLevel >= key) {
                levelName = rankNames.get(key);
            } else {
                break;
            }
        }
        return levelName;
    }

    public static Map<Integer, String> getLevelNamesMap() {
        return rankNames;
    }

    // Calculate the level based on the points
    public static int calculateLevel(int points) {
        Map<Integer, Integer> levelPointsMap = calculateLevelPointsMap();
        int level = startingLevel;

        for (int i = startingLevel; i <= maxLevel; i++) {
            if (points >= levelPointsMap.get(i)) {
                level = i;
            } else {
                break;
            }
        }

        return level;
    }

    // Calculate the thresholds for each level
    public static Map<Integer, Integer> calculateLevelPointsMap() {
        Map<Integer, Integer> levelPointsMap = new HashMap<>();
        int currentPoints = 0;

        for (int i = startingLevel; i <= maxLevel; i++) {
            currentPoints += (int) (startingPoints * Math.pow(base, i - 1));
            levelPointsMap.put(i, currentPoints);
        }

        return levelPointsMap;
    }
}
