package madsdf.ardrone.controller.templates;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
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
import java.util.Map.Entry;
import madsdf.ardrone.utils.DataFileReader;
import madsdf.ardrone.utils.DataFileReader.Gesture;
import madsdf.ardrone.utils.PropertiesReader;
import madsdf.ardrone.utils.WindowAccumulator;
import madsdf.shimmer.gui.AccelGyro;
import javax.swing.SwingUtilities;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.controller.DroneController;

/**
 * Controller based on matching incoming measurements with gesture templates
 */
public class KNNGestureController extends DroneController {
    public static class GestureTemplate {
        public final ActionCommand command;
        public final Gesture gesture;
        public GestureTemplate(ActionCommand cmd, Gesture g) {
            this.command = cmd;
            this.gesture = g;
        }
    }
    
    public static KNNGestureController FromProperties(
            ImmutableSet<ActionCommand> actionMask, ARDrone drone,
            EventBus ebus, String configSensor) throws FileNotFoundException, IOException {
        PropertiesReader reader = new PropertiesReader(configSensor);
        checkState(reader.getString("class_name").equals(KNNGestureController.class.getName()));
        
        String sensorDataBasedir = reader.getString("sensor_basedir");
        String templates_file = sensorDataBasedir + "/" + reader.getString("templates_file");
        
        PropertiesReader descReader = new PropertiesReader(sensorDataBasedir + "/" + reader.getString("desc_file"));
        boolean calibrated = descReader.getBoolean("calibrated");
        // MovementsMap : convert from <String, String> to <Integer, String>
        Map<String, String> _movementsMap = descReader.getMap("movements_map");
        Map<Integer, ActionCommand> movementsMap = Maps.newHashMap();
        for (Entry<String, String> e : _movementsMap.entrySet()) {
            final ActionCommand a = ActionCommand.valueOf(e.getValue());
            movementsMap.put(Integer.parseInt(e.getKey()), a);
        }
        
        final DataFileReader freader = new DataFileReader(new FileReader(templates_file));
        List<Gesture> gestures = freader.readAll();
        List<GestureTemplate> templates = Lists.newArrayList(); 
        for (Gesture g : gestures) {
            final ActionCommand cmd = movementsMap.get(g.command);
            templates.add(new GestureTemplate(cmd, g));
        }
         
        KNNGestureController ctrl = new KNNGestureController(actionMask, drone, templates, calibrated);
        ebus.register(ctrl);
        return ctrl;
    }
    
    private Multimap<ActionCommand, GestureTemplate> gestureTemplates = ArrayListMultimap.create();
    private WindowAccumulator accumulator =
            new WindowAccumulator<AccelGyro.Sample>(150, 15);
    
    private TimeseriesChartFrame distFrame;
    private TimeseriesChartFrame stdDevFrame;
    private TimeseriesChartFrame knnFrame;
    private TimeseriesChartFrame detectedFrame;
    
    private static final int KNN_K = 3;
    
    private final boolean calibrated;
    
    public KNNGestureController(ImmutableSet<ActionCommand> actionMask,
                                ARDrone drone, List<GestureTemplate> templates,
                                boolean calibrated) {
        super(actionMask, drone);
        this.calibrated = calibrated;
        
        for (GestureTemplate g: templates) {
            gestureTemplates.put(g.command, g);
        }
        
        System.out.println("-- DTW Gesture Controller, number of templates per command");
        for (ActionCommand command : gestureTemplates.keySet()) {
            System.out.println("command : " + command + " : " +
                    gestureTemplates.get(command).size());
        }
        
        // Create the user configuration frame
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                ImmutableSortedMap.Builder<Integer, String> b = ImmutableSortedMap.naturalOrder();
                for (ActionCommand a : gestureTemplates.keySet()) {
                    b.put(a.ordinal(), a.name());
                }
                ImmutableSortedMap<Integer, String> commandIDToName = b.build();
                KNNGestureController.this.distFrame = new TimeseriesChartFrame(
                        "Distance to gesture templates",
                        "Windows", "DTW distance", commandIDToName);
                KNNGestureController.this.distFrame.setVisible(true);
                
                KNNGestureController.this.knnFrame = new TimeseriesChartFrame(
                        "KNN votes",
                        "Windows", "votes", commandIDToName);
                KNNGestureController.this.knnFrame.setVisible(true);
                KNNGestureController.this.stdDevFrame = new TimeseriesChartFrame(
                        "Standard deviation",
                        "Windows", "Stddev", ImmutableSortedMap.of(0, "Stddev"));
                KNNGestureController.this.stdDevFrame.setVisible(true);
                
                KNNGestureController.this.detectedFrame = new TimeseriesChartFrame(
                        "Detected gestures",
                        "Windows", "Detected", commandIDToName);
                KNNGestureController.this.detectedFrame.setVisible(true);
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
    
    public float average(Collection<Float> col) {
        float sum = 0;
        for (Float f: col) {
            sum += f;
        }
        return sum / col.size();
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
    
    public Map<Integer, Float> toIntegerMap(Map<ActionCommand, Float> m) {
        Map<Integer, Float> outM = Maps.newHashMap();
        for (Entry<ActionCommand, Float> e : m.entrySet()) {
            outM.put(e.getKey().ordinal(), e.getValue());
        }
        return outM;
    }
    
    private void matchWindow(float[][] windowAccel) {
        KNN knn = KNN.classify(KNN_K, windowAccel, gestureTemplates);
        
        Map<Integer, Float> cmdDists = Maps.newHashMap();
        for (ActionCommand command: knn.distsPerClass.keySet()) {
            Collection<Float> dists = knn.distsPerClass.get(command);
            //final float dist = Collections.min(dists);
            final float dist = average(dists);
            cmdDists.put(command.ordinal(), dist);
        }
        updateChart(distFrame, cmdDists);
        
        //System.out.println(_tmp);
        updateChart(knnFrame, toIntegerMap(knn.votesPerClass));
        
        float meanStddev = (stddev(windowAccel[0]) + stddev(windowAccel[1])
                + stddev(windowAccel[2])) / 3.0f;
        Map<Integer, Float> chartData = Maps.newHashMap();
        chartData.put(0, meanStddev);
        updateChart(stdDevFrame, chartData);
        
        // Finally, decide if we detected something
        decideGesture(knn, meanStddev);
    }
    
    private static void updateChart(final TimeseriesChartFrame panel,
                                    final Map<Integer, Float> data) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                panel.addToChart(data);
            }
        });
    }
    
    
    private void decideGesture(final KNN knn,
                               float stddev) {
        Map<ActionCommand, Float> detections = Maps.newHashMap();
        for (ActionCommand command: gestureTemplates.keySet()) {
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
            List<Entry<ActionCommand, Float>> l = Lists.newArrayList(knn.votesPerClass.entrySet());
            final ActionCommand bestClass = l.get(0).getKey();
            System.out.println("bestclass " + bestClass + " nearest : " + knn.getNeighborClass(0));
            
            // Check that nearest neighbor is of majority class
            if (knn.getNeighborClass(0).equals(bestClass)) {
                // Check that nearest neighbor dist is below threshold
                System.out.println("dist : " + knn.getNeighborDist(0));
                if (knn.getNeighborDist(0) < /*10000*/125) {
                    detections.put(bestClass, 1.0f);
                }
            }
        }
        
        updateChart(detectedFrame, toIntegerMap(detections));
        sendToDrone(detections);
    }
    
    private void sendToDrone(Map<ActionCommand, Float> detections) {
        for (Entry<ActionCommand, Float> e : detections.entrySet()) {
            final boolean v = e.getValue() > 0;
            this.updateDroneAction(e.getKey(), v);
        }
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
