package gesture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.swing.event.EventListenerList;

/**
 * Receive the informations from the device and transmit them to the 
 * MovementListener
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class BluetoothDeviceCom implements Runnable {

   // Constants for the command we can send to the shimmer
   public final byte START_STREAMING_COMMAND    = 0x07;
   public final byte STOP_STREAMING_COMMAND     = 0x20;
   public final byte TOGGLE_LED_COMMAND         = 0x06;
   public final byte SET_SAMPLING_RATE_COMMAND  = 0x05;
   public final byte SET_SENSORS_COMMAND        = 0x08;
   public final byte SET_ACCEL_RANGE_COMMAND    = 0x09;
   public final byte INQUIRY_COMMAND            = 0x01;
   
   public final byte SENSOR_ACCEL   = (byte)0x80;
   public final byte SENSOR_GYRO    = 0x40;
   
   public final byte SAMPLING_1000HZ   = 0x01;
   public final byte SAMPLING_500HZ    = 0x02;
   public final byte SAMPLING_250HZ    = 0x04;
   public final byte SAMPLING_200HZ    = 0x05;
   public final byte SAMPLING_166HZ    = 0x06;
   public final byte SAMPLING_125HZ    = 0x08;
   public final byte SAMPLING_100HZ    = 0x0A;
   public final byte SAMPLING_50HZ     = 0x14;
   public final byte SAMPLING_10HZ     = 0x64;
   public final byte SAMPLING_0HZ_OFF  = (byte)0xFF;
   
   public final byte RANGE_1_5G  = 0x0;
   public final byte RANGE_2_0G  = 0x1;
   public final byte RANGE_4_0G  = 0x2;
   public final byte RANGE_6_0G  = 0x3;
   
   // Sample size
   public final int SAMPLE_SIZE = 15;
   
   // Connection to the device
   private StreamConnection bluetoothConnection;
   private OutputStream os;
   private InputStream is;
   private Thread thread;
   private boolean continueRead = true;
   
   // List of all MovementListener
   private final EventListenerList listeners = new EventListenerList();

   /**
    * Constructor
    * @param connectionURL is the service on the device
    * @param listener is the listener for the new AccelGyroSample arriving
    */
   public BluetoothDeviceCom(String connectionURL, MovementListener listener) {
      try {
         // Connection to the bluetooth device
         bluetoothConnection = (StreamConnection) Connector.open(connectionURL);
         os = bluetoothConnection.openOutputStream();
         is = bluetoothConnection.openInputStream();
         
         // Add the listener
         addMovementListener(listener);
         
         // Start the thread
         thread = new Thread(this);
         thread.start();
      }
      catch (IOException ex) {
         System.err.print("BluetoothDeviceCom : " + ex);
      }
   }

   @Override
   public void run() {
      try {
         
         byte[] packetReceive = new byte[240];
         
         // Activate the accelerometer and the gyroscope
         byte[] activeSensor = new byte[]{SET_SENSORS_COMMAND, SENSOR_GYRO | SENSOR_ACCEL, 0x00};
         os.write(activeSensor);
         is.read(packetReceive);

         // Define the frequency
         byte[] setFrequency = new byte[]{SET_SAMPLING_RATE_COMMAND, SAMPLING_100HZ};
         os.write(setFrequency);
         is.read(packetReceive);

         // Starts the data streaming
         os.write(START_STREAMING_COMMAND);
         is.read(packetReceive);
         
         // Starts reading data
         int bytesRead;
         byte[] sample = new byte[SAMPLE_SIZE];
         int current = 0;
         while (continueRead) {

            // Read the received packet, can contain multiple sample, and 
            // the last can be incomplete
            bytesRead = is.read(packetReceive);

            for(int i = 0; i < bytesRead; i++){

               // Complete the existing sample
               sample[current++] = packetReceive[i];

               // If the sample is complete it is passed to the display
               if(current == SAMPLE_SIZE){
                  try{
                     fireSampleReceived(new AccelGyroSample(sample));
                  }catch(Exception ex){
                     // Ignore the sample if it is malformed
                  }
                  current = 0;
               }
            }
         }
      }
      catch (IOException ex) {
         System.err.print("BluetoothDeviceCom.run : " + ex);
      }
   }

   /**
    * Stop the current thread and close the communication
    */
   public void stop() {
      continueRead = false;
      try{
         os.write(STOP_STREAMING_COMMAND);
         os.close();
         bluetoothConnection.close();
      }
      catch(IOException ex){
         System.err.print("BluetoothDeviceCom.stop : " + ex);
      }
   }
   
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
    * Fire the sampleReceived method from all the listeners.
    * @param sample the sample to pass to the listener
    */
   protected void fireSampleReceived(AccelGyroSample sample) {  
      for(MovementListener listener : getMovementListeners()) {
         listener.sampleReceived(sample);
      }
   }
}
