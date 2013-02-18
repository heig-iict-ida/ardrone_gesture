package madsdf.ardrone.utils;

import java.util.Arrays;
import java.util.Map;

public class MathUtils {
    public static float median(float[] array) {
        return median(array, false);
    }
    // if canModifyArray is true, the median function will destroy the array
    // given as parameter
    public static float median(float[] array, boolean canModifyArray) {
        float[] sorted;
        if (canModifyArray) {
            sorted = array;
        } else {
            sorted = new float[array.length];
            for (int i = 0; i < array.length; ++i) {
                sorted[i] = array[i];
            }
        }
        Arrays.sort(sorted);
        final int n = sorted.length;
        if (n % 2 == 0) {
            return (sorted[n/2] + sorted[(n/2) - 1]) / 2f;
        } else {
            return sorted[(n-1)/2];
        }
    }
    
    public static float[][] medianFilter(float[][] serie, int winsize) {
        float[][] out = new float[serie.length][];
        for (int i = 0; i < serie.length; ++i) {
            out[i] = medianFilter(serie[i], winsize);
        }
        return out;
    }
    
    public static float[] medianFilter(float[] serie, int winsize) {
        float[] out = new float[serie.length];
        for (int i = 0; i < serie.length; ++i) {
            final int to = Math.min(i + winsize, serie.length);
            out[i] = median(Arrays.copyOfRange(serie, i, to), true);
        }
        return out;
    }

    // Increment the value of 'key' in 'map' by 'incr'. Create new entry
    // if needed
    public static <K> void mapIncr(Map<K, Float> m, K key, float incr) {
        Float v = m.get(key);
        if (v == null) {
            m.put(key, incr);
        } else {
            m.put(key, v + incr);
        }
    }
}
