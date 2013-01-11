package gesture;

import java.util.LinkedList;

/**
 * Representation of a data packet received form the Bluetooth device
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class AccelGyroSample {

   // Packet type
   public final byte DATA_TYPE   = 0x00;
   
   // List of converted value from the data buffer
   private LinkedList<Integer> val = new LinkedList<Integer>();

   /**
    * Constructor
    * @param buffer is the data to convert
    * @throws Exception if the buffer is malformed
    */
   public AccelGyroSample(byte[] buffer) throws Exception {
      
      // Verify that the packet is really a sample
      if(buffer[0] == DATA_TYPE){
      
         byte[] twoBytes = new byte[2];
         for (int i = 1; i < buffer.length; i = i + 2) {

            // Conversion, but warning to little endian
            twoBytes[1] = buffer[i];
            twoBytes[0] = buffer[i+1];
            val.add(uint8ToInt(twoBytes));
         }
      }
      else{
         System.err.println("AccelGyroSample, bad data : " 
                              + getHexString(buffer, buffer.length));
         throw new Exception("Bad data format");
      }
   }   
   
   /**
    * Retrieve a value at the index position.
    * @param index is the position of the value retrieved
    * @return the value at the index position
    */
   public Integer getVal(Integer index) {
      return val.get(index);
   }
   
   /**
    * Convert an unsigned 2 bytes integer in an Integer
    * @param b is an array of 2 bytes
    * @return the integer representation
    */
   public static int uint8ToInt(byte[] b) {
      int val = 0;
      int shift = 8;
      for (int i = 0; i < b.length && i < 2; i++) {
         val += (b[i] & 0x000000FF) << shift;
         shift -= 8;
      }
      return val;
   }
   
   /**
    * Convert an array of bytes in their hexadecimal string representation
    * @param b is the bytes array
    * @param a is the number of byte to convert
    * @return the hexadecimal string representation
    */
   public static String getHexString(byte[] b, int a){
      
      String result = "";
      try{
         for (int i=0; i < b.length && i < a; i++) {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
         }
      }catch(Exception ex){
         System.err.println("AccelGyroSample.getHexString : " + ex);
      }
      return result.toUpperCase();
   }
}
