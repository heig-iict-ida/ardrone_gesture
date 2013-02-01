package madsdf.ardrone.controller;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import madsdf.ardrone.gesture.DTW;
import madsdf.ardrone.gesture.DTWDistanceFrame;
import madsdf.ardrone.gesture.DetectedMovementFrame;
import madsdf.ardrone.utils.DataFileReader;
import madsdf.ardrone.utils.DataFileReader.Gesture;
import madsdf.ardrone.utils.PropertiesReader;
import madsdf.ardrone.utils.WindowAccumulator;
import madsdf.shimmer.gui.AccelGyro;
import javax.swing.SwingUtilities;

/**
 * Controller based on matching incoming measurements with gesture templates
 */
public class DTWGestureController extends DroneController {
    public static DTWGestureController FromProperties(
            ImmutableSet<ActionCommand> actionMask, ARDrone drone,
            EventBus ebus, String configSensor) throws FileNotFoundException, IOException {
        PropertiesReader reader = new PropertiesReader(configSensor);
        checkState(reader.getString("class_name").equals(DTWGestureController.class.getName()));
        
        String sensorDataBasedir = reader.getString("sensor_basedir");
        String templates_file = sensorDataBasedir + "/" + reader.getString("templates_file");
        
        final DataFileReader freader = new DataFileReader(new FileReader(templates_file));
        List<Gesture> templates = freader.readAll();
         
        DTWGestureController ctrl = new DTWGestureController(actionMask, drone, templates);
        ebus.register(ctrl);
        return ctrl;
    }
    
    private Multimap<Integer, Gesture> gestureTemplates = ArrayListMultimap.create();
    private WindowAccumulator accumulator =
            new WindowAccumulator<AccelGyro.UncalibratedSample>(100, 10);
    
    private DTWDistanceFrame distFrame;
    
    public DTWGestureController(ImmutableSet<ActionCommand> actionMask,
                                ARDrone drone, List<Gesture> gestures) {
        super(actionMask, drone);
        for (Gesture g: gestures) {
            gestureTemplates.put(g.command, g);
        }
        
        System.out.println("-- DTW Gesture Controller, number of templates per command");
        for (Integer command : gestureTemplates.keySet()) {
            System.out.println("command : " + command + " : " +
                    gestureTemplates.get(command).size());
        }
        
        // TODO: Hardcoding is bad.. this should be loaded from the properties
        final Integer[] gestIds = new Integer[]{1, 2, 3, 4, 5, 6};
        final String[] gestNames = new String[]{"Avancer", "Droite", "Monter",
            "Descendre", "Rotation", "Bruit"};
        
        // Create the user configuration frame
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                DTWGestureController.this.distFrame = new DTWDistanceFrame(
                        "DTW distance", gestIds, gestNames);
                DTWGestureController.this.distFrame.setVisible(true);
            }
        });
    }
    
    private static float[][] windowAccelToFloat(
            ArrayList<AccelGyro.UncalibratedSample> window) {
        float[][] data = new float[3][window.size()];
        for (int i = 0; i < window.size(); ++i) {
            final AccelGyro.UncalibratedSample sample = window.get(i);
            data[0][i] = sample.accel[0];
            data[1][i] = sample.accel[1];
            data[2][i] = sample.accel[2];
        }
        return data;
    }
    
    public float average(List<Float> lst) {
        float sum = 0;
        for (Float f: lst) {
            sum += f;
        }
        return sum / lst.size();
    }
    
    private void matchWindow(float[][] windowAccel) {
        Map<Integer, Float> cmdDists = Maps.newHashMap();
        for (Integer command: gestureTemplates.keySet()) {
            Collection<Gesture> templates = gestureTemplates.get(command);
            
            ArrayList<Float> dists = Lists.newArrayList();
            for (Gesture g: templates) {
                //dists.add(DTW.allAxisDTW(windowAccel, g.accel));
                dists.add(DTW.allAxisEuclidean(windowAccel, g.accel));
            }
            
            //final float dist = Collections.min(dists);
            final float dist = average(dists);
            cmdDists.put(command, dist);
        }
        distFrame.addToChart(cmdDists);
    }
    
    @Subscribe
    public void sampleReceived(AccelGyro.UncalibratedSample sample) {
        ArrayList<AccelGyro.UncalibratedSample> window = accumulator.add(sample);
        if (window != null) {
            matchWindow(windowAccelToFloat(window));
        }
    }
}
