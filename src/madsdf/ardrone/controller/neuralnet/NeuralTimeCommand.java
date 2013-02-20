package madsdf.ardrone.controller.neuralnet;

import java.util.concurrent.ConcurrentHashMap;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.controller.DroneController;

/**
 * This class manage the time a detected ActionCommand should have effect on
 * the drone.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class NeuralTimeCommand extends Thread {

   // Constant of the default time values
   static final int DEFAULT_TIMER = 50;
   static final int DEFAULT_NB_TIMER = 6;
   
   // The time in ms between two check
   private int timerMs = DEFAULT_TIMER;
   
   // The number of check an action should be active
   private int nbTimerMs = DEFAULT_NB_TIMER;
   
   // The controller
   private DroneController controller;
   
   // A map of all the ActionCommand already detected and their status
   private ConcurrentHashMap<ActionCommand, Integer> actionMap = new ConcurrentHashMap<ActionCommand, Integer>();
   
   /**
    * Constructor
    * @param arDrone the controlled drone
    */
   public NeuralTimeCommand(DroneController controller){
      this.controller = controller;
   }
   
   /**
    * Constructor with all the configurations.
    * @param timerMs the time in ms between two check.
    * @param nbTimerMs the number of check an action should be active
    * @param arDrone the controlled drone
    */
   public NeuralTimeCommand(int timerMs, int nbTimerMs, DroneController controller){
      this.timerMs = timerMs;
      this.nbTimerMs = nbTimerMs;
      this.controller = controller;
   }
   
   /**
    * Restart the time of an ActionCommand, should be used each time a command
    * is detected.
    * @param actCmd the detected command
    */
   public void updateTimeCmd(ActionCommand actCmd){
      actionMap.put(actCmd, nbTimerMs);
   }
   
   @Override
   public void run() {
      
      // The thread end when the ardrone class is terminated
      while(true){
         
         // Update the check number of each action command in the list
         int val;
         for(ActionCommand actCmd : actionMap.keySet()){
            val = actionMap.get(actCmd) - 1;
            
            // If the check number reach 0 the action is disabled and the drone
            // action map is updated
            if(val == 0) {
                controller.directUpdateDroneAction(actCmd, false);
            }
            actionMap.put(actCmd, val);
         }
         try {
            // Wait between two check
            sleep(timerMs);
         }
         catch (InterruptedException ex) {
            System.err.println("NeuralTimeCommand.run: " + ex);
         }
      }
   }
}
