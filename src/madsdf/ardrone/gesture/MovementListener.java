package madsdf.ardrone.gesture;

import java.util.EventListener;

/**
 * Interface for the movement listener.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public interface MovementListener extends EventListener {
   
   /**
    * Is fired when BluetoothDeviceCom receive a new sample
    * @param sample the sample BluetoothDeviceCom just received
    */
   public void sampleReceived(AccelGyroSample sample);
   
   /**
    * Is fired when a MovementModel has a new movement ready and it features are
    * calculated.
    * @param features the features of the new movement
    */
   public void movementReady(float[] features);
}
