package gesture;

import java.util.Arrays;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.event.EventListenerList;

/**
 * Represent a complete movement done by the Shimmer captor wielder.
 * This class is abstract and general.The processFeatures method is specific
 * to a neural network and has to be implemented by a MovementModel descendent
 * specialized for a specific neural network type.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public abstract class MovementModel {
   
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
   private CopyOnWriteArrayList<AccelGyroSample> movement = new CopyOnWriteArrayList<AccelGyroSample>();
   
   // The movement listener list
   private final EventListenerList listeners = new EventListenerList();
   
   /**
    * Constructor with all configurations
    * @param listener a movement listener
    * @param windowSize the array of windows size
    * @param movementSize the array of movement size
    */
   public MovementModel(MovementListener listener, int[] windowSize, int[] movementSize){      
      addMovementListener(listener);
      this.windowSize = windowSize;
      setMovementSize(movementSize);
      
      // Define the counter array
      this.counter = new int[movementSize.length];
      Arrays.fill(counter, 0);
   }
   
   /**
    * Constructor with default window size and movement size
    * @param listener a movement listener
    */
   public MovementModel(MovementListener listener){      
      addMovementListener(listener);
      
      // Create the default window size array
      windowSize = new int [1];
      windowSize[0] = DEFAULT_WINDOWSIZE;
      
      // Create the default movement size array
      int[] moveSize = new int[1];
      moveSize[0] = DEFAULT_MOVEMENTSIZE;
      setMovementSize(moveSize);
      
      // Define the counter array
      counter = new int[1];
      counter[0] = 0;
   }
   
   /**
    * Constructor with no movement listener
    * @param windowSize the array of windows size
    * @param movementSize the array of movement size
    */
   public MovementModel(int[] windowSize, int[] movementSize){
      this.windowSize = windowSize;
      setMovementSize(movementSize);
      
      // Define the counter array
      this.counter = new int[movementSize.length];
      Arrays.fill(counter, 0);
   }
   
   /**
    * Constructor using the default configuration and without movement listener
    */
   public MovementModel(){
      
      // Create the default window size array
      windowSize = new int [1];
      windowSize[0] = DEFAULT_WINDOWSIZE;
      
      // Create the default movement size array
      int[] moveSize = new int[1];
      moveSize[0] = DEFAULT_MOVEMENTSIZE;
      setMovementSize(moveSize);
      
      // Define the counter array
      counter = new int[1];
      counter[0] = 0;
   }
   
   /**
    * Add a new AccelGyroSample to the sample list. Process the features
    * when the movement size is big enough and the windows has completely slid.
    * @param sample the new sample to add
    */
   public void addAccelGyroSample(AccelGyroSample sample){
      
      // Add the sample
      getMovement().add(sample);

      // Clear the buffer regularly, avoids memory leaks
      if(getMovement().size() > maxBufferSize)
         getMovement().retainAll(getMovement().subList(getMovement().size() - 2 * maxMovementSize, getMovement().size() - 1));

      // Use a sliding windows, and process the features whenever the window has completely slid
      // and the movement size is enough for all the size configurations
      for(int i = 0; i < windowSize.length; i++)
         if(counter[i]++ >= windowSize[i] && getMovement().size() >= movementSize[i]){
            counter[i] = 0;
            processFeatures(getMovement().listIterator(getMovement().size() - movementSize[i]));
            fireMovementReady();
         }
   }
   
   /**
    * Abstract method for the calculation of the features. Its implementation depends
    * on the features that will be processed.
    * @param iterator the iterator used to process the features
    */
   protected abstract void processFeatures(ListIterator<AccelGyroSample> iterator);

   /**
    * @param listener the listener to add to the MovementListener list
    */
   public final void addMovementListener(MovementListener listener) {
      listeners.add(MovementListener.class, listener);
   }

   /**
    * @param listener the listener to remove from the MovementListener list
    */
   public final void removeMovementListener(MovementListener listener) {
      listeners.remove(MovementListener.class, listener);
   }

   /**
    * Retrieve all the movementListener in the list
    * @return the registered movementListener
    */
   public final MovementListener[] getMovementListeners() {
      return listeners.getListeners(MovementListener.class);
   }
   
   /**
    * Fire the movementReady method from all the listeners.
    */
   protected void fireMovementReady() {  
      for(MovementListener listener : getMovementListeners()) {
         listener.movementReady(getFeatures());
      }
   }

   /**
    * @return the actual movement
    */
   protected CopyOnWriteArrayList<AccelGyroSample> getMovement() {
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
      if(features == null)
         return null;
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
      for(int i = 1; i < movementSize.length; i++)
         maxMovementSize = Math.max(maxMovementSize, movementSize[i]);
      
      // Set the sample buffer maximum size
      this.maxBufferSize = Math.max(DEFAULT_MAX_BUFFER_SIZE, 3 * maxMovementSize);
   }
}
