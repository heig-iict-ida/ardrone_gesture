package controller;

import gesture.AccelGyroSample;
import gesture.CaptorSelectionFrame;
import gesture.MovementListener;
import gesture.MovementModel;
import java.awt.Point;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import layers.OutputLayer;
import scripting.InterThreadMonitor;
import toolFNNXImportMLP.FNNXImportMLP;
import toolFNNXMLP.FNNXMLP;

/**
 * The neural controller for the gesture control of the drone.
 * This class implement a MovementListener and analyze the movement features
 * with a FENNIX neural network each time a new movement is ready.
 * The neural controller update the drone action map when an ActionCommand is 
 * detected by the neural network.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class NeuralController implements MovementListener {

   // Default weight file for the neural network
   static final String MLP_CONFIG_FILE = "network.fxr";
   
   // Default accepted error
   static final double ERROR_ACCEPT = 0.2;
   
   // The mappage between the output of the neural network and the ActionCommand
   private Map<Integer, ActionCommand[]> commandMap;
   
   // The neural network
   private FNNXMLP network;
   private boolean networkLoaded = true;
   
   // The timer for the ActionCommand detected
   private NeuralTimeCommand timeCommand;
   
   // The listened movement model 
   private MovementModel movement;
   
   // The accepted error for the network outputs
   private double acceptedError = ERROR_ACCEPT;
   
   // The controlled drone
   private ARDrone arDrone;
   
   // The user configuration and device selection frame
   private CaptorSelectionFrame captorFrame;
   private final String frameTitle;
   private final Point framePosition;

   /**
    * Constructor using the weight file name for the neural network
    * @param movement the listened movement model
    * @param timeActivated the time between two verification in the NeuralTimeCommand class
    * @param nbTimeactivated the number of timeActivated an ActionCommand last after it detection
    * @param arDrone the controlled drone
    */
   public NeuralController(MovementModel movement, int timeActivated, int nbTimeactivated, ARDrone arDrone) {
      this(movement, timeActivated, nbTimeactivated, arDrone, "Sensor", null, ERROR_ACCEPT, MLP_CONFIG_FILE, "");
   }

   /**
    * Constructor
    * @param movement the listened movement model
    * @param timeActivated the time between two verification in the NeuralTimeCommand class
    * @param nbTimeactivated the number of timeActivated an ActionCommand last after it detection
    * @param arDrone the controlled drone
    * @param frameTitle the frame title
    * @param framePosition the position of the frame
    * @param acceptedError the accepted error for the network outputs
    * @param weightFile the name of the weight file
    * @param cmdmapFile the name of the cmdmap file
    */
   public NeuralController(MovementModel movement, int timeActivated, int nbTimeactivated, ARDrone arDrone, String frameTitle, Point framePosition, double acceptedError, String weightFile, String cmdmapFile) {
      
      // Set the accepted error
      this.acceptedError = acceptedError;
      
      // Set the weight file
      weightFile = weightFile == null || weightFile.isEmpty() ? MLP_CONFIG_FILE : weightFile;
      
      // Load the mapping between the action command and the neural network output
      loadCommandMap(cmdmapFile == null || cmdmapFile.isEmpty()? weightFile + ".cmdmap" : cmdmapFile);

      // Load and initialize the neural network with the weight file
      FNNXImportMLP loader = new FNNXImportMLP(weightFile);
      InterThreadMonitor monitor = loader.getMonitor();
      network = (FNNXMLP) loader.eval(monitor);
      
      // Set the class field
      this.frameTitle = frameTitle;
      this.framePosition = framePosition;
      this.arDrone = arDrone;
      this.movement = movement;
      
      // Register as a movement listener
      this.movement.addMovementListener(this);
      
      // Create the action command timer
      this.timeCommand = new NeuralTimeCommand(timeActivated, nbTimeactivated, arDrone);

      // Verify the network is loaded
      if ((network == null) || (monitor.hasErrorMessage())) {
         networkLoaded = false;
         System.out.println("Error. Impossible to load neural network.");
         System.out.println(monitor.getErrorMessage());
      }
      else{
         
         // Start the action command timer
         timeCommand.start();
         
         // Create the user configuration frame
         java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
               NeuralController.this.captorFrame = new CaptorSelectionFrame(NeuralController.this);
            }
         });
      }
   }
   
   /**
    * Analyze the features of a movement with a neural network.
    * @param features the features to analyze
    * @return the ActionCommand detected by the neural network
    */
   private ActionCommand[] analyzeMovement(float[] features) {
      
      // The command detected by the network
      ActionCommand nothingCommand[] = {ActionCommand.NOTHING};
      ActionCommand detectedCommand[] = nothingCommand;
      
      // Verify that the network has been loaded
      if (networkLoaded) {

         // Define the input of the network
         network.setCurrentInput(features);
         
         // Evaluate the input
         network.feedForward();
         
         // Get the last layer of the network
         OutputLayer outputs = network.getLastLayer();
         
         // Move to the first output
         outputs.head();
         
         // Find the corresponding command by going through all output
         int nodeCount = 1;
         int positive = 0;
         while(outputs.hasNode()){
            if(outputs.getOutput() > 1 - acceptedError){
               
               // Verify if there is more than one positive output
               if(++positive > 1)
                  return nothingCommand;
               
               detectedCommand = commandMap.get(nodeCount);
            }
            nodeCount++;
            outputs.next();
         }
      }
      
      return detectedCommand;
   }

   /**
    * Load the mapping file and create the mapping between the neural network
    * output and the action command.
    * @param fileName the mapping file name
    * @return true if the mapping was successful and false otherwise
    */
   private boolean loadCommandMap(String fileName) {
      try{
         // Open the file
         FileInputStream fis = new FileInputStream(fileName);
         BufferedReader br = new BufferedReader(new InputStreamReader(fis));
         
         // Create the map
         commandMap = new HashMap<Integer, ActionCommand[]>();
         
         // Read the file line by line
         String strLine;
         String[] strSplit;
         int output;
         while ((strLine = br.readLine()) != null){
            strSplit = strLine.split(":");
            
            // Parse the line
            if(strSplit.length > 1){
               output = Integer.parseInt(strSplit[0]);
               
               // There is possibly more than one action for the output
               strSplit = strSplit[1].split(",");
               ActionCommand[] actionCmd = new ActionCommand[strSplit.length];
               for(int i = 0; i< actionCmd.length; i++)
                  actionCmd[i] = actionFromString(strSplit[i]);
               
               // Add the output and the action command array to the map
               commandMap.put(output, actionCmd);
            }
         }
         
         // Close the input stream
         fis.close();
         return true;
      }catch(FileNotFoundException ex){
         System.err.println("NeuralController.loadCommandMap: " + ex);
      }catch(IOException ex){
         System.err.println("NeuralController.loadCommandMap: " + ex);
      }
      return false;
   }
   
   /**
    * Convert a string to its corresponding action command
    * @param s the string to convert
    * @return the corresponding action command
    */
   public ActionCommand actionFromString(String s){
      
      // Special treatment for the speed
      if(s.toUpperCase().contains("SPEED")){
         
         // Verify if the speed command contain a value
         String[] speed = s.split("=");
         if(speed.length > 1)
            ActionCommand.SPEED.setVal(Integer.parseInt(speed[1]));
         return ActionCommand.SPEED;
      }
      
      // Standard treatement for the others command
      try{
         return ActionCommand.valueOf(s.toUpperCase());
      }catch(IllegalArgumentException ex){
         System.err.println("ActionCommand.fromString: " + ex);
         return ActionCommand.NOTHING;
      }
   }

   /**
    * Implementation of the MovementListener method. Is fired each time a new
    * sample is received from the Bluetooth device.
    * @param sample the sample received
    */
   @Override
   public void sampleReceived(AccelGyroSample sample) {
      movement.addAccelGyroSample(sample);
   }

   /**
    * Implementation of the MovementListener method. Is fired each time a new
    * movement is ready.
    * @param features the features of the new movement
    */
   @Override
   public void movementReady(float[] features) {
     
      // Analyze the movmement
      ActionCommand[] actCmd = analyzeMovement(features);
      
      // Get the detected movement
      String cmdString = "";
      for(ActionCommand cmd : actCmd){
         cmdString += cmd.name() + ", ";
         
         // Update the action map
         arDrone.updateActionMap(cmd, true);
         
         // And update the command timer
         timeCommand.updateTimeCmd(cmd);
      }
      final String showedCmd = cmdString.substring(0, cmdString.length()-2);
      
      // Execute the EDT
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            // Show the command name on the frame
            captorFrame.changeActionCommand(showedCmd);
         }
      });
   }

   /**
    * @return the frameTitle
    */
   public String getFrameTitle() {
      return frameTitle;
   }
   
   /**
    * @return the framePosition
    */
   public Point getFramePosition() {
      return framePosition;
   }
}
