package io.snoopy.engine.detection.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates Shannon entropy H(X) = -sum(p(x) * log2(p(x))) for candidate strings (spec §18.3).
 */
public final class EntropyCalculator {

    private EntropyCalculator() {}

    public static double calculateEntropy(String input) {
        if (input == null || input.isEmpty()) {
            return 0.0;
        }
        Map<Character, Integer> counts = new HashMap<>();
        for (char c : input.toCharArray()) {
            counts.put(c, counts.getOrDefault(c, 0) + 1);
        }
        double length = input.length();
        double entropy = 0.0;
        for (int count : counts.values()) {
            double p = count / length;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }
}
