package madsdf.ardrone.controller;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.Floats;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import madsdf.ardrone.gesture.DTW;
import madsdf.ardrone.gesture.TimeseriesChartFrame;
import madsdf.ardrone.gesture.DetectedMovementFrame;
import madsdf.ardrone.utils.DataFileReader;
import madsdf.ardrone.utils.DataFileReader.Gesture;
import madsdf.ardrone.utils.PropertiesReader;
import madsdf.ardrone.utils.WindowAccumulator;
import madsdf.shimmer.gui.AccelGyro;
import javax.swing.SwingUtilities;
import madsdf.ardrone.utils.KNN;

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
        
        PropertiesReader descReader = new PropertiesReader(sensorDataBasedir + "/" + reader.getString("desc_file"));
        boolean calibrated = descReader.getBoolean("calibrated");
        // MovementsMap : convert from <String, String> to <Integer, String>
        Map<String, String> _movementsMap = descReader.getMap("movements_map");
        Map<Integer, String> movementsMap = Maps.newHashMap();
        for (Entry<String, String> e : _movementsMap.entrySet()) {
            movementsMap.put(Integer.parseInt(e.getKey()), e.getValue());
        }
        
        final DataFileReader freader = new DataFileReader(new FileReader(templates_file));
        List<Gesture> templates = freader.readAll();
         
        DTWGestureController ctrl = new DTWGestureController(actionMask, drone, templates, movementsMap, calibrated);
        ebus.register(ctrl);
        return ctrl;
    }
    
    private Multimap<Integer, Gesture> gestureTemplates = ArrayListMultimap.create();
    private WindowAccumulator accumulator =
            new WindowAccumulator<AccelGyro.Sample>(150, 15);
    
    private TimeseriesChartFrame distFrame;
    private TimeseriesChartFrame stdDevFrame;
    private TimeseriesChartFrame knnFrame;
    private TimeseriesChartFrame detectedFrame;
    
    private static final int KNN_K = 3;
    
    private final boolean calibrated;
    
    private final Map<Integer, String> movementsMap;
    
    public DTWGestureController(ImmutableSet<ActionCommand> actionMask,
                                ARDrone drone, List<Gesture> gestures,
                                final Map<Integer, String> movementsMap,
                                boolean calibrated) {
        super(actionMask, drone);
        this.calibrated = calibrated;
        this.movementsMap = movementsMap;
        for (Gesture g: gestures) {
            gestureTemplates.put(g.command, g);
            // Consistency check
            checkState(movementsMap.containsKey(g.command),
                       "movementsMap must contain mapping for command %d",
                       g.command);
        }
        
        System.out.println("-- DTW Gesture Controller, number of templates per command");
        for (Integer command : gestureTemplates.keySet()) {
            System.out.println("command : " + command + " : " +
                    gestureTemplates.get(command).size());
        }
        
        // Create the user configuration frame
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                DTWGestureController.this.distFrame = new TimeseriesChartFrame(
                        "Distance to gesture templates",
                        "Windows", "DTW distance", movementsMap);
                DTWGestureController.this.distFrame.setVisible(true);
                
                DTWGestureController.this.knnFrame = new TimeseriesChartFrame(
                        "KNN votes",
                        "Windows", "votes", movementsMap);
                DTWGestureController.this.knnFrame.setVisible(true);
                DTWGestureController.this.stdDevFrame = new TimeseriesChartFrame(
                        "Standard deviation",
                        "Windows", "Stddev", ImmutableMap.of(0, "Stddev"));
                DTWGestureController.this.stdDevFrame.setVisible(true);
                
                DTWGestureController.this.detectedFrame = new TimeseriesChartFrame(
                        "Detected gestures",
                        "Windows", "Detected", movementsMap);
                DTWGestureController.this.detectedFrame.setVisible(true);
            }
        });
    }
    
    private static float[][] windowAccelToFloat(
            ArrayList<AccelGyro.Sample> window) {
        float[][] data = new float[3][window.size()];
        for (int i = 0; i < window.size(); ++i) {
            final AccelGyro.Sample sample = window.get(i);
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
    
    public float stddev(float[] arr) {
        float avg = 0;
        for (int i = 0; i < arr.length; ++i) {
            avg += arr[i];
        }
        avg /= arr.length;
        
        float stddev = 0;
        for (int i = 0; i < arr.length; ++i) {
            final float v = arr[i] - avg;
            stddev += v*v;
        }
        return (float) Math.sqrt(stddev);
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
            
            final float dist = Collections.min(dists);
            //final float dist = average(dists);
            cmdDists.put(command, dist);
        }
        distFrame.addToChart(cmdDists);
        
        KNN knn = KNN.classify(KNN_K, windowAccel, gestureTemplates);
        
        //System.out.println(_tmp);
        knnFrame.addToChart(knn.votesPerClass);
        
        float meanStddev = (stddev(windowAccel[0]) + stddev(windowAccel[1])
                + stddev(windowAccel[2])) / 3.0f;
        Map<Integer, Float> chartData = Maps.newHashMap();
        chartData.put(0, meanStddev);
        stdDevFrame.addToChart(chartData);
        
        // Finally, decide if we detected something
        decideGesture(knn, meanStddev);
    }
    
    private void decideGesture(final KNN knn,
                               float stddev) {
        Map<Integer, Float> detections = Maps.newHashMap();
        for (Integer command: gestureTemplates.keySet()) {
            detections.put(command, 0.0f);
        }
        
        /*if (stddev > 2000) {
            // Sort by number of votes
            List<Entry<Integer, Float>> l = Lists.newArrayList(knn.votesPerClass.entrySet());
            final Entry<Integer, Float> nearest = l.get(0);
            final Entry<Integer, Float> secondNearest = l.get(1);
            final float nnratio = secondNearest.getValue() / nearest.getValue();
            
            List<Entry<Integer, Float>> dl = Lists.newArrayList(knn.distPerClass.entrySet());
            final float bestClassAvgDist = dl.get(0).getValue();
            System.out.println("--- decideGesture");
            System.out.println("best class : " + nearest.getKey() + ", avg dist : " + bestClassAvgDist);
            Entry<Float, Gesture> nearestEntry = knn.nearest.get(0);
            System.out.println("nearest : " + nearestEntry.getValue().sample
                    + " dist = " + nearestEntry.getKey());
            System.out.println("nnratio : " + nnratio);
            final int command = nearest.getKey();
            if (nnratio < 0.8 && command < 5) { // Disable rotation and noise commands
                if ((stddev > 4000) || (command == 4 && stddev > 2000)) {
                    detections.put(command, 1.0f);
                }
            }
        }*/
        
        if (stddev > /*2000*/25) {
            List<Entry<Integer, Float>> l = Lists.newArrayList(knn.votesPerClass.entrySet());
            final int bestClass = l.get(0).getKey();
            //System.out.println("bestclass " + bestClass + " nearest : " + knn.getNeighborClass(0));
            
            // Check that nearest neighbor is of majority class
            if (knn.getNeighborClass(0) == bestClass) {
                // Check that nearest neighbor dist is below threshold
                //System.out.println("dist : " + knn.getNeighborDist(0));
                if (knn.getNeighborDist(0) < /*10000*/125) {
                    detections.put(bestClass, 1.0f);
                }
            }
        }
        
        detectedFrame.addToChart(detections);
    }
    
    private void sendToDrone(Map<Integer, Float> detections) {
        // TODO:
        //this.updateDroneAction(ActionCommand.LAND, calibrated);
    }
    
    private void onSample(AccelGyro.Sample sample) {
        ArrayList<AccelGyro.Sample> window = accumulator.add(sample);
        if (window != null) {
            matchWindow(windowAccelToFloat(window));
        }
    }
    
    @Subscribe
    public void sampleReceived(AccelGyro.UncalibratedSample sample) {
        if (!calibrated) {
            onSample(sample);
        }
    }
    
    @Subscribe
    public void sampledReceived(AccelGyro.CalibratedSample sample) {
        if (calibrated) {
            onSample(sample);
        }
    }
}
