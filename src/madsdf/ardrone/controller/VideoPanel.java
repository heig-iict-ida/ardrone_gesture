package madsdf.ardrone.controller;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;

/**
 * This class extend a JPanel and is meant to show the video sent by the drone.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class VideoPanel extends JPanel {

   private static final long serialVersionUID = 1L;
   
   // The showed image from the drone
   private AtomicReference<BufferedImage> image = new AtomicReference<BufferedImage>();
   private AtomicBoolean preserveAspect = new AtomicBoolean(true);
   
   // The showed image when there is no drone connected
   private BufferedImage noConnection = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);

   /**
    * Constructor
    */
   public VideoPanel() {
      Graphics2D g2d = (Graphics2D) noConnection.getGraphics();
      Font f = g2d.getFont().deriveFont(17.0f);
      g2d.setFont(f);
      g2d.drawString("No drone connection", 80, 120);
      image.set(noConnection);
   }

   /**
    * @param preserveAspect the preserveAspect value to set
    */
   public void setPreserveAspect(boolean preserveAspect) {
      this.preserveAspect.set(preserveAspect);
   }

   /**
    * Fired when a new frame is received from the drone.
    * @param startX the X position
    * @param startY the X position
    * @param w the width
    * @param h the height
    * @param rgbArray the picture
    * @param offset the offset
    * @param scansize the scan size (width)
    */
   public void frameReceived(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
      BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      im.setRGB(startX, startY, w, h, rgbArray, offset, scansize);
      image.set(im);
      repaint();
   }

   /**
    * Draw the picture on the panel
    * @param g the graphic object of the panel
    */
   @Override
   public void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D) g;
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int width = getWidth();
      int height = getHeight();
      drawDroneImage(g2d, width, height);
   }

   /**
    * Draw the picture
    * @param g2d the graphic object
    * @param width the width of the picture
    * @param height the height of the picture
    */
   private void drawDroneImage(Graphics2D g2d, int width, int height) {
      BufferedImage im = image.get();
      
      // Verify the picture
      if (im == null) {
         return;
      }
      
      // To preserve the aspect when the from is redimensioned.
      int xPos = 0;
      int yPos = 0;
      if (preserveAspect.get()) {
         g2d.setColor(Color.BLACK);
         g2d.fill3DRect(0, 0, width, height, false);
         float widthUnit = ((float) width / 4.0f);
         float heightAspect = (float) height / widthUnit;
         float heightUnit = ((float) height / 3.0f);
         float widthAspect = (float) width / heightUnit;

         if (widthAspect > 4) {
            xPos = (int) (width - (heightUnit * 4)) / 2;
            width = (int) (heightUnit * 4);
         }
         else if (heightAspect > 3) {
            yPos = (int) (height - (widthUnit * 3)) / 2;
            height = (int) (widthUnit * 3);
         }
      }
      
      // Draw the picture
      if (im != null) {
         g2d.drawImage(im, xPos, yPos, width, height, null);
      }
   }
}
