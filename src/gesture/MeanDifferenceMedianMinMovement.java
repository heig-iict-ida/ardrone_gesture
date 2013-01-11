package gesture;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Movement model implementing the abstract MovementModel and implementing the
 * processFeatures methods with only four features. This movement model
 * is a specific implementation for the networks which need only the mean,
 * difference, median and min features.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class MeanDifferenceMedianMinMovement extends MovementModel {
   
   // Number of line and number of features
   private final static int NB_FEATURES = 4;
   private final static int NB_LINES = 6;
   
   // Constants for the normalization
   private static final float COL_MAX = 4100;
   private static final float COL_MIN = 0;
   private static final float NORM_MAX = 0.95f;
   private static final float NORM_MIN = -0.95f;
   
   /**
    * Constructor with all configurations
    * @param listener a movement listener
    * @param windowSize the array of windows size
    * @param movementSize the array of movement size
    */
   public MeanDifferenceMedianMinMovement(MovementListener listener, int[] windowSize, int[] movementSize){
      super(listener, windowSize, movementSize);
   }
   
   /**
    * Constructor with default window size and movement size
    * @param listener a movement listener
    */
   public MeanDifferenceMedianMinMovement(MovementListener listener){      
      super(listener);
   }
   
   /**
    * Constructor with no movement listener
    * @param windowSize the array of windows size
    * @param movementSize the array of movement size
    */
   public MeanDifferenceMedianMinMovement(int[] windowSize, int[] movementSize){
      super(windowSize, movementSize);
   }
   
   /**
    * Constructor using the default configuration and without movement listener
    */
   public MeanDifferenceMedianMinMovement(){
      super();
   }

   /**
    * Process all the four feature, normalize them and return them.
    * @param iterator the iterator used to process the features
    */
   @Override
   protected void processFeatures(ListIterator<AccelGyroSample> iterator) {
      
      // Verify the feature array
      float[] features = getFeatures();
      if(features == null){
         features = new float[NB_FEATURES * NB_LINES];
      }
      
      // Copy all the sample values
      LinkedList<Float>[] sampleCopy = new LinkedList[NB_LINES];
      for(int i = 0; i < sampleCopy.length; i++)
         sampleCopy[i] = new LinkedList<Float>();
      
      // Initialisation of the features array
      AccelGyroSample sample = iterator.next();
      
      // The maximum values
      float[] maxValue = new float[NB_LINES];
      
      // For each value of the first AccelGyroSample, initialise the features
      for(int i = 0; i < NB_LINES; i++){
         
         // Copy the sample
         sampleCopy[i].add((float)sample.getVal(i+1));
         
         // Initialize the mean, min and max
         features[NB_FEATURES * i] = sample.getVal(i+1);
         features[3+NB_FEATURES * i] = sample.getVal(i+1);
         maxValue[i] = sample.getVal(i+1);
      }
      
      // For each sample
      while(iterator.hasNext()){
         sample = iterator.next();
         
         // For each value of the AccelGyroSample
         for(int i = 0; i < NB_LINES; i++){
            
            // Copy
            sampleCopy[i].add((float)sample.getVal(i+1));
            
            // Mean
            features[NB_FEATURES * i] += sample.getVal(i+1);
            
            // Min
            features[3+NB_FEATURES * i] = Math.min(sample.getVal(i+1), features[3+NB_FEATURES * i]);
            
            // Max
            maxValue[i] = Math.max(sample.getVal(i+1), maxValue[i]);
         }
      }
      
      // For each value of the AccelGyroSample, process the remaining features
      for(int i = 0; i < NB_LINES; i++){
         
         
         // Mean
         features[NB_FEATURES * i] /= sampleCopy[i].size();
         
         // Difference
         features[1+NB_FEATURES * i] = maxValue[i] - features[3+NB_FEATURES * i];
         
         // Median
         Float[] med = sampleCopy[i].toArray(new Float[0]);
         Arrays.sort(med);
         if(med.length % 2 == 0)
            features[2+NB_FEATURES * i] = (med[med.length / 2] + med[med.length / 2 + 1]) / 2.f;
         else
            features[2+NB_FEATURES * i] = med[med.length / 2 + 1];
      }
     
      // Normalize the features
      for(int i = 0; i < features.length; i++)
         features[i] = (NORM_MAX - NORM_MIN) * ((features[i] - COL_MIN) / (COL_MAX - COL_MIN)) + NORM_MIN;
      
      setFeatures(features);
   }   
}
