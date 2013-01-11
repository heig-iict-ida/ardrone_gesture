package controller;

/**
 * Enumeration class describing a possible action for the drone
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public enum ActionCommand
{
   SPEED,
   TAKEOFF,
   LAND,
   HOVER,
   GOTOP,
   GODOWN,
   GOFORWARD,
   GOBACKWARD,
   GOLEFT,
   GORIGHT,
   ROTATELEFT,
   ROTATERIGHT,
   CHANGEVIDEO,
   NOTHING;

   /**
    * Allow to add an integer value to the constant, useful for the speed
    * for example
    */
   private int val;

   /**
    * @return the integer value associated with the constant
    */
   public int getVal() {
      return val;
   }

   /**
    * @param val the integer value to set for the constant
    */
   public void setVal(int val) {
      this.val = val;
   }
}
