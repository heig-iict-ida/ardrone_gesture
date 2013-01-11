package madsdf.ardrone.gesture;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Movement model implementing the abstract MovementModel and implementing the
 * processFeatures methods with all the seven features. This movement model
 * is an example of implementation and is used to test the neural network. It
 * allow to choose which features must be send to the network.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class SevenFeaturesSelectionMovement extends MovementModel {
   
   // Number of line and number of features
   private final static int NB_FEATURES = 7;
   private final static int NB_LINES = 6;
   
   // Constants for the normalization
   private static final float COL_VAR_MAX = 3000000;
   private static final float COL_MAX = 4100;
   private static final float COL_MIN = 0;
   private static final float NORM_MAX = 0.95f;
   private static final float NORM_MIN = -0.95f;
   
   // The array of chosen features
   private Features[] selectedFeatures;
   
   // The position of the variance feature in the input, the variance need
   // a special treatment
   private int variancePos = -1;
   
   /**
    * Constructor with all configurations
    * @param listener a movement listener
    * @param windowSize the array of windows size
    * @param movementSize the array of movement size
    * @param selectedFeatures the chosen features 
    */
   public SevenFeaturesSelectionMovement(MovementListener listener, int[] windowSize, int[] movementSize, Features ... selectedFeatures){      
      super(listener, windowSize, movementSize);
      this.selectedFeatures = selectedFeatures;
      setVariancePos();
   }
   
   /**
    * Constructor with default window size and movement size
    * @param listener a movement listener
    * @param selectedFeatures the chosen features 
    */
   public SevenFeaturesSelectionMovement(MovementListener listener, Features ... selectedFeatures){      
     super(listener);
      this.selectedFeatures = selectedFeatures;
      setVariancePos();
   }
   
   /**
    * Constructor with no movement listener
    * @param windowSize the array of windows size
    * @param movementSize the array of movement size
    * @param selectedFeatures the chosen features 
    */
   public SevenFeaturesSelectionMovement(int[] windowSize, int[] movementSize, Features ... selectedFeatures){
      super(windowSize, movementSize);
      this.selectedFeatures = selectedFeatures;
      setVariancePos();
   }
   
   /**
    * Constructor using the default configuration and without movement listener
    * @param selectedFeatures the chosen features 
    */
   public SevenFeaturesSelectionMovement(Features ... selectedFeatures){
      super();
      this.selectedFeatures = selectedFeatures;
      setVariancePos();
   }
   
   /**
    * Find the variance position in the chosen features list
    */
   private void setVariancePos(){
      for(int i = 0; i < selectedFeatures.length; i++)
         if(selectedFeatures[i] == Features.VARIANCE){
            variancePos = i;
            return;
         }   
   }

   /**
    * Process all the seven feature, normalize them and return only the chosen one.
    * @param iterator the iterator used to process the features
    */
   @Override
   protected void processFeatures(ListIterator<AccelGyroSample> iterator) {
      
      // Verify the feature array
      float[] calcFeatures = new float[NB_FEATURES * NB_LINES];
      float[] features = getFeatures();
      if(features == null){
         features = new float[selectedFeatures.length * NB_LINES];
      }
      
      // Copy all the sample values
      LinkedList<Float>[] sampleCopy = new LinkedList[NB_LINES];
      for(int i = 0; i < sampleCopy.length; i++)
         sampleCopy[i] = new LinkedList<Float>();
      
      // Initialisation of the features array
      AccelGyroSample sample = iterator.next();
      
      // For each value of the first AccelGyroSample, initialise the features
      for(int i = 0; i < NB_LINES; i++){
         
         // Copy the sample
         sampleCopy[i].add((float)sample.getVal(i+1));
         
         // Initialize the max, min, mean and variance
         calcFeatures[NB_FEATURES * i] = sample.getVal(i+1);
         calcFeatures[1+NB_FEATURES * i] = sample.getVal(i+1);
         calcFeatures[4+NB_FEATURES * i] = sample.getVal(i+1);
         calcFeatures[5+NB_FEATURES * i] = 0;
      }
      
      // For each sample
      while(iterator.hasNext()){
         sample = iterator.next();
         
         // For each value of the AccelGyroSample
         for(int i = 0; i < NB_LINES; i++){
            
            // Copy
            sampleCopy[i].add((float)sample.getVal(i+1));
            
            // Max
            calcFeatures[NB_FEATURES * i] = Math.max(sample.getVal(i+1), calcFeatures[NB_FEATURES * i]);
            
            // Min
            calcFeatures[1+NB_FEATURES * i] = Math.min(sample.getVal(i+1), calcFeatures[1+NB_FEATURES * i]);
            
            // Mean
            calcFeatures[4+NB_FEATURES * i] += sample.getVal(i+1);
         }
      }
      
      // For each value of the AccelGyroSample, process the remaining features
      for(int i = 0; i < NB_LINES; i++){
         
         // Median
         Float[] med = sampleCopy[i].toArray(new Float[0]);
         Arrays.sort(med);
         if(med.length % 2 == 0)
            calcFeatures[2+NB_FEATURES * i] = (med[med.length / 2] + med[med.length / 2 + 1]) / 2.f;
         else
            calcFeatures[2+NB_FEATURES * i] = med[med.length / 2 + 1];
         
         // Difference
         calcFeatures[3+NB_FEATURES * i] = calcFeatures[NB_FEATURES * i] - calcFeatures[1+NB_FEATURES * i];

         // Mean
         calcFeatures[4+NB_FEATURES * i] /= sampleCopy[i].size();

         // Variance
         for(Float f : sampleCopy[i])
            calcFeatures[5+NB_FEATURES * i] += (int)Math.pow(f - calcFeatures[4+NB_FEATURES * i], 2);
         calcFeatures[5+NB_FEATURES * i] /= sampleCopy[i].size();

         // Standard deviation
         calcFeatures[6+NB_FEATURES * i] = (float)Math.sqrt(calcFeatures[5+NB_FEATURES * i]);
         
      }
      
      // Select the choosen features
      for(int i = 0; i < NB_LINES; i++)
         for(int j = 0; j < selectedFeatures.length; j++)
            features[j + selectedFeatures.length * i] = calcFeatures[selectedFeatures[j].val + NB_FEATURES * i];
      
      // Special normalization if the variance is choosen
      if(variancePos > -1){
         for(int i = 0; i < 6; i++)
            for(int j = 0; j < features.length; j++)
               if(variancePos == j)
                  features[i] = (NORM_MAX - NORM_MIN) * ((features[i] - COL_MIN) / (COL_VAR_MAX - COL_MIN)) + NORM_MIN;
               else
                  features[i] = (NORM_MAX - NORM_MIN) * ((features[i] - COL_MIN) / (COL_MAX - COL_MIN)) + NORM_MIN;
      }else{
         // Normalize the features
         for(int i = 0; i < features.length; i++)
            features[i] = (NORM_MAX - NORM_MIN) * ((features[i] - COL_MIN) / (COL_MAX - COL_MIN)) + NORM_MIN;
      }
      
      setFeatures(features);
   }   
}
