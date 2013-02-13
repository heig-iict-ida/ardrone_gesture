package madsdf.ardrone.controller.neuralnet;

import static com.google.common.base.Preconditions.*;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.event.EventListenerList;
import madsdf.ardrone.utils.WindowAccumulator;
import madsdf.shimmer.gui.AccelGyro;

/**
 * Represent a complete movement done by the Shimmer captor wielder. This class
 * is abstract and general.The processFeatures method is specific to a neural
 * network and has to be implemented by a MovementModel descendent specialized
 * for a specific neural network type.
 *
 * Java version : JDK 1.6.0_21 IDE : Netbeans 7.1.1
 *
 * @author Gregoire Aubert
 * @version 1.0
 */
public abstract class MovementModel {
    public static class MovementFeatures {
        public final float[] data;

        public MovementFeatures(float[] f) {
            checkNotNull(f);
            data = new float[f.length];
            System.arraycopy(f, 0, data, 0, f.length);
        }
    }
    
    // Wrapper for neural network feature extraction based on old AccelGyro
    // sample
    // TODO: Remove : this is only for backward-compatibility
    public static float getVal(AccelGyro.Sample sample, int i) {
        if (i < 1 || i > 6) {
            throw new IllegalArgumentException("Invaild i : " + i);
        }
        if (i <= 3) {
            return sample.accel[i - 1];
        } else {
            return sample.gyro[i - 4];
        }

    }
    
    // The defaults values
    public static final int DEFAULT_WINDOWSIZE = 20;
    public static final int DEFAULT_MOVEMENTSIZE = 100;
    public static final int DEFAULT_MAX_BUFFER_SIZE = 5000;
    // The array of sliding windows size
    private int[] windowSize;
    // The array of the movements size (represent the number of sample in one movement)
    private int[] movementSize;
    // The biggest movement size of the array
    private int maxMovementSize = 0;
    private WindowAccumulator[] accumulators;
    
    private EventBus ebus;
    
    private long prevWindowTimestamp = System.currentTimeMillis();

    /**
     * Constructor with all configurations
     *
     * @param ebus the event bus to use for event broadcasting
     * @param windowSize the array of windows size (can be null)
     * @param movementSize the array of movement size (can be null)
     */
    public MovementModel(EventBus ebus, int[] windowSize, int[] movementSize) {
        this.ebus = ebus;
        this.windowSize = windowSize;
        setMovementSize(movementSize);
        
        if (windowSize == null) {
            // Create the default window size array
            windowSize = new int[] { DEFAULT_WINDOWSIZE };
        }

        if (movementSize == null) {
            // Create the default movement size array
            movementSize = new int[] { DEFAULT_MOVEMENTSIZE };
        }
        setMovementSize(movementSize);

        accumulators = new WindowAccumulator[movementSize.length];
        for (int i = 0; i < movementSize.length; ++i) {
            // TODO: Fixed step is not too good... make it a parameter
            accumulators[i] = new WindowAccumulator<AccelGyro.UncalibratedSample>(movementSize[i], 10);
        }
    }

    /**
     * Add a new AccelGyroSample to the sample list. Process the features when
     * the movement size is big enough and the windows has completely slid.
     *
     * @param sample the new sample to add
     */
    public void addAccelGyroSample(AccelGyro.UncalibratedSample sample) {
        //System.out.println("Sample : ax = " + sample.accel[0] + ", ay = " + sample.accel[1] + ", az = " + sample.accel[2]);
        // Accumulate the sample and if a new window is available, process it
        for (int i = 0; i < windowSize.length; ++i) {
            List<AccelGyro.UncalibratedSample> window = accumulators[i].add(sample);
            if (window != null) {
                final float[] features = processFeatures(window.toArray(new AccelGyro.UncalibratedSample[1]));
                final long now = System.currentTimeMillis();
                //double elapsedS = (now - prevWindowTimestamp) / 1000.0;
                //System.out.println("[" + this + "] Time since last window : " + elapsedS);
                prevWindowTimestamp = now;
                ebus.post(new MovementFeatures(features));
            }
        }
    }
    
    /**
     * Abstract method for the calculation of the features. Its implementation
     * depends on the features that will be processed.
     *
     * @param iterator the iterator used to process the features
     */
    protected abstract float[] processFeatures(AccelGyro.UncalibratedSample[] window);

    /**
     * @param movementSize the movementSize to set
     */
    private void setMovementSize(int[] movementSize) {

        // Set the movement size
        this.movementSize = movementSize;

        // Search the biggest size
        maxMovementSize = movementSize[0];
        for (int i = 1; i < movementSize.length; i++) {
            maxMovementSize = Math.max(maxMovementSize, movementSize[i]);
        }
    }
}
