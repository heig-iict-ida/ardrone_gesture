package madsdf.ardrone.controller;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
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
        
        final DataFileReader freader = new DataFileReader(new FileReader(templates_file));
        List<Gesture> templates = freader.readAll();
         
        DTWGestureController ctrl = new DTWGestureController(actionMask, drone, templates);
        ebus.register(ctrl);
        return ctrl;
    }
    
    private Multimap<Integer, Gesture> gestureTemplates = ArrayListMultimap.create();
    private WindowAccumulator accumulator =
            new WindowAccumulator<AccelGyro.UncalibratedSample>(150, 15);
    
    private TimeseriesChartFrame distFrame;
    private TimeseriesChartFrame stdDevFrame;
    private TimeseriesChartFrame knnFrame;
    private TimeseriesChartFrame detectedFrame;
    
    private static final int KNN_K = 3;
    
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
        final Integer[] gestIds = new Integer[]{1, 2, 3, 4/*, 5, 6*/};
        final String[] gestNames = new String[]{"Avancer", "Droite", "Monter",
            "Descendre"/*, "Rotation", "Bruit"*/};
        
        // Create the user configuration frame
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                DTWGestureController.this.distFrame = new TimeseriesChartFrame(
                        "Distance to gesture templates",
                        "Windows", "DTW distance", gestIds, gestNames);
                DTWGestureController.this.distFrame.setVisible(true);
                
                DTWGestureController.this.knnFrame = new TimeseriesChartFrame(
                        "KNN votes",
                        "Windows", "votes", gestIds, gestNames);
                DTWGestureController.this.knnFrame.setVisible(true);
                
                DTWGestureController.this.stdDevFrame = new TimeseriesChartFrame(
                        "Standard deviation",
                        "Windows", "Stddev", new Integer[]{0}, new String[]{"Stddev"});
                DTWGestureController.this.stdDevFrame.setVisible(true);
                
                DTWGestureController.this.detectedFrame = new TimeseriesChartFrame(
                        "Detected gestures",
                        "Windows", "Detected", gestIds, gestNames);
                DTWGestureController.this.detectedFrame.setVisible(true);
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
    
    // Returns a map of (class, #votes). The class with the highest number of
    // votes should be kept
    /*private Map<Integer, Integer> knnClassify(int k, float[][] windowAccel) {
        // Contains (distance, gesture)
        TreeMap<Float, Gesture> gestureDistances = Maps.newTreeMap();
        for (Gesture g : gestureTemplates.values()) {
            final float dist = DTW.allAxisEuclidean(windowAccel, g.accel);
            //final float dist = DTW.allAxisDTW(windowAccel, g.accel);
            gestureDistances.put(dist, g);
        }
        FluentIterable<Gesture> closest = FluentIterable
                .from(gestureDistances.values())
                .limit(k);
        
        // Aggregate votes per class
        Map<Integer, Integer> votesPerClass = Maps.newHashMap();
        for (Gesture g: closest) {
            Integer v = votesPerClass.get(g.command);
            if (v == null) {
                votesPerClass.put(g.command, 1);
            } else {
                votesPerClass.put(g.command, v + 1);
            }
        }
        return votesPerClass;
    }*/
    
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
        
        if (stddev > 2000) {
            List<Entry<Integer, Float>> l = Lists.newArrayList(knn.votesPerClass.entrySet());
            final int bestClass = l.get(0).getKey();
            //System.out.println("bestclass " + bestClass + " nearest : " + knn.getNeighborClass(0));
            
            // Check that nearest neighbor is of majority class
            if (knn.getNeighborClass(0) == bestClass) {
                // Check that nearest neighbor dist is below threshold
                //System.out.println("dist : " + knn.getNeighborDist(0));
                if (knn.getNeighborDist(0) < 10000) {
                    detections.put(bestClass, 1.0f);
                }
            }
        }
        
        detectedFrame.addToChart(detections);
    }
    
    @Subscribe
    public void sampleReceived(AccelGyro.UncalibratedSample sample) {
        ArrayList<AccelGyro.UncalibratedSample> window = accumulator.add(sample);
        if (window != null) {
            matchWindow(windowAccelToFloat(window));
        }
    }
}
