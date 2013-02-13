package madsdf.ardrone.controller.neuralnet;

import com.google.common.eventbus.EventBus;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import madsdf.shimmer.gui.AccelGyro;

/**
 * Movement model implementing the abstract MovementModel and implementing the
 * processFeatures methods with only three features. This movement model
 * is a specific implementation for the networks which need only the mean,
 * difference and median features.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class MeanDifferenceMedianMovement extends MovementModel {
   
   // Number of line and number of features
   private final static int NB_FEATURES = 3;
   private final static int NB_LINES = 6;
   
   // Constants for the normalization
   private static final float COL_MAX = 4100;
   private static final float COL_MIN = 0;
   private static final float NORM_MAX = 0.95f;
   private static final float NORM_MIN = -0.95f;
   
   public MeanDifferenceMedianMovement(EventBus ebus, int[] windowSize, int[] movementSize){
      super(ebus, windowSize, movementSize);
   }

   /**
    * Process all the three feature, normalize them and return them.
    * @param iterator the iterator used to process the features
    */
   @Override
   protected float[] processFeatures(AccelGyro.UncalibratedSample[] window) {
      // Verify the feature array
      float[] features = new float[NB_FEATURES * NB_LINES];
      
      // Copy all the sample values
      LinkedList<Float>[] sampleCopy = new LinkedList[NB_LINES];
      for(int i = 0; i < sampleCopy.length; i++)
         sampleCopy[i] = new LinkedList<Float>();
      
      // Initialisation of the features array
      AccelGyro.UncalibratedSample sample = window[0];
      
      // The maximum values
      float[] maxValue = new float[NB_LINES];
      
      // The minimum values
      float[] minValue = new float[NB_LINES];
      
      // For each value of the first AccelGyroSample, initialise the features
      for(int i = 0; i < NB_LINES; i++){
         
         // Copy the sample
         sampleCopy[i].add((float)getVal(sample, i+1));
         
         // Initialize the mean, min and max
         features[NB_FEATURES * i] = getVal(sample, i+1);
         minValue[i] = getVal(sample, i+1);
         maxValue[i] = getVal(sample, i+1);
      }
      
      // For each sample
      for (int j = 1; j < window.length; ++j) {
          sample = window[j];
         
         // For each value of the AccelGyroSample
         for(int i = 0; i < NB_LINES; i++){
            
            // Copy
            sampleCopy[i].add((float)getVal(sample, i+1));
            
            // Mean
            features[NB_FEATURES * i] += getVal(sample, i+1);
            
            // Min
            minValue[i] = Math.min(getVal(sample, i+1), minValue[i]);
            
            // Max
            maxValue[i] = Math.max(getVal(sample, i+1), maxValue[i]);
         }
      }
      
      // For each value of the AccelGyroSample, process the remaining features
      for(int i = 0; i < NB_LINES; i++){
         
         
         // Mean
         features[NB_FEATURES * i] /= sampleCopy[i].size();
         
         // Difference
         features[1+NB_FEATURES * i] = maxValue[i] - minValue[i];
         
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
      
      return features;
   }   
}
