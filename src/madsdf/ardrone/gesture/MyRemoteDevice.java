package madsdf.ardrone.gesture;

import java.io.IOException;
import javax.bluetooth.RemoteDevice;

/**
 * RemoteDevice with a string representation.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class MyRemoteDevice{
   
   // The Bluetooth device
   private RemoteDevice remoteDevice;
   
   /**
    * Constructor
    * @param remoteDevice the discovered device
    */
   public MyRemoteDevice(RemoteDevice remoteDevice){
      this.remoteDevice = remoteDevice;
   }
   
   /**
    * @return the represented device name
    */
   @Override
   public String toString() {
      if(getRemoteDevice() == null)
         return "";
      
      try {
         return getRemoteDevice().getFriendlyName(false);
      }
      catch (IOException ex) {
         return getRemoteDevice().toString();
      }
   }

   /**
    * @return the represented remote device
    */
   public RemoteDevice getRemoteDevice() {
      return remoteDevice;
   }
   
}
