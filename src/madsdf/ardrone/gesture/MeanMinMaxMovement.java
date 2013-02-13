package madsdf.ardrone.gesture;

import com.google.common.eventbus.EventBus;
import java.util.ListIterator;
import madsdf.shimmer.gui.AccelGyro;

/**
 * Movement model implementing the abstract MovementModel and implementing the
 * processFeatures methods with only three features. This movement model is a
 * specific implementation for the networks which need only the mean, min and
 * max features.
 *
 * Java version : JDK 1.6.0_21 IDE : Netbeans 7.1.1
 *
 * @author Gregoire Aubert
 * @version 1.0
 */
public class MeanMinMaxMovement extends MovementModel {

    // Number of line and number of features
    private final static int NB_FEATURES = 3;
    private final static int NB_LINES = 6;
    // Constants for the normalization
    private static final float COL_MAX = 4100;
    private static final float COL_MIN = 0;
    private static final float NORM_MAX = 0.95f;
    private static final float NORM_MIN = -0.95f;

    /**
     * Constructor with all configurations
     *
     * @param listener a movement listener
     * @param windowSize the array of windows size
     * @param movementSize the array of movement size
     */
    public MeanMinMaxMovement(EventBus ebus, int[] windowSize, int[] movementSize) {
        super(ebus, windowSize, movementSize);
    }

    /**
     * Process all the three feature, normalize them and return them.
     *
     * @param iterator the iterator used to process the features
     */
    @Override
    protected float[] processFeatures(AccelGyro.UncalibratedSample[] window) {

        // Verify the feature array
        float[] features = new float[NB_FEATURES * NB_LINES];

        // Initialisation of the features array
        AccelGyro.UncalibratedSample sample = window[0];

        // Number of sample
        int nbSample = 1;

        // For each value of the first AccelGyroSample, initialise the features
        for (int i = 0; i < NB_LINES; i++) {
            // Initialize the mean, min, max 
            features[NB_FEATURES * i] = getVal(sample, i + 1);
            features[1 + NB_FEATURES * i] = getVal(sample, i + 1);
            features[2 + NB_FEATURES * i] = getVal(sample, i + 1);
        }

        // For each sample
        for (int j = 1; j < window.length; ++j) {
            sample = window[j];
            nbSample++;

            // For each value of the AccelGyroSample
            for (int i = 0; i < NB_LINES; i++) {

                // Mean
                features[NB_FEATURES * i] += getVal(sample, i + 1);

                // Min
                features[1 + NB_FEATURES * i] = Math.min(getVal(sample, i + 1), features[1 + NB_FEATURES * i]);

                // Max
                features[2 + NB_FEATURES * i] = Math.max(getVal(sample, i + 1), features[2 + NB_FEATURES * i]);
            }
        }

        // For each line process the mean
        for (int i = 0; i < NB_LINES; i++) {

            // Mean
            features[NB_FEATURES * i] /= nbSample;
        }

        // Normalize the features
        for (int i = 0; i < features.length; i++) {
            features[i] = (NORM_MAX - NORM_MIN) * ((features[i] - COL_MIN) / (COL_MAX - COL_MIN)) + NORM_MIN;
        }

        return features;
    }
}
