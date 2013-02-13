package madsdf.ardrone;

/**
 * This enum class represent the possible flying state of the drone.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public enum FlyingState
{
   LANDED, FLYING, TAKING_OFF, LANDING;

   private static int val;

   /**
    * Convert an integer to a FlyingState
    * @param v the integer to convert
    * @return the corresponding FlyingState
    */
   public static FlyingState fromInt(int v){
      val = v;
      switch(v){
         case 3:
         case 4:
         case 7:
             return FLYING;
         case 6:
             return TAKING_OFF;
         case 8:
             return LANDING;
         default:
             return LANDED;
      }
   }

   /**
    * @return the flying state name with its value
    */
   public String toString() {
      return super.toString() + "(" + val + ")";
   }
}
