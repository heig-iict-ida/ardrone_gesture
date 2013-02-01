/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.gesture;
import static com.google.common.base.Preconditions.*;
import java.util.Arrays;

/**
 * Dynamic time warping implementation
 */
public class DTW {
    private static float min3(float a, float b, float c) {
        return Math.min(Math.min(a, b), c);
    }
    
    // DTW Distance between 2 series
    public static float dtwDistance(float[] serie1, float[] serie2) {
        final int N = serie1.length;
        final int M = serie2.length;
        final float D[][] = new float[N][M];
        // Instead of initializing the first line and row to infinity and having
        // a (N+1) * (M+1) matrix, we "merge" the two first lines and therefore
        // directly initialize to the distance.
        // That's inspired by mlpy's implementation (and a bit different from
        // Wikipedia's implementation which uses 1-based indexing for serie and
        // 0-based indexing for cost matrix
        D[0][0] = Math.abs(serie1[0] - serie2[0]);
        for (int i = 1 ; i < N; ++i) {
            D[i][0] = D[i-1][0] + Math.abs(serie1[i] - serie2[0]);
        }
        for (int i = 1; i < M; ++i) {
            D[0][i] = D[0][i-1] + Math.abs(serie1[0] - serie2[i]);
        }
        
        for (int i = 1; i < N; ++i) {
            for (int j = 1; j < M; ++j) {
                final float cost = Math.abs(serie1[i] - serie2[j]);
                D[i][j] = cost + min3(D[i-1][j], // insertion
                                      D[i][j-1], // deletion
                                      D[i-1][j-1]); // match
            }
        }
        //System.out.println(Arrays.deepToString(D));
        return D[N-1][M-1];
    }
    
    // sum of DTW distance between corresponding axis of two series
    // data is NxM where N is the number of axis and M the number of values
    public static float allAxisDTW(float[][] serie1, float[][] serie2) {
        // TODO: This is wrong, need to do DTW on all 3 axis at the same time
        // (we should use the same path on all axis)
        checkState(serie1.length == serie2.length);
        float sum = 0;
        for (int i = 0; i < serie1.length; ++i) {
            sum += dtwDistance(serie1[i], serie2[i]);
        }
        return sum;
    }
    
    public static float euclideanDist(float[] v1, float[] v2) {
        checkState(v1.length == v2.length);
        float sum = 0;
        for (int i = 0; i < v1.length; ++i) {
            sum += (v1[i] - v2[i]) * (v1[i] - v2[i]);
        }
        return (float) Math.sqrt(sum);
    }
    
    public static float allAxisEuclidean(float[][] serie1, float[][] serie2) {
        checkState(serie1.length == serie2.length);
        float sum = 0;
        for (int i = 0; i < serie1.length; ++i) {
            sum += euclideanDist(serie1[i], serie2[i]);
        }
        return sum;
    }
}
