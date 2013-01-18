package madsdf.ardrone.gesture;

import static com.google.common.base.Preconditions.*;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.event.EventListenerList;
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
    // The defaults values
    public static final int DEFAULT_WINDOWSIZE = 20;
    public static final int DEFAULT_MOVEMENTSIZE = 100;
    public static final int DEFAULT_MAX_BUFFER_SIZE = 5000;
    // The array of sliding windows size
    private int[] windowSize;
    // The array of the movements size (represent the number of sample in one movement)
    private int[] movementSize;
    // The array of counters for each precedent movement size and windows size config
    private int[] counter;
    // The biggest movement size of the array
    private int maxMovementSize = 0;
    // The sample buffer maximum size
    private int maxBufferSize;
    // The calculated features of the last movement
    private float[] features;
    // The sample buffer
    private CopyOnWriteArrayList<AccelGyro.UncalibratedSample> movement = new CopyOnWriteArrayList<AccelGyro.UncalibratedSample>();
    // The movement listener list
    private final EventListenerList listeners = new EventListenerList();
    private EventBus ebus;

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

        // Define the counter array
        this.counter = new int[movementSize.length];
        Arrays.fill(counter, 0);
    }

    /**
     * Add a new AccelGyroSample to the sample list. Process the features when
     * the movement size is big enough and the windows has completely slid.
     *
     * @param sample the new sample to add
     */
    public void addAccelGyroSample(AccelGyro.UncalibratedSample sample) {

        // Add the sample
        getMovement().add(sample);

        // Clear the buffer regularly, avoids memory leaks
        if (getMovement().size() > maxBufferSize) {
            getMovement().retainAll(getMovement().subList(getMovement().size() - 2 * maxMovementSize, getMovement().size() - 1));
        }

        // Use a sliding windows, and process the features whenever the window has completely slid
        // and the movement size is enough for all the size configurations
        for (int i = 0; i < windowSize.length; i++) {
            if (counter[i]++ >= windowSize[i] && getMovement().size() >= movementSize[i]) {
                counter[i] = 0;
                processFeatures(getMovement().listIterator(getMovement().size() - movementSize[i]));
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
    protected abstract void processFeatures(ListIterator<AccelGyro.UncalibratedSample> iterator);

    /**
     * @return the actual movement
     */
    protected CopyOnWriteArrayList<AccelGyro.UncalibratedSample> getMovement() {
        return movement;
    }

    /**
     * @param features the features to set
     */
    protected void setFeatures(float[] features) {
        this.features = features;
    }

    /**
     * @return the actual features
     */
    public float[] getFeatures() {
        if (features == null) {
            return null;
        }
        return features.clone();
    }

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

        // Set the sample buffer maximum size
        this.maxBufferSize = Math.max(DEFAULT_MAX_BUFFER_SIZE, 3 * maxMovementSize);
    }
}
