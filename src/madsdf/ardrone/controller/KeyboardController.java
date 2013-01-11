package madsdf.ardrone.controller;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * The keyboard controller for control of the drone.
 * This class implement a KeyListener update the drone action map when an 
 * ActionCommand key is pressed or released on the keyboard.
 * 
 * Here is a description of the command :
 * 
 * Takeoff : PageUp 
 * Landing : PageDown 
 * Hovering: Space (automatically hovering when no command entry)
 * Switch between video mode : V
 * 
 * Arrow keys:
 *         Go Forward
 *             ^
 *             |
 * Go Left <---+---> Go Right
 *             |
 *             v
 *        Go Backward
 * 
 * WASD keys:
 *               Go Up
 *                 ^
 *                 W
 * Rotate Left <-A-+-D-> Rotate Right
 *                 S
 *                 v
 *              Go Down
 *             
 * Digital keys 1~9: Change speed (rudder rate 5%~99%), 1 is min and 9 is max.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class KeyboardController implements KeyListener {

   // The controlled drone
   ARDrone arDrone;
   
   /**
    * Constructor
    * @param arDrone the controlled drone
    */
   public KeyboardController(ARDrone arDrone) {
      this.arDrone = arDrone;
      arDrone.addKeyListener(this);
   }

   /**
    * Listen to the typed keys, not used.    
    * @param e the key event who called the method
    */
   public void keyTyped(KeyEvent e) {
   }

   /**
    * Listen to the pressed keys
    * @param e the key event who called the method
    */
   public void keyPressed(KeyEvent e) {
      
      // Convert the key in a ActionCommand and update the drone action map
      arDrone.updateActionMap(actionFromKeyEvent(e), true);
   }

   /**
    * Listen to the released keys
    * @param e the key event who called the method
    */
   public void keyReleased(KeyEvent e) {
      
      // Update the action map with the key released
      arDrone.updateActionMap(actionFromKeyEvent(e), false);
   }

   /**
    * Convert a KeyEvent in an ActionCommand
    * @param e the KeyEvent to convert
    * @return the corresponding ActionCommand
    */
   public ActionCommand actionFromKeyEvent(KeyEvent e) {
      switch (e.getKeyCode()) {

         // Speed control
         case KeyEvent.VK_1:
         case KeyEvent.VK_2:
         case KeyEvent.VK_3:
         case KeyEvent.VK_4:
         case KeyEvent.VK_5:
         case KeyEvent.VK_6:
         case KeyEvent.VK_7:
         case KeyEvent.VK_8:
         case KeyEvent.VK_9:
            ActionCommand.SPEED.setVal(e.getKeyCode() - KeyEvent.VK_1 + 1);
            return ActionCommand.SPEED;

         // Switch video channel
         case KeyEvent.VK_V:
            return ActionCommand.CHANGEVIDEO;

         // Go up (gaz+)
         case KeyEvent.VK_W:
            return ActionCommand.GOTOP;

         // Go down (gaz-)
         case KeyEvent.VK_S:
            return ActionCommand.GODOWN;

         // Rotate on the right (yaw+)
         case KeyEvent.VK_D:
            return ActionCommand.ROTATERIGHT;

         // Rotate on the left (yaw-)
         case KeyEvent.VK_A:
            return ActionCommand.ROTATELEFT;

         // Go forward (pitch+)
         case KeyEvent.VK_UP:
            return ActionCommand.GOFORWARD;

         // Go backward (pitch-)
         case KeyEvent.VK_DOWN:
            return ActionCommand.GOBACKWARD;

         // Move to the right (roll+)
         case KeyEvent.VK_RIGHT:
            return ActionCommand.GORIGHT;

         // Move to the left (roll-)
         case KeyEvent.VK_LEFT:
            return ActionCommand.GOLEFT;

         // Try to take off
         case KeyEvent.VK_PAGE_UP:
            return ActionCommand.TAKEOFF;

         // Land
         case KeyEvent.VK_PAGE_DOWN:
            return ActionCommand.LAND;

         // Force the drone to hover
         case KeyEvent.VK_SPACE:
            return ActionCommand.HOVER;

         default:
            return ActionCommand.NOTHING;
      }
   }
}
