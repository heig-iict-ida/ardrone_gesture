package madsdf.ardrone.controller;

import madsdf.ardrone.gesture.BluetoothDiscovery;
import madsdf.ardrone.gesture.CaptorSelectionFrame;
import madsdf.ardrone.gesture.Features;
import madsdf.ardrone.gesture.MovementModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicProgressBarUI;

/**
 * ########### Gesture Command of a Quadricopter ###########
 * 
 * The goal of this project is to control an ARDrone Parrot with gesture command.
 * The gesture recognition is made with neural networks and two Shimmer sensors 
 * each containing an accelerometer and a gyroscope.
 * Here is the principal class of the project. It manage the configuration of
 * the drone and create all the thread to communicate with it.
 * This class manage the list of command to send to the drone and maintain
 * the action map. The action map is updated by the controllers, which can be
 * a keyboard controller or a neural controller.
 * The connection with the Shimmer sensors is entirely handled by the neural
 * controllers which depends on it.
 * 
 * ########### Keyboard control mode ###########
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
public class ARDrone extends JFrame implements Runnable {

   private static final long serialVersionUID = 1L;
   
   // ARDrone listening port
   static final int NAVDATA_PORT = 5554;
   static final int VIDEO_PORT = 5555;
   static final int AT_PORT = 5556;
   
   // ARDrone constant
   static final int COM_MAX_LENGTH = 1024;
   
   // NavDataReader offset
   static final int NAVDATA_STATE = 4;
   static final int NAVDATA_BATTERY = 24;
   static final int NAVDATA_ALTITUDE = 40;
   
   // AT*REF values
   static final int AT_REF_TAKEOFF = 290718208;
   static final int AT_REF_LANDING = 290717696;
   static final int AT_REF_EMERGENCY = 290717952;
   static final int AT_REF_RESET = 290717696;
   
   // End line char
   static final String LF = "\r";
   
   // Default time between two commands
   static final int CONFIG_INTERVAL = 30;
   
   // Base configuration
   static final float CONFIG_EULER_MAX = 0.22f;
   static final int CONFIG_VZ_MAX = 1300;
   static final float CONFIG_YAW_MAX = 4.0f;
   static final int CONFIG_ALTITUDE_MAX = 3000;
   static final int CONFIG_ALTITUDE_MIN = 20;
   static final float CONFIG_SPEED = 0.25f;
   static final String CONFIG_DEFAULT_IP = "192.168.1.1";
   static final String CONFIG_DRONE_PROPERTIES = "ardrone.properties";
   static final String CONFIG_MOVEMENT_MODEL = "gesture.SevenFeaturesSelectionMovement";
   
   // Drone ip adresse
   private InetAddress droneAdr;
   
   // Command sequence number
   // Send AT command with sequence number 1 will reset the counter
   private int seq = 1;
   
   // Socket to send command to the drone
   private DatagramSocket atSocket;
   
   // Drone speed
   private float speed = CONFIG_SPEED;
   
   // Time between two commands
   private int cmdInterval = CONFIG_INTERVAL;
   
   // Drone video chanel
   private int videoChannel = 0;
   
   // Drone state
   private FlyingState droneState = FlyingState.LANDED;
   
   // Boolean action map
   private boolean actionTop = false;
   private boolean actionDown = false;
   private boolean actionForward = false;
   private boolean actionBackward = false;
   private boolean actionLeft = false;
   private boolean actionRight = false;
   private boolean actionRotateLeft = false;
   private boolean actionRotateRight = false;
   private boolean actionHovering = false;
   private boolean actionTakeOff = false;
   private boolean actionLanding = false;
   
   // Program state
   private boolean exit = false;
   private boolean navDataBootStrap = false;
   private boolean emergency = false;
   
   // Sending commands thread and command to send list
   Thread commandSender;
   ConcurrentLinkedQueue<String> comList;
   
   // The panel containing the video
   VideoPanel videoPanel;
   
   // The battery progress bar
   JProgressBar batteryLevel;
   
   // The properties objects
   private Properties configDrone;
   private Properties[] configSensors;

   /**
    * Main process, create the drone controller.
    * @param args the command line argument, can be the drone ip address
    */
   public static void main(String args[]){
            
      // Set the Default look and feel
      try {
         boolean nimbus = false;
         for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
               javax.swing.UIManager.setLookAndFeel(info.getClassName());
               nimbus = true;
               break;
            }
         }
         if(!nimbus || System.getProperty("os.name").toLowerCase().indexOf("win") >= 0)
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
      }
      catch (Exception ex) {
         System.err.println("Error look'n feel : " + ex);
      }

      // Set the drone ip adress
      String ip = null;

      // The adress can be set with a command line parameter
      if (args.length >= 1) {
         ip = args[0];
      }
      
      // Create the drone controller
      ARDrone arDrone = new ARDrone(ip);
   }

   /**
    * Constructor of the drone controller
    * @param droneIp the ip address of the drone
    */
   public ARDrone(String droneIp) {
      super();
      
      // Load the properties files
      loadProperties();
      
      // Set the ip
      try {
         if(droneIp == null || droneIp.isEmpty())
            this.droneAdr = InetAddress.getByName(configDrone.getProperty("ip", CONFIG_DEFAULT_IP));
         else
            this.droneAdr = InetAddress.getByName(droneIp);
      }
      catch (UnknownHostException ex) {
         System.err.println("ARDrone.main : " + ex);
      }
      
      // Set the title and create the video panel
      this.setTitle("ARDrone gesture control");
      setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("parrot_icon.png")));
      videoPanel = new VideoPanel();
      
      // Discover the bluetooth devices if needed
      if(configSensors != null && configSensors.length > 0)
         BluetoothDiscovery.getInstance().launchDevicesDiscovery();

      try {

         // Define the socket and the application itself
         atSocket = new DatagramSocket(AT_PORT);

         // Define the frame
         videoPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
         this.getContentPane().add(videoPanel, BorderLayout.CENTER);
         
         // Define the battery progress bar
         batteryLevel = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);
         batteryLevel.setStringPainted(true);
         batteryLevel.setBackground(Color.WHITE);
         batteryLevel.setFocusable(false);
         batteryLevel.setRequestFocusEnabled(false);
         batteryLevel.setPreferredSize(new Dimension(52, 16));
         batteryLevel.setFont(new java.awt.Font("Tahoma", Font.BOLD, 11));
         batteryLevel.setUI(new BasicProgressBarUI() {
            protected Color getSelectionBackground() { return Color.BLACK; }
            protected Color getSelectionForeground() { return Color.BLACK; }
         });
         batteryLevel.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               if(batteryLevel.getValue() > 50)
                  batteryLevel.setForeground(Color.GREEN);
               else if(batteryLevel.getValue() < 15)
                  batteryLevel.setForeground(Color.RED);
               else
                  batteryLevel.setForeground(Color.YELLOW);
            }
         });
         videoPanel.add(batteryLevel);
         setSize(460, 430);
         setVisible(true);
         
         // Define the window closing action
         addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
               exit = true;
               System.exit(0);
            }
         });

         // Create the thread and the list to send the commands
         comList = new ConcurrentLinkedQueue<String>();
         commandSender = new Thread(this);

         // Launch the configuration of the drone
         startConfig();

         // Create the keyboard controller
         new KeyboardController(this);
         
         // Create the neural controllers
         createNeuralControllers();
         
      }
      catch (SocketException ex) {
         System.err.println("ARDrone: " + ex);
      }
   }

   /**
    * Send the command to the drone with an interval of CONFIG_INTERVAL ms
    */
   public void run() {
           
      // Exit when the ARDrone principal thread is closed
      while (!isExit()) {
         
         // Send more than one command in one packet, but less than the max length of a packet
         String cmd = "";
         while (comList.size() > 0 && cmd.length() + comList.peek().length() < COM_MAX_LENGTH) {
            
            // Take the first command of the list
            cmd += comList.poll();
         }

         try {

            // Verify there is a command to send
            if (cmd.length() > 0) {

               // Create the packet
               byte[] buf = cmd.getBytes();
               DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, getDroneAdr(), AT_PORT);

               // And send it
               atSocket.send(sendPacket);
            }

            // Wait between two command
            Thread.sleep(cmdInterval);
         }
         catch (IOException ex) {
            System.err.println("ARDrone.run: " + ex);
         }
         catch (InterruptedException ex) {
            System.err.println("ARDrone.run: " + ex);
         }
      }
   }
   
   /**
    * Load the properties of the drone and the sensors.
    */
   private void loadProperties() {
      
      // Create the drone properties object
      configDrone = new Properties();
      try {
         
         // Load the properties
         configDrone.load(new FileInputStream(CONFIG_DRONE_PROPERTIES));
         
         // Set the timer interval in ms
         if(!configDrone.getProperty("cmd_interval", "").isEmpty())
            cmdInterval = Integer.parseInt(configDrone.getProperty("cmd_interval"));
         
         // Set the start speed
         if(!configDrone.getProperty("speed", "").isEmpty())
            speed = Float.parseFloat(configDrone.getProperty("speed"));
         
         // Verify if the sensors property is set
         if(configDrone.getProperty("sensors", "").isEmpty())
            configSensors = new Properties[0];
         else{
            // Parse the sensors field properties
            String[] sensorFiles = configDrone.getProperty("sensors").split("\",\"");
            configSensors = new Properties[sensorFiles.length];
            
            // Load the sensors properties
            for(int i = 0; i < configSensors.length; i++){
               configSensors[i] = new Properties();
               configSensors[i].load(new FileInputStream(sensorFiles[i].replaceAll("\"", "")));
            }
         }
      }
      catch(FileNotFoundException ex){
         System.err.println("ARDrone.loadProperties: " + ex);
      }
      catch (IOException ex) {
         System.err.println("ARDrone.loadProperties: " + ex);
      }
      
      // Verify that configSensors has been created
      if(configSensors == null)
         configSensors = new Properties[0];
   }

   /**
    * Configure the drone and start the control sender, the navigation data 
    * thread, the video reader thread and the control sender thread.
    */
   private void startConfig() {

      // Launch the command sender thread
      commandSender.start();

      // Initialisation of the drone
      sendATCmd("AT*PMODE=" + getSeq() + ",2");
      sendATCmd("AT*MISC=" + getSeq() + ",2,20,2000,3000");

      // Reset emergency state
      sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);

      // Reset the command watchdog just in case
      sendATCmd("AT*COMWDG=" + getSeq());

      // Set the altitude max
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:altitude_max\",\"" 
               + configDrone.getProperty("altitude_max", CONFIG_ALTITUDE_MAX + "") + "\"");

      // Set the altitude min
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:altitude_min\",\"" 
               + configDrone.getProperty("altitude_min", CONFIG_ALTITUDE_MIN + "") + "\"");

      // Set the control level (0 = no combined yaw mode)
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:control_level\",\"0\"");

      // Tell the drone we are indoor/outdoor
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:outdoor\",\"FALSE\"");

      // Tell the drone it has the shell or not
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:flight_without_shell\",\"FALSE\"");

      // Set the drone max angle
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:euler_angle_max\",\"" 
               + configDrone.getProperty("euler_max", CONFIG_EULER_MAX + "") + "\"");

      // Set the drone max up/down speed
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:control_vz_max\",\"" 
               + configDrone.getProperty("vz_max", CONFIG_VZ_MAX + "") + "\"");

      // Set the drone yaw speed
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"control:control_yaw\",\"" 
               + configDrone.getProperty("yaw_max", CONFIG_YAW_MAX + "") + "\"");

      // Set the navdata demo mode
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"general:navdata_demo\",\"TRUE\"");

      // Set the video stream
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"general:video_enable\",\"TRUE\"");

      // Set the video channel
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"video:video_channel\",\"" + videoChannel + "\"");

      // Set the ultra sound frequence
      //sendATCmd("AT*CONFIG=" + getSeq() + ",\"pic:ultrasound_freq\",\"8\"");

      // Tell the drone it is laying horizontally
      sendATCmd("AT*FTRIM=" + getSeq());

      // Reset emergency state
      sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);

      // Send an hovering command
      sendPCMD(0, 0, 0, 0, 0);

      // Reset emergency state
      sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);

      // Reset emergency state
      sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);
      
      // Wait a little before starting all thread
      try {
         Thread.sleep(cmdInterval * 10);
      }
      catch (InterruptedException ex) {
         System.err.println("ARDrone.startConfig: " + ex);
      }

      // Launch the navadata thread
      NavDataReader navDataThread = new NavDataReader(this);
      navDataThread.start();

      // Launch the video reader
      VideoReader videoReader = new VideoReader(this);
      videoReader.start();

      // Launch the control sender thread
      ControlSender controlThread = new ControlSender(this);
      controlThread.start();

   }
   
   /**
    * Create the neural controllers according to the properties files loaded
    */
   private void createNeuralControllers() {
      
      // Temps string arrays
      String[] windowsStrings;
      String[] movementsStrings;
      String[] featuresStrings;
      
      // Sensor frame position
      Point framePosition = new Point(this.getWidth() + 5, 5);
      
      // For all sensors
      for(Properties configSensor : configSensors){
         
         // Verify the config sensor file
         if(configSensor != null){
         
            // Parse the movements and windows size
            windowsStrings = configSensor.getProperty("windows_size", "").split(";");
            movementsStrings = configSensor.getProperty("movements_size", "").split(";");
            int[] windowsSize = new int[Math.min(windowsStrings.length, movementsStrings.length)];
            int[] movementSize = new int[Math.min(windowsStrings.length, movementsStrings.length)];
            for(int i = 0; i < windowsSize.length; i++){
               try{
                  windowsSize[i] = Integer.parseInt(windowsStrings[i]);
               }catch(NumberFormatException ex){
                  windowsSize[i] = MovementModel.DEFAULT_WINDOWSIZE;
                  System.err.println("ARDrone.createNeuralControllers.windowsSize : " + ex);
               }
               try{
                  movementSize[i] = Integer.parseInt(movementsStrings[i]);
               }catch(NumberFormatException ex){
                  movementSize[i] = MovementModel.DEFAULT_MOVEMENTSIZE;
                  System.err.println("ARDrone.createNeuralControllers.movementSize : " + ex);
               }
            }
            
            // Define the movement model class name
            String movementModelClassName = configSensor.getProperty("class_name", CONFIG_MOVEMENT_MODEL);
            if(movementModelClassName.isEmpty())
               movementModelClassName = CONFIG_MOVEMENT_MODEL;

            // Parse the features
            featuresStrings = configSensor.getProperty("features", "").split(";");
            Features[] features;
            if(configSensor.getProperty("features", "").isEmpty()){
               features = new Features[0];
            }
            else{
               features = new Features[featuresStrings.length];
               for(int i = 0; i < features.length; i++)
                  features[i] = Features.valueOf(featuresStrings[i].toUpperCase());
            }

            // Parse the rest
            int timerMs = NeuralTimeCommand.DEFAULT_TIMER;
            try{
               timerMs = Integer.parseInt(configSensor.getProperty("timer_ms"));
            }catch(NumberFormatException ex){
               System.err.println("ARDrone.createNeuralControllers.timerMs : " + ex);
            }
            int nbTimerMs = NeuralTimeCommand.DEFAULT_NB_TIMER;
            try{
               nbTimerMs = Integer.parseInt(configSensor.getProperty("nb_timer_ms"));
            }catch(NumberFormatException ex){
               System.err.println("ARDrone.createNeuralControllers.nbTimerMs : " + ex);
            }
            double errorAccepted = NeuralController.ERROR_ACCEPT;
            try{
               errorAccepted = Double.parseDouble(configSensor.getProperty("error"));
            }catch(Exception ex){
               System.err.println("ARDrone.createNeuralControllers.errorAccepted : " + ex);
            }
            String title = configSensor.getProperty("title", "Sensor");

            // Retreive the network weight and cmdmap files
            String weightFile = configSensor.getProperty("weight_file", NeuralController.MLP_CONFIG_FILE);
            String cmdmapFile = configSensor.getProperty("cmdmap_file", weightFile + ".cmdmap");
            
            // Load the movement model and create the neural controller
            try{
               
               // Load the movement model class
               Class movementModelClass = ClassLoader.getSystemClassLoader().loadClass(movementModelClassName);
               MovementModel movementModel = null;
               
               // Create the movement model with the constructor containing the Features list
               try{
                  Constructor constructor = movementModelClass.getConstructor(int[].class, int[].class, Features[].class);
                  movementModel = (MovementModel)constructor.newInstance(windowsSize, movementSize, features);
               }catch(NoSuchMethodException ex){
                  System.err.println("ARDrone.createNeuralControllers.contructor : " + ex);
               }
               
               // If there is no constructor with the Features list create without the features
               if(movementModel == null){
                  try{
                     Constructor constructor = movementModelClass.getConstructor(int[].class, int[].class);
                     movementModel = (MovementModel)constructor.newInstance(windowsSize, movementSize);
                  }catch(NoSuchMethodException ex){
                     System.err.println("ARDrone.createNeuralControllers.contructor : " + ex);
                  }
               }
               
               // Create the neural controller
               new NeuralController(
                  movementModel,
                  timerMs, nbTimerMs, this,
                  title,
                  (Point)framePosition.clone(),
                  errorAccepted,
                  weightFile,
                  cmdmapFile);
               
            }catch(Exception e){
               System.err.println("erreur");
            }
            
            // Adapte the coordinate of the next frame
            framePosition.x += 10 + CaptorSelectionFrame.DEFAULT_FRAME_WIDTH;
            if(framePosition.x + CaptorSelectionFrame.DEFAULT_FRAME_WIDTH > this.getToolkit().getScreenSize().width){
               framePosition.x = this.getWidth() + 5;
               framePosition.y += CaptorSelectionFrame.DEFAULT_FRAME_HEIGHT + 10;
            }
         }
         
      }  
   }

   /**
    * Is fired when a new video frame is received by the VideoReader. The
    * video panel is updated with the new picture.
    * @param startX the X position
    * @param startY the X position
    * @param w the width
    * @param h the height
    * @param rgbArray the picture
    * @param offset the offset
    * @param scansize the scan size (width)
    */
   void videoFrameReceived(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
      videoPanel.frameReceived(startX, startY, w, h, rgbArray, offset, scansize);
   }

   /**
    * Send an AT command to the drone, always use this method to send a command.
    * @param cmd the AT command to send without the final "LF"
    */
   public void sendATCmd(String cmd) {
      
      // Add the command to the end of the list of commands to send
      comList.offer(cmd + LF);
   }

   /**
    * Send a movement command to the drone, always use this method to send a
    * movement command. This method use the sendATCmd() method.
    * @param flag 0 for hovering, 1 to make the next parameter useful
    * @param roll the roll degree
    * @param pitch the pitch degree
    * @param gas the gas command (up & down)
    * @param yaw the yaw speed (rotation left & right)
    */
   public void sendPCMD(int flag, float roll, float pitch, float gas, float yaw) {
      sendATCmd("AT*PCMD=" + getSeq() + ","
              + flag + ","
              + Float.floatToIntBits(roll) + ","
              + Float.floatToIntBits(pitch) + ","
              + Float.floatToIntBits(gas) + ","
              + Float.floatToIntBits(yaw));
   }

   /**
    * Make the drone take off if the condition are filled
    */
   public void takeOff() {
            
      // If the drone is already flying cancel the command
      if (getFlyingState() == FlyingState.FLYING || getFlyingState() == FlyingState.LANDING || navDataBootStrap || emergency) {
         actionTakeOff = false;
         
         // If the drone is in emergency state, reset the flag
         if(emergency){
            sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);
            sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_EMERGENCY);
         }
      }
      
      // Otherwise send it
      else {
         sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_TAKEOFF);
         System.out.println("Takeoff");
      }
   }

   /**
    * Make the drone land
    */
   public void land() {
      // If the drone is already landed cancel the status
      if (getFlyingState() == FlyingState.LANDED) {
         actionLanding = false;
      }

      // But keep sending the command, we never know...
      sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_LANDING);
      System.out.println("Landing");
   }

   /**
    * Change the video chanel
    * @param chanel the new chanel to switch to
    */
   private void changeVideoChannel(int chanel) {
      System.out.println("Video channel : " + chanel);
      sendATCmd("AT*CONFIG=" + getSeq() + ",\"video:video_channel\",\"" + chanel + "\"");
   }

   /**
    * Update the map containing the action to send to the drone
    * @param command the ActionCommand to update
    * @param startAction true to send the command and false to stop sending the command
    */
   public void updateActionMap(ActionCommand command, boolean startAction) {

      switch (command) {

         // Speed control
         case SPEED:
            switch (command.getVal()) {
               case 1:
                  setSpeed(0.05, startAction);
                  break;
               case 2:
                  setSpeed(0.1, startAction);
                  break;
               case 3:
                  setSpeed(0.15, startAction);
                  break;
               case 4:
                  setSpeed(0.25, startAction);
                  break;
               case 5:
                  setSpeed(0.35, startAction);
                  break;
               case 6:
                  setSpeed(0.45, startAction);
                  break;
               case 7:
                  setSpeed(0.6, startAction);
                  break;
               case 8:
                  setSpeed(0.8, startAction);
                  break;
               case 9:
                  setSpeed(0.99, startAction);
                  break;
            }
            break;

         // Switch video channel
         case CHANGEVIDEO:
            if (startAction) {
               videoChannel = (videoChannel + 1) % 4;
               changeVideoChannel(videoChannel);
            }
            break;

         // Go up (gas+)
         case GOTOP:
            System.out.println("Go Up (gas+) " + startAction);
            actionTop = startAction;
            break;

         // Go down (gas-)
         case GODOWN:
            System.out.println("Go Down (gas-) " + startAction);
            actionDown = startAction;
            break;

         // Rotate on the right (yaw+)
         case ROTATERIGHT:
            System.out.println("Rotate Right (yaw+) " + startAction);
            actionRotateRight = startAction;
            break;

         // Rotate on the left (yaw-)
         case ROTATELEFT:
            System.out.println("Rotate Left (yaw-) " + startAction);
            actionRotateLeft = startAction;
            break;

         // Go forward (pitch+)
         case GOFORWARD:
            System.out.println("Go Forward (pitch+) " + startAction);
            actionForward = startAction;
            break;

         // Go backward (pitch-)
         case GOBACKWARD:
            System.out.println("Go Backward (pitch-) " + startAction);
            actionBackward = startAction;
            break;

         // Move to the right (roll+)
         case GORIGHT:
            System.out.println("Go Right (roll+) " + startAction);
            actionRight = startAction;
            break;

         // Move to the left (roll-)
         case GOLEFT:
            System.out.println("Go Left (roll-) " + startAction);
            actionLeft = startAction;
            break;

         // Try to take off
         case TAKEOFF:
            if (startAction) {
               actionTakeOff = true;
               takeOff();
            }
            break;

         // Land
         case LAND:
            if (startAction) {
               actionLanding = true;
               actionTakeOff = false;
               land();
            }
            break;

         // Force the drone to hover
         case HOVER:
            System.out.println("Hovering");
            actionHovering = startAction;
            sendPCMD(0, 0, 0, 0, 0);
            break;

         default:
            break;
      }
   }

   /**
    * @return the command sequence number and increase it
    */
   public synchronized int getSeq() {
      return seq++;
   }

   /**
    * @return the application exit state
    */
   public boolean isExit() {
      return exit;
   }

   /**
    * @return the drone flying state
    */
   public FlyingState getFlyingState() {
      return droneState;
   }

   /**
    * @param flyingState set the new flying state
    */
   public void setFlyingState(FlyingState flyingState) {
      this.droneState = flyingState;
   }

   /**
    * @return the drone ip address
    */
   public InetAddress getDroneAdr() {
      return droneAdr;
   }

   /**
    * @param navDataBootStrap the navDataBootStrap to set
    * @return the navDatabootStrap value
    */
   public boolean setNavDataBootStrap(boolean navDataBootStrap) {
      return this.navDataBootStrap = navDataBootStrap;
   }
   
   /**
    * @param emergency the emergency to set
    * @return the emergency field value
    */
   public boolean setEmergency(boolean emergency) {
      return this.emergency = emergency;
   }

   /**
    * @param speed the speed to set
    * @param set true to set the speed and false to ignore it
    */
   public void setSpeed(double speed, boolean set) {
      if (set) {
         this.speed = (float) speed;

         // Print the speed
         System.out.println("Speed: " + getSpeed());
      }
   }
   
   /**
    * @return the actual speed
    */
   public float getSpeed() {
      return speed;
   }

   /**
    * @return the take off action value
    */
   public boolean isActionTakeOff() {
      return actionTakeOff;
   }

   /**
    * @return the landing action value
    */
   public boolean isActionLanding() {
      return actionLanding;
   }

   /**
    * @return the top action value
    */
   public boolean isActionTop() {
      return actionTop;
   }

   /**
    * @return the down action value
    */
   public boolean isActionDown() {
      return actionDown;
   }

   /**
    * @return the forward action value
    */
   public boolean isActionForward() {
      return actionForward;
   }

   /**
    * @return the backward action value
    */
   public boolean isActionBackward() {
      return actionBackward;
   }

   /**
    * @return the left action value
    */
   public boolean isActionLeft() {
      return actionLeft;
   }

   /**
    * @return the right action value
    */
   public boolean isActionRight() {
      return actionRight;
   }

   /**
    * @return the rotate left action value
    */
   public boolean isActionRotateLeft() {
      return actionRotateLeft;
   }

   /**
    * @return the rotate right action value
    */
   public boolean isActionRotateRight() {
      return actionRotateRight;
   }

   /**
    * @return the hovering action value
    */
   public boolean isActionHovering() {
      return actionHovering;
   }
}
