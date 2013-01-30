/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.gesture;

/**
 * Dynamic time warping implementation
 */
public class DTW {
    private static float min3(float a, float b, float c) {
        return Math.min(Math.min(a, b), c);
    }
    
    public static float dtwDistance(float[] serie1, float[] serie2) {
        final int N = serie1.length;
        final int M = serie2.length;
        final float D[][] = new float[N][M];
        for (int i = 0 ; i < N; ++i) {
            D[i][0] = Float.MAX_VALUE;
        }
        for (int i = 0; i < M; ++i) {
            D[0][i] = Float.MAX_VALUE;
            
        }
        D[0][0] = 0;
        
        for (int i = 1; i < N; ++i) {
            for (int j = 1; j < M; ++j) {
                final float cost = Math.abs(serie1[i] - serie2[j]);
                D[i][j] = cost + min3(D[i-1][j], // insertion
                                      D[i][j-1], // deletion
                                      D[i-1][j-1]); // match
            }
        }
        return D[N-1][M-1];
    }
}
