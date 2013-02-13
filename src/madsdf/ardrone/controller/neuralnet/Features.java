package madsdf.ardrone.controller.neuralnet;

/**
 * This enum class represent all the features that can be used for the gesture
 * recognition.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public enum Features {
   MAX(0), MIN(1), MEDIAN(2), DIFFERENCE(3), MEAN(4), VARIANCE(5), STANDARD_DEVIATION(6);
   
   // The feature index
   int val;
   
   /**
    * Private constructor
    * @param val is the index of the feature
    */
   private Features(int val){
      this.val = val;
   }
}
