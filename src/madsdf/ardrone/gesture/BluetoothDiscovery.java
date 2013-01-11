package madsdf.ardrone.gesture;

import java.util.LinkedList;
import java.util.List;
import javax.bluetooth.*;

/**
 * Implement the DiscoveryListener to discover the Bluetooth devices and their
 * RFCOMM services.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class BluetoothDiscovery implements DiscoveryListener {

   // Bluetooth devices list
   private List<MyRemoteDevice> devicesDiscovered = new LinkedList<MyRemoteDevice>();
   // Bluetooth RFCOMM services
   private List<String> servicesUrl = new LinkedList<String>();
   // Lock object
   private final Object lock = new Object();
   private static final Object lockInstance = new Object();
   // RFCOMM service ID
   private final String RFCOMM_ID = "1101";
   // Singleton
   private static BluetoothDiscovery instance;

   /**
    * Private constructor
    */
   private BluetoothDiscovery() {
      devicesDiscovered.clear();
   }
   
   /**
    * @return the instance of the BluetoothDiscovery class (singleton)
    */
   public static BluetoothDiscovery getInstance(){
      // First call 
      if (instance == null) {
         synchronized(lockInstance) {
            if (instance == null) {
               instance = new BluetoothDiscovery();
            }
         }
      }
      return instance;
   }
   
   /**
    * Is fired when a new device is discovered
    * @param btDevice the device that was found during the inquiry
    * @param cod the device class
    */
   @Override
   public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
      getDevicesDiscovered().add(new MyRemoteDevice(btDevice));
   }

   /**
    * Is fired when some new services are discovered
    * @param transID the transaction ID identifying the request
    * @param servRecord a list of services found during the search request
    */
   @Override
   public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
      
      // Add the services to the list
      if (servRecord != null && servRecord.length > 0) {
         for (int i = 0; i < servRecord.length; i++) {
            getServicesUrl().add(servRecord[i].getConnectionURL(0, false));
         }
      }
      
      // Notify that the services have been discovered
      synchronized (getLock()) {
         getLock().notify();
      }
   }

   /**
    * Is fired when the services discovery is complete
    * @param transID the transaction ID identifying the request
    * @param respCode the response code that indicates the status of the transaction
    */
   @Override
   public void serviceSearchCompleted(int transID, int respCode) {
      // Notify all the thread waiting
      synchronized (getLock()) {
         getLock().notifyAll();
      }
   }

   /**
    * Is fired when the devices discovery is complete
    * @param discType the type of request that was completed
    */
   @Override
   public void inquiryCompleted(int discType) {
      // Notify all the thread waiting
      synchronized (getLock()) {
         getLock().notifyAll();
      }
   }
   
   /**
    * Start the discovery of the devices
    * @return the list of the discovered devices
    */
   public List<MyRemoteDevice> launchDevicesDiscovery() {
      // Clear the devices list
      devicesDiscovered.clear();
      synchronized (lock) {
         try {
            boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, this);
            
            // Wait for the inquery to finish
            if (started) {
               lock.wait();
            }
         }
         catch (BluetoothStateException ex) {
            System.err.println("BluetoothDiscovery.launchDevicesDiscovery : " + ex);
         }
         catch (InterruptedException ex){
            System.err.println("BluetoothDiscovery.launchDevicesDiscovery : " + ex);
         }
      }
      return devicesDiscovered;
   }
   
   /**
    * Start the discovery of the services for a given device
    * @param remoteDevice is the device to discover the services
    * @return the list of the discovered services
    */
   public List<String> launchServicesDiscovery(RemoteDevice remoteDevice){
      
      // Clear services the list
      servicesUrl.clear();
      
      UUID[] uuidSet = new UUID[1];
      uuidSet[0] = new UUID(RFCOMM_ID, true);
      
      try {
         LocalDevice.getLocalDevice().getDiscoveryAgent().searchServices(null, uuidSet, remoteDevice, this);
         
         // Wait for the service discovery to finish
         synchronized (lock) {
            lock.wait();
         }
      }
      catch (BluetoothStateException ex) {
         System.err.println("BluetoothDiscovery.launchServicesDiscovery : " + ex);
      }
      catch (InterruptedException ex) {
         System.err.println("BluetoothDiscovery.launchServicesDiscovery : " + ex);
      }
      return servicesUrl;
   }

   /**
    * @return the list of the discovered devices
    */
   public List<MyRemoteDevice> getDevicesDiscovered() {
      return devicesDiscovered;
   }

   /**
    * @return the list of the services Url
    */
   public List<String> getServicesUrl() {
      return servicesUrl;
   }

   /**
    * @return the lock
    */
   public Object getLock() {
      return lock;
   }
}
