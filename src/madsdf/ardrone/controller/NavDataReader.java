package madsdf.ardrone.controller;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Receive all the navigation data from the drone, interprete them and display
 * them on the screen.
 * Here is the definition of the mask for the ARDrone state :
 * 31                                                             0
 *  x x x x x x x x x x x x x x x x x x x x x x x x x x x x x x x x -> state
 *  | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | |
 *  | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | FLY MASK : (0) ardrone is landed, (1) ardrone is flying
 *  | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | VIDEO MASK : (0) video disable, (1) video enable
 *  | | | | | | | | | | | | | | | | | | | | | | | | | | | | | VISION MASK : (0) vision disable, (1) vision enable
 *  | | | | | | | | | | | | | | | | | | | | | | | | | | | | CONTROL ALGO : (0) euler angles control, (1) angular speed control
 *  | | | | | | | | | | | | | | | | | | | | | | | | | | | ALTITUDE CONTROL ALGO : (0) altitude control inactive (1) altitude control active
 *  | | | | | | | | | | | | | | | | | | | | | | | | | | USER feedback : Start button state
 *  | | | | | | | | | | | | | | | | | | | | | | | | | Control command ACK : (0) None, (1) one received
 *  | | | | | | | | | | | | | | | | | | | | | | | | Trim command ACK : (0) None, (1) one received
 *  | | | | | | | | | | | | | | | | | | | | | | | Trim running : (0) none, (1) running
 *  | | | | | | | | | | | | | | | | | | | | | | Trim result : (0) failed, (1) succeeded
 *  | | | | | | | | | | | | | | | | | | | | | Navdata demo : (0) All navdata, (1) only navdata demo
 *  | | | | | | | | | | | | | | | | | | | | Navdata bootstrap : (0) options sent in all or demo mode, (1) no navdata options sent
 *  | | | | | | | | | | | | | | | | | | | Motors status : (0) Ok, (1) Motors Com is down
 *  | | | | | | | | | | | | | | | | | | 
 *  | | | | | | | | | | | | | | | | | Bit means that there's an hardware problem with gyrometers
 *  | | | | | | | | | | | | | | | | VBat low : (1) too low, (0) Ok
 *  | | | | | | | | | | | | | | | VBat high (US mad) : (1) too high, (0) Ok
 *  | | | | | | | | | | | | | | Timer elapsed : (1) elapsed, (0) not elapsed
 *  | | | | | | | | | | | | | Power : (0) Ok, (1) not enough to fly
 *  | | | | | | | | | | | | Angles : (0) Ok, (1) out of range
 *  | | | | | | | | | | | Wind : (0) Ok, (1) too much to fly
 *  | | | | | | | | | | Ultrasonic sensor : (0) Ok, (1) deaf
 *  | | | | | | | | | Cutout system detection : (0) Not detected, (1) detected
 *  | | | | | | | | PIC Version number OK : (0) a bad version number, (1) version number is OK
 *  | | | | | | | ATCodec thread ON : (0) thread OFF (1) thread ON
 *  | | | | | | Navdata thread ON : (0) thread OFF (1) thread ON
 *  | | | | | Video thread ON : (0) thread OFF (1) thread ON
 *  | | | | Acquisition thread ON : (0) thread OFF (1) thread ON
 *  | | | CTRL watchdog : (1) delay in control execution (> 5ms), (0) control is well scheduled  * Check frequency of control loop
 *  | | ADC Watchdog : (1) delay in uart2 dsr (> 5ms), (0) uart2 is good  * Check frequency of uart2 dsr (com with adc)
 *  | Communication Watchdog : (1) com problem, (0) Com is ok  * Check if we have an active connection with a client
 *  Emergency landing : (0) no emergency, (1) emergency
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class NavDataReader extends Thread
{   
   // Mask for the ARDrone state
   static final int COM_LOST_MASK = 1 << 13;
   static final int COM_BOOTSTRAP_MASK = 1 << 11;
   static final int COM_WATCHDOG_MASK = 1 << 30;
   static final int COM_EMERGENCY_MASK  = 1 << 31;
   
   // Value of the tag for the navdata demo
   static final int NAVDATA_DEMO_TAG = 0;
   
   // Default print every 20 receptions
   static final int RESTRICT_PRINT = 20;

   // The ARDrone
   private ARDrone arDrone;
   
   // The communication socket
   private DatagramSocket navDataSocket;

   /**
    * Constructor
    * @param arDrone the drone sending the navigation data
    */
   public NavDataReader(ARDrone arDrone){
      this.arDrone = arDrone;
      
      // Connect and configure the socket
      try {
         navDataSocket = new DatagramSocket(ARDrone.NAVDATA_PORT);
         navDataSocket.setSoTimeout(3000);
      }
      catch (SocketException ex) {
         System.err.println("NavDataReader: " + ex);
      }
   }

   /**
     * Convert the byte array to an int starting from the given offset.
     * @param b The byte array
     * @param offset The array offset
     * @return The integer
     */
   public static int byteArrayToInt(byte[] b, int offset)
   {
      int value = 0, tmp = 0;
      for(int i = 3; i >= 0; i--)
      {
         value <<= 8;
         tmp += b[i + offset] & 0x000000FF;
         value |= tmp;
      }
      return value;
   }

   /**
    * Convert the byte array to an int starting from the given offset.
    * @param b The byte array
    * @param offset The array offset
    * @return The short
    */
   public static int byteArrayToShort(byte[] b, int offset)
   {
      return ((b[offset + 1] & 0x000000FF) << 8) | (b[offset] & 0x000000FF);
   }
   
   @Override
   public void run() {
      
      try {

         // Send the trigger flag to the drone udp port to start the navdata stream
         byte[] buffer = {0x01, 0x00, 0x00, 0x00};
         DatagramPacket packet = new DatagramPacket(buffer, buffer.length, arDrone.getDroneAdr(), ARDrone.NAVDATA_PORT);
         navDataSocket.send(packet);
         arDrone.sendATCmd("AT*CONFIG=" + arDrone.getSeq() + ",\"general:navdata_demo\",\"TRUE\"");
         //myARDrone.sendATCmd("AT*CTRL=0");
         
         // Stock the received data
         byte[] navDataBuf = new byte[4096];
         DatagramPacket navDataPacket = new DatagramPacket(navDataBuf, navDataBuf.length);

         int bitStateMask,
             seqOnDrone,
             battery,
             altitude,
             option_tag,
             option_len;
         float pitch,
               roll,
               yaw,
               vx, vy, vz;
         int offset, print = 0;

         while(true) {
            try{

               navDataSocket.receive(navDataPacket);
               print++;
               offset = 0;

               // Retrieve the header
               offset += 4;

               // Retrieve the drone state bit mask
               bitStateMask = byteArrayToInt(navDataBuf, offset);
               offset += 4;

               // Retrieve the sequence number
               seqOnDrone = byteArrayToInt(navDataBuf, offset);
               offset += 4;

               // Retrieve the vision flag
               offset += 4;

               // Verify if an option packet is present
               if(offset < navDataBuf.length){

                  // Retrieve the demo option header
                  option_tag = byteArrayToShort(navDataBuf, offset);
                  offset += 2;
                  option_len = byteArrayToShort(navDataBuf, offset);
                  offset += 2;

                  // Verify that it is a demo option
                  if(option_tag == NAVDATA_DEMO_TAG && option_len > 0){

                     arDrone.setNavDataBootStrap(false);

                     // Retrieve the flying state
                     arDrone.setFlyingState(FlyingState.fromInt(byteArrayToInt(navDataBuf, offset) >> 16));
                     offset += 4;

                     // Retrieve the battery %
                     battery = byteArrayToInt(navDataBuf, offset);
                     offset += 4;
   
                     // Retrieve the pitch in milli-degrees
                     pitch = Float.intBitsToFloat(byteArrayToInt(navDataBuf, offset)) / 1000;
                     offset += 4;
   
                     // Retrieve the roll in milli-degrees
                     roll =  Float.intBitsToFloat(byteArrayToInt(navDataBuf, offset)) / 1000;
                     offset += 4;
   
                     // Retrieve the yaw in milli-degrees
                     yaw =  Float.intBitsToFloat(byteArrayToInt(navDataBuf, offset)) / 1000;
                     offset += 4;
   
                     // Retrieve the altitude in centimeters
                     altitude = byteArrayToInt(navDataBuf, offset);
                     offset += 4;
   
                     // Retrieve the estimated linear velocity
                     vx = Float.intBitsToFloat(byteArrayToInt(navDataBuf, offset));
                     offset += 4;
                     vy = Float.intBitsToFloat(byteArrayToInt(navDataBuf, offset));
                     offset += 4;
                     vz = Float.intBitsToFloat(byteArrayToInt(navDataBuf, offset));
                     offset += 4;

                     // Print the result
                     if(print == RESTRICT_PRINT){
                        print = 0;
                        System.out.println("bit mask : " + Integer.toBinaryString(bitStateMask)
                                        + " | sequence : " + seqOnDrone
                                        + " | watchdog : " + (bitStateMask & COM_WATCHDOG_MASK)
                                        + " | status : " + arDrone.getFlyingState()
                                        + " | altitude : " + altitude + " mm"
                                        + " | battery : " + battery + " %"
                                        + " | speed : [" + vx + ", " + vy + ", " + vz + "]"
                                        + " | pitch,roll,yaw : [" + pitch + ", " + roll + ", " + yaw + "]");
                        
                        // Set the battery level
                        arDrone.batteryLevel.setValue(battery);
                        
                        //printBitMask(bitStateMask);
                     }

                  }
                  else{
                     // Print the result
                     if(print == RESTRICT_PRINT){
                        print = 0;
                        System.out.println("bit mask : " + Integer.toBinaryString(bitStateMask)
                                        + " | sequence : " + seqOnDrone
                                        + " | watchdog : " + (bitStateMask & COM_WATCHDOG_MASK)
                                        + " | no demo option packet");

                        //printBitMask(bitStateMask);
                     }
                     arDrone.setNavDataBootStrap(true);
                  }

                  // Verify the bootstrap mode
                  if(arDrone.setNavDataBootStrap((bitStateMask & COM_BOOTSTRAP_MASK) != 0))
                     // Try to exit the bootstrap mode
                     arDrone.sendATCmd("AT*CONFIG=" + arDrone.getSeq() + ",\"GENERAL:navdata_demo\",\"TRUE\"");
                  
                  
                  // Verify the emergency mode
                  if(arDrone.setEmergency((bitStateMask & COM_EMERGENCY_MASK) != 0) && print == 0)
                     System.out.println("Emergency state!");

                  // Verify if the communication is lost
                  if((bitStateMask & COM_LOST_MASK) != 0)
                     // In this case signal the user
                     System.out.println("Communication lost, reinitialize the network communication!");

                  // Verify the command watchdog in the bit field state
                  else if((bitStateMask & COM_WATCHDOG_MASK) != 0)
                     // Need to exit the watchdog mode
                     arDrone.sendATCmd("AT*COMWDG=" + arDrone.getSeq());
               }
            }
            catch(SocketTimeoutException ex) {
               System.err.println("NavData : Timeout");
               arDrone.setNavDataBootStrap(true);
            }
            catch(IOException ex){
               System.err.println("NavDataReader.run: " + ex);
            }
         }
      }
      catch (IOException ex) {
         System.err.println("NavDataReader.run: " + ex);
      }
   }

   /**
    * Print a formated bitmask with its definition.
    * @param bitStateMask the bitmask to print
    */
   private void printBitMask(int bitStateMask){
      String mask = String.format("%32s",Integer.toBinaryString(bitStateMask)).replaceAll(" ", "0");
      System.out.println(mask.substring(16, 32).replaceAll(".(?!$)", "$0 "));
      System.out.println(
                "| | | | | | | | | | | | | | | FLY MASK : (0) ardrone is landed, (1) ardrone is flying\n"
              + "| | | | | | | | | | | | | | VIDEO MASK : (0) video disable, (1) video enable\n"
              + "| | | | | | | | | | | | | VISION MASK : (0) vision disable, (1) vision enable\n"
              + "| | | | | | | | | | | | CONTROL ALGO : (0) euler angles control, (1) angular speed control\n"
              + "| | | | | | | | | | | ALTITUDE CONTROL ALGO : (0) altitude control inactive (1) altitude control active\n"
              + "| | | | | | | | | | USER feedback : Start button state\n"
              + "| | | | | | | | | Control command ACK : (0) None, (1) one received\n"
              + "| | | | | | | | Trim command ACK : (0) None, (1) one received\n"
              + "| | | | | | | Trim running : (0) none, (1) running\n"
              + "| | | | | | Trim result : (0) failed, (1) succeeded\n"
              + "| | | | | Navdata demo : (0) All navdata, (1) only navdata demo\n"
              + "| | | | Navdata bootstrap : (0) options sent in all or demo mode, (1) no navdata options sent\n"
              + "| | | Motors status : (0) Ok, (1) Motors Com is down\n"
              + "| | \n"
              + "| Bit means that there's an hardware problem with gyrometers\n"
              + "VBat low : (1) too low, (0) Ok\n");
      System.out.println(mask.substring(0, 16).replaceAll(".(?!$)", "$0 "));
      System.out.println(
                "| | | | | | | | | | | | | | | VBat high (US mad) : (1) too high, (0) Ok\n"
              + "| | | | | | | | | | | | | | Timer elapsed : (1) elapsed, (0) not elapsed\n"
              + "| | | | | | | | | | | | | Power : (0) Ok, (1) not enough to fly\n"
              + "| | | | | | | | | | | | Angles : (0) Ok, (1) out of range\n"
              + "| | | | | | | | | | | Wind : (0) Ok, (1) too much to fly\n"
              + "| | | | | | | | | | Ultrasonic sensor : (0) Ok, (1) deaf\n"
              + "| | | | | | | | | Cutout system detection : (0) Not detected, (1) detected\n"
              + "| | | | | | | | PIC Version number OK : (0) a bad version number, (1) version number is OK\n"
              + "| | | | | | | ATCodec thread ON : (0) thread OFF (1) thread ON\n"
              + "| | | | | | Navdata thread ON : (0) thread OFF (1) thread ON\n"
              + "| | | | | Video thread ON : (0) thread OFF (1) thread ON\n"
              + "| | | | Acquisition thread ON : (0) thread OFF (1) thread ON\n"
              + "| | | CTRL watchdog : (1) delay in control execution (> 5ms), (0) control is well scheduled // Check frequency of control loop\n"
              + "| | ADC Watchdog : (1) delay in uart2 dsr (> 5ms), (0) uart2 is good // Check frequency of uart2 dsr (com with adc)\n"
              + "| Communication Watchdog : (1) com problem, (0) Com is ok // Check if we have an active connection with a client\n"
              + "Emergency landing : (0) no emergency, (1) emergency\n");
   }
}
