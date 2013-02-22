package madsdf.ardrone;

import com.google.common.eventbus.EventBus;
import madsdf.ardrone.ARDrone;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import madsdf.ardrone.video.BufferedVideoImage;

/**
 * Receive the video stream from the drone and display it on the VideoPanel.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class VideoReader extends Thread {
    public static class VideoFrameEvent {
        public final int startX;
        public final int startY;
        public final int w;
        public final int h;
        public final int[] rgbArray;
        public final int offset;
        public final int scansize;
        
        public VideoFrameEvent(int startX, int startY, int w, int h,
                int[] rgbArray, int offset, int scansize) {
            this.startX = startX;
            this.startY = startY;
            this.w = w;
            this.h = h;
            this.rgbArray = rgbArray;
            this.offset = offset;
            this.scansize = scansize;
        }
    }

   // The drone sending the video
   private DroneClient drone;
   
   // The connection socket
   private DatagramSocket videoSocket;
   
   // Bus on which events are sent
   private final EventBus ebus;

   /**
    * Constructor
    * @param myARDrone 
    */
   public VideoReader(DroneClient myARDrone, EventBus ebus) {
      this.drone = myARDrone;
      this.ebus = ebus;
      
      // Connect and configure the socket
      try {
         videoSocket = new DatagramSocket(DroneClient.VIDEO_PORT);
         videoSocket.setSoTimeout(2000);
      }
      catch (SocketException ex) {
         System.err.println("VideoReader: " + ex);
      }
   }
   
    void disconnect() {
        videoSocket.close();
    }

   @Override
   public void run() {
      try {

         // Send the trigger flag to the drone udp port to start the video stream
         byte[] buffer = {0x01, 0x00, 0x00, 0x00};
         DatagramPacket packet = new DatagramPacket(buffer, buffer.length, drone.getDroneAddress(), DroneClient.VIDEO_PORT);
         videoSocket.send(packet);
         drone.sendATCmd("AT*CONFIG=" + drone.incrSeq() + ",\"general:video_enable\",\"TRUE\"");

         // Stock the received data
         byte[] videoBuf = new byte[64000];
         DatagramPacket videoPacket = new DatagramPacket(videoBuf, videoBuf.length);

         while (!videoSocket.isClosed()) {
            try {
               // Receive the video packet
               videoSocket.receive(videoPacket);
               ByteBuffer videoByteBuf = ByteBuffer.wrap(videoPacket.getData());
               
               // Convert the packet in a picture
               BufferedVideoImage video = new BufferedVideoImage();
               video.addImageStream(videoByteBuf);
               
               ebus.post(new VideoFrameEvent(0, 0, video.getWidth(),
                                            video.getHeight(),
                                            video.getJavaPixelData(), 0,
                                            video.getWidth()));
               //System.out.println("Video Received: " + videoPacket.getLength() + " bytes");
            }
            catch (SocketTimeoutException ex) {
               System.err.println("VideoReader.run: " + ex);
               videoSocket.send(packet);
            }
            catch (IOException ex) {
               System.err.println("VideoReader.run: " + ex);
            }
         }
      }
      catch (IOException ex) {
         System.err.println("VideoReader.run: " + ex);
      }
      System.out.println("videoSocket closed, terminating VideoReader thread");
   }
}
