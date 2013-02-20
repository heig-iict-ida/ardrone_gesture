package madsdf.ardrone.controller.neuralnet;

import com.google.common.eventbus.Subscribe;
import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import madsdf.ardrone.controller.neuralnet.DetectedMovementFrame;
import madsdf.ardrone.controller.neuralnet.MovementModel;
import java.awt.Point;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import layers.OutputLayer;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.controller.DroneController;
import madsdf.ardrone.controller.neuralnet.Features;
import madsdf.ardrone.utils.PropertiesReader;
import madsdf.shimmer.gui.AccelGyro;
import scripting.InterThreadMonitor;
import toolFNNXImportMLP.FNNXImportMLP;
import toolFNNXMLP.FNNXMLP;

/**
 * The neural controller for the gesture control of the drone. This class
 * implement a MovementListener and analyze the movement features with a FENNIX
 * neural network each time a new movement is ready. The neural controller
 * update the drone action map when an ActionCommand is detected by the neural
 * network.
 *
 * Java version : JDK 1.6.0_21 IDE : Netbeans 7.1.1
 *
 * @author Gregoire Aubert
 * @version 1.0
 */
public class NeuralController extends DroneController {
    // Factory method
    public static NeuralController FromProperties (ImmutableSet<ActionCommand> actionMask, ARDrone drone, EventBus ebus, String propFileName) {
        PropertiesReader reader = new PropertiesReader(propFileName);
        
        // Parse the movements and windows size
        final String[] windowsStrings = reader.getString("windows_size").split(";");
        final String[] movementsStrings = reader.getString("movements_size").split(";");
        int[] windowsSize = new int[Math.min(windowsStrings.length, movementsStrings.length)];
        int[] movementSize = new int[Math.min(windowsStrings.length, movementsStrings.length)];
        for (int i = 0; i < windowsSize.length; i++) {
            try {
                windowsSize[i] = Integer.parseInt(windowsStrings[i]);
            } catch (NumberFormatException ex) {
                windowsSize[i] = MovementModel.DEFAULT_WINDOWSIZE;
                System.err.println("ARDrone.createNeuralControllers.windowsSize : " + ex);
            }
            try {
                movementSize[i] = Integer.parseInt(movementsStrings[i]);
            } catch (NumberFormatException ex) {
                movementSize[i] = MovementModel.DEFAULT_MOVEMENTSIZE;
                System.err.println("ARDrone.createNeuralControllers.movementSize : " + ex);
            }
        }

        // Define the movement model class name
        String movementModelClassName = reader.getString("class_name");

        // Parse the features
        Features[] features;
        if (reader.hasKey("features")) {
            final String[] featuresStrings = reader.getString("features").split(";");
            features = new Features[featuresStrings.length];
            for (int i = 0; i < features.length; i++) {
                features[i] = Features.valueOf(featuresStrings[i].toUpperCase());
            }
        } else {
            features = new Features[0];
        }

        // Parse the rest
        int timerMs = reader.getInteger("timer_ms");
        int nbTimerMs = reader.getInteger("nb_timer_ms");
        double errorAccepted = reader.getDouble("error");
        String title = reader.getString("title");
        
        String sensorDataBasedir = reader.getString("sensor_basedir");

        // Retreive the network weight and cmdmap files
        String weightFile = sensorDataBasedir + "/" + reader.getString("weight_file");
        String cmdmapFile = sensorDataBasedir + "/" + reader.getString("cmdmap_file");

        // Load the movement model and create the neural controller
        // Load the movement model class
        Class movementModelClass = null;
        try {
            movementModelClass = ClassLoader.getSystemClassLoader().loadClass(movementModelClassName);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(NeuralController.class.getName()).log(Level.SEVERE, null, ex);
        }
        MovementModel movementModel = null;

        // Create the movement model with the constructor containing the Features list
        try {
            Constructor constructor = movementModelClass.getConstructor(EventBus.class, int[].class, int[].class, Features[].class);
            try {
                movementModel = (MovementModel) constructor.newInstance(ebus, windowsSize, movementSize, features);
            } catch (Exception ex) {
                Logger.getLogger(NeuralController.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (NoSuchMethodException ex) {
            // Will fallback on other constructor
        }

        // If there is no constructor with the Features list create without the features
        if (movementModel == null) {
            try {
                Constructor constructor = movementModelClass.getConstructor(EventBus.class, int[].class, int[].class);
                try {
                    movementModel = (MovementModel) constructor.newInstance(ebus, windowsSize, movementSize);
                } catch (Exception ex) {
                    Logger.getLogger(NeuralController.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (NoSuchMethodException ex) {
                System.err.println("ARDrone.createNeuralControllers.contructor : " + ex);
            }
        }
        
        // Create the neural controller
        NeuralController controller = new NeuralController(
                actionMask, drone, movementModel,
                timerMs, nbTimerMs,
                title,
                errorAccepted,
                weightFile,
                cmdmapFile);
        
        ebus.register(controller);
        return controller;
    }
    
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
    // The accepted error for the network outputs
    private double acceptedError = ERROR_ACCEPT;
    // The user configuration and device selection frame
    private DetectedMovementFrame movementFrame;
    private final String frameTitle;
    private MovementModel movementModel;

    /**
     * Constructor using the weight file name for the neural network
     *
     * @param timeActivated the time between two verification in the
     * NeuralTimeCommand class
     * @param nbTimeactivated the number of timeActivated an ActionCommand last
     * after it detection
     * @param arDrone the controlled drone
     */
    public NeuralController(ImmutableSet<ActionCommand> actionMask, ARDrone drone, MovementModel movementModel, int timeActivated, int nbTimeactivated) {
        this(actionMask, drone, movementModel, timeActivated, nbTimeactivated, "Sensor", ERROR_ACCEPT, MLP_CONFIG_FILE, "");
    }

    /**
     * Constructor
     *
     * @param timeActivated the time between two verification in the
     * NeuralTimeCommand class
     * @param nbTimeactivated the number of timeActivated an ActionCommand last
     * after it detection
     * @param arDrone the controlled drone
     * @param frameTitle the frame title
     * @param framePosition the position of the frame
     * @param acceptedError the accepted error for the network outputs
     * @param weightFile the name of the weight file
     * @param cmdmapFile the name of the cmdmap file
     */
    public NeuralController(ImmutableSet<ActionCommand> actionMask, ARDrone drone, MovementModel movementModel,
                            int timeActivated, int nbTimeactivated, final String frameTitle, double acceptedError, String weightFile, String cmdmapFile) {
        super(actionMask, drone);
        // Set the accepted error
        this.acceptedError = acceptedError;
        this.movementModel = movementModel;

        // Set the weight file
        weightFile = weightFile == null || weightFile.isEmpty() ? MLP_CONFIG_FILE : weightFile;

        // Load the mapping between the action command and the neural network output
        loadCommandMap(cmdmapFile == null || cmdmapFile.isEmpty() ? weightFile + ".cmdmap" : cmdmapFile);

        // Load and initialize the neural network with the weight file
        FNNXImportMLP loader = new FNNXImportMLP(weightFile);
        InterThreadMonitor monitor = loader.getMonitor();
        network = (FNNXMLP) loader.eval(monitor);
        checkNotNull(network);
        checkState(!monitor.hasErrorMessage());

        // Set the class field
        this.frameTitle = frameTitle;

        // Create the action command timer
        this.timeCommand = new NeuralTimeCommand(timeActivated, nbTimeactivated, this);

        // Start the action command timer
        timeCommand.start();

        // Create the user configuration frame
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                NeuralController.this.movementFrame = new DetectedMovementFrame(frameTitle);
            }
        });

    }

    /**
     * Analyze the features of a movement with a neural network.
     *
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
            while (outputs.hasNode()) {
                if (outputs.getOutput() > 1 - acceptedError) {

                    // Verify if there is more than one positive output
                    if (++positive > 1) {
                        return nothingCommand;
                    }

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
     *
     * @param fileName the mapping file name
     * @return true if the mapping was successful and false otherwise
     */
    private boolean loadCommandMap(String fileName) {
        try {
            // Open the file
            FileInputStream fis = new FileInputStream(fileName);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            // Create the map
            commandMap = new HashMap<Integer, ActionCommand[]>();

            // Read the file line by line
            String strLine;
            String[] strSplit;
            int output;
            while ((strLine = br.readLine()) != null) {
                strSplit = strLine.split(":");

                // Parse the line
                if (strSplit.length > 1) {
                    output = Integer.parseInt(strSplit[0]);

                    // There is possibly more than one action for the output
                    strSplit = strSplit[1].split(",");
                    ActionCommand[] actionCmd = new ActionCommand[strSplit.length];
                    for (int i = 0; i < actionCmd.length; i++) {
                        actionCmd[i] = actionFromString(strSplit[i]);
                    }

                    // Add the output and the action command array to the map
                    commandMap.put(output, actionCmd);
                }
            }

            // Close the input stream
            fis.close();
            return true;
        } catch (FileNotFoundException ex) {
            System.err.println("NeuralController.loadCommandMap: " + ex);
        } catch (IOException ex) {
            System.err.println("NeuralController.loadCommandMap: " + ex);
        }
        return false;
    }

    /**
     * Convert a string to its corresponding action command
     *
     * @param s the string to convert
     * @return the corresponding action command
     */
    public ActionCommand actionFromString(String s) {

        // Special treatment for the speed
        if (s.toUpperCase().contains("SPEED")) {

            // Verify if the speed command contain a value
            String[] speed = s.split("=");
            if (speed.length > 1) {
                ActionCommand.SPEED.setVal(Integer.parseInt(speed[1]));
            }
            return ActionCommand.SPEED;
        }

        // Standard treatement for the others command
        try {
            return ActionCommand.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            System.err.println("ActionCommand.fromString: " + ex);
            return ActionCommand.NOTHING;
        }
    }
    
    @Subscribe
    public void sampleReceived(AccelGyro.UncalibratedSample sample) {
        movementModel.addAccelGyroSample(sample);
    }

    /**
     * Is fired each time a new movement is ready.
     *
     * @param features the features of the new movement
     */
    @Subscribe
    public void movementReady(MovementModel.MovementFeatures features) {

        // Analyze the movmement
        ActionCommand[] actCmd = analyzeMovement(features.data);

        // Get the detected movement
        String cmdString = "";
        for (ActionCommand cmd : actCmd) {
            cmdString += cmd.name() + ", ";

            // Update the action map
            this.directUpdateDroneAction(cmd, true);

            // And update the command timer
            timeCommand.updateTimeCmd(cmd);
        }
        final String showedCmd = cmdString.substring(0, cmdString.length() - 2);

        // Execute the EDT
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // Show the command name on the frame
                movementFrame.changeActionCommand(showedCmd);
            }
        });
    }
}
