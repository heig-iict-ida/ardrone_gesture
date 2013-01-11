package controller;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import video.BufferedVideoImage;

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

   // The drone sending the video
   private ARDrone myARDrone;
   
   // The connection socket
   private DatagramSocket videoSocket;

   /**
    * Constructor
    * @param myARDrone 
    */
   public VideoReader(ARDrone myARDrone) {
      this.myARDrone = myARDrone;
      
      // Connect and configure the socket
      try {
         videoSocket = new DatagramSocket(ARDrone.VIDEO_PORT);
         videoSocket.setSoTimeout(2000);
      }
      catch (SocketException ex) {
         System.err.println("VideoReader: " + ex);
      }
   }

   @Override
   public void run() {
      try {

         // Send the trigger flag to the drone udp port to start the video stream
         byte[] buffer = {0x01, 0x00, 0x00, 0x00};
         DatagramPacket packet = new DatagramPacket(buffer, buffer.length, myARDrone.getDroneAdr(), ARDrone.VIDEO_PORT);
         videoSocket.send(packet);
         myARDrone.sendATCmd("AT*CONFIG=" + myARDrone.getSeq() + ",\"general:video_enable\",\"TRUE\"");

         // Stock the received data
         byte[] videoBuf = new byte[64000];
         DatagramPacket videoPacket = new DatagramPacket(videoBuf, videoBuf.length);

         while (true) {
            try {
               // Receive the video packet
               videoSocket.receive(videoPacket);
               ByteBuffer videoByteBuf = ByteBuffer.wrap(videoPacket.getData());
               
               // Convert the packet in a picture
               BufferedVideoImage video = new BufferedVideoImage();
               video.addImageStream(videoByteBuf);
               
               // Add the picture to the video frame
               myARDrone.videoFrameReceived(0, 0, 
                                            video.getWidth(),
                                            video.getHeight(),
                                            video.getJavaPixelData(), 0,
                                            video.getWidth());
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
   }
}
