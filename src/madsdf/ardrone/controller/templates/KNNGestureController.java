package madsdf.ardrone.controller.templates;

import bibliothek.gui.DockController;
import bibliothek.gui.DockStation;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.DefaultDockable;
import bibliothek.gui.dock.SplitDockStation;
import bibliothek.gui.dock.station.split.SplitDockGrid;
import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.JFrame;
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
    public static class GestureTemplate implements Comparable<GestureTemplate> {
        public final ActionCommand command;
        public final Gesture gesture;
        public GestureTemplate(ActionCommand cmd, Gesture g) {
            this.command = cmd;
            this.gesture = g;
        }

        @Override
        public int compareTo(GestureTemplate that) {
            return ComparisonChain.start()
                .compare(this.command, that.command)
                .result();
        }
    }
    
    public static KNNGestureController FromProperties(
            String name,
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
         
        KNNGestureController ctrl = new KNNGestureController(name, actionMask, drone, templates, calibrated);
        ebus.register(ctrl);
        return ctrl;
    }
    
    private Multimap<ActionCommand, GestureTemplate> gestureTemplates = ArrayListMultimap.create();
    private WindowAccumulator accumulator =
            new WindowAccumulator<AccelGyro.Sample>(150, 15);
    
    private TimeseriesChartPanel distChartPanel;
    private TimeseriesChartPanel stdChartPanel;
    private TimeseriesChartPanel knnChartPanel;
    private TimeseriesChartPanel detectedChartPanel;
    
    private JFrame chartFrame;
    private DockController dockController;
    
    private GestureDetector gestureDetector = new GestureDetector();
    
    private static final int KNN_K = 3;
    private static final long COMMAND_DURATION = 800;
    
    private final boolean calibrated;
    
    public KNNGestureController(final String name,
                                ImmutableSet<ActionCommand> actionMask,
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
                // Dockable frame creation
                chartFrame = new JFrame();
                chartFrame.setTitle(name);
                dockController = new DockController();
                dockController.setRootWindow(chartFrame);
                chartFrame.addWindowListener(new WindowAdapter() {
                   public void windowClosing(WindowEvent e) {
                       dockController.kill();
                   } 
                });
                SplitDockStation station = new SplitDockStation();
                dockController.add(station);
                chartFrame.add(station);
                
                final int GRID_SIZE = 200;
                chartFrame.setBounds(40, 40, 2*GRID_SIZE, 2*GRID_SIZE);
                
                
                SplitDockGrid grid = new SplitDockGrid();
                
                // Chart panels
                ImmutableSortedMap.Builder<Integer, String> b = ImmutableSortedMap.naturalOrder();
                for (ActionCommand a : gestureTemplates.keySet()) {
                    b.put(a.ordinal(), a.name());
                }
                ImmutableSortedMap<Integer, String> commandIDToName = b.build();
                
                distChartPanel = new TimeseriesChartPanel(
                        "Distance to gesture templates",
                        "Windows", "DTW distance", commandIDToName);
                Dockable d = createDockable(distChartPanel);
                grid.addDockable(0, 0, GRID_SIZE, GRID_SIZE, d);
                
                knnChartPanel = new TimeseriesChartPanel(
                        "KNN votes",
                        "Windows", "votes", commandIDToName);
                d = createDockable(knnChartPanel);
                grid.addDockable(0, GRID_SIZE, GRID_SIZE, GRID_SIZE, d);
                stdChartPanel = new TimeseriesChartPanel(
                        "Standard deviation",
                        "Windows", "Stddev", ImmutableSortedMap.of(0, "Stddev"));
                d = createDockable(stdChartPanel);
                grid.addDockable(GRID_SIZE, 0, GRID_SIZE, GRID_SIZE, d);
                
                detectedChartPanel = new TimeseriesChartPanel(
                        "Detected gestures",
                        "Windows", "Detected", commandIDToName);
                d = createDockable(detectedChartPanel);
                grid.addDockable(GRID_SIZE, GRID_SIZE, GRID_SIZE, GRID_SIZE, d);
                
                station.dropTree(grid.toTree());
                
                chartFrame.setVisible(true);
                
            }
        });
    }
    
    private static Dockable createDockable(TimeseriesChartPanel chartPanel) {
        DefaultDockable dockable = new DefaultDockable();
        dockable.setTitleText(chartPanel.title);
        dockable.add(chartPanel);
        return dockable;
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
    
    public ImmutableMap<Integer, Float> toIntegerMap(ImmutableMap<ActionCommand, Float> m) {
        ImmutableMap.Builder<Integer, Float> outM = ImmutableMap.builder();
        for (Entry<ActionCommand, Float> e : m.entrySet()) {
            outM.put(e.getKey().ordinal(), e.getValue());
        }
        return outM.build();
    }
    
    private void matchWindow(float[][] windowAccel) {
        KNN knn = KNN.classify(KNN_K, windowAccel, gestureTemplates);
        
        ImmutableMap.Builder<Integer, Float> cmdDists = ImmutableMap.builder();
        for (ActionCommand command: knn.distsPerClass.keySet()) {
            Collection<Float> dists = knn.distsPerClass.get(command);
            //final float dist = Collections.min(dists);
            final float dist = average(dists);
            cmdDists.put(command.ordinal(), dist);
        }
        updateChart(distChartPanel, cmdDists.build());
        
        //System.out.println(_tmp);
        updateChart(knnChartPanel, toIntegerMap(knn.votesPerClass));
        
        float meanStddev = (stddev(windowAccel[0]) + stddev(windowAccel[1])
                + stddev(windowAccel[2])) / 3.0f;
        ImmutableMap<Integer, Float> chartData = ImmutableMap.of(0, meanStddev);
        updateChart(stdChartPanel, chartData);
        
        // Finally, decide if we detected something
        decideGesture(knn, meanStddev);
    }
    
    private static void updateChart(final TimeseriesChartPanel panel,
                                    final ImmutableMap<Integer, Float> data) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                panel.addToChart(data);
            }
        });
    }
    
    // Gesture detector that makes a decision based on current and historical
    // KNN votes
    private static class GestureDetector {
        private static class Entry {
            public final KNN knn;
            public final float stddev;
            public Entry(KNN knn, float stddev) {
                this.knn = knn;
                this.stddev = stddev;
            }
        }
        
        private final static float STDDEV_THRESHOLD = 2000;
        private final static float NEAREST_DIST_THRESHOLD = 100000;
        // Minimum number of NN that should agree
        private final static int NN_MIN_AGREE = (int)(KNN_K * 2. / 3.);
        
        private final static int HISTORY_SIZE = 2;
        private Deque<Entry> history = new ArrayDeque<>();
        
        // For each action, store the last time we decided it. This is to
        // avoid having noisy consecutive actions
        private final Map<ActionCommand, Long> prevTimestampMS = Maps.newHashMap();
        private long INTER_ACTION_DELAY = 2000;
        
        public GestureDetector() {
            for (ActionCommand a: ActionCommand.values()) {
                prevTimestampMS.put(a, System.currentTimeMillis());
            }
        }
        
        public void addVotation(KNN knn, float stddev) {
            history.addLast(new Entry(knn, stddev));
            while (history.size() > HISTORY_SIZE) {
                history.removeFirst();
            }
        }
        
        public ActionCommand decide() {
            ActionCommand prevBest = null;
            //System.out.println("==== decide ====");
            for (Entry e: history) {
                final KNN knn = e.knn;
                final float stddev = e.stddev;
                // Check stddev above threshold
                if (stddev < STDDEV_THRESHOLD) {
                    return ActionCommand.NOTHING;
                }
                
                // Check that nearest neighbor is of majority class
                List<Map.Entry<ActionCommand, Float>> l = Lists.newArrayList(
                        knn.votesPerClass.entrySet());
                final ActionCommand bestClass = l.get(0).getKey();
                //System.out.println("best : " + bestClass);
                if (!knn.getNeighborClass(0).equals(bestClass)) {
                    return ActionCommand.NOTHING;
                }
                //System.out.println("1");
                // Check dist of nearest neighbour below threshold
                if (knn.getNeighborDist(0) > NEAREST_DIST_THRESHOLD) {
                    return ActionCommand.NOTHING;
                }
                //System.out.println("2");
                
                // Check number of agreeing NN
                //System.out.println("votes for best : " + knn.votesPerClass.get(bestClass));
                //System.out.println("min agree : " + NN_MIN_AGREE);
                if (knn.votesPerClass.get(bestClass) < NN_MIN_AGREE) {
                    return ActionCommand.NOTHING;
                }
                //System.out.println("3");
                
                // Check that previous votation detected same class as us
                if (prevBest != null && prevBest != bestClass) {
                    return ActionCommand.NOTHING;
                }
                //System.out.println("4");
                
                prevBest = bestClass;
            }
            
            // If we reach this point, this means that all entries in history
            // have :
            // - detected the same class
            // - fulfilled all conditions
            if (prevBest == null) {
                return ActionCommand.NOTHING;
            }
            
            // Check against INTER_ACTION_DELAY
            final long now = System.currentTimeMillis();
            if (prevBest != ActionCommand.NOTHING &&
                (now - prevTimestampMS.get(prevBest)) > INTER_ACTION_DELAY) {
                prevTimestampMS.put(prevBest, now);
                return prevBest;
            } else {
                System.out.println("Prevented by INTER_ACTION_DELAY");
                return ActionCommand.NOTHING;
            }
        }
    }
    
    
    private void decideGesture(final KNN knn,
                               float stddev) {
        gestureDetector.addVotation(knn, stddev);
        final ActionCommand detected = gestureDetector.decide();
        ImmutableMap.Builder<ActionCommand, Float> _detections = ImmutableMap.builder();
        for (ActionCommand command: gestureTemplates.keySet()) {
            if (detected.equals(command)) {
                _detections.put(command, 1.0f);
            } else {
                _detections.put(command, 0.0f);
            }
        }
        ImmutableMap<ActionCommand, Float> detections = _detections.build();
        
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
        
        /*if (stddev > 2000) {  // 25
            List<Entry<ActionCommand, Float>> l = Lists.newArrayList(knn.votesPerClass.entrySet());
            final ActionCommand bestClass = l.get(0).getKey();
            System.out.println("bestclass " + bestClass + " nearest : " + knn.getNeighborClass(0));
            
            // Check that nearest neighbor is of majority class
            if (knn.getNeighborClass(0).equals(bestClass)) {
                // Check that nearest neighbor dist is below threshold
                System.out.println("dist : " + knn.getNeighborDist(0));
                if (knn.getNeighborDist(0) < 100000) { // 125
                    detections.put(bestClass, 1.0f);
                }
            }
        }*/
        
        updateChart(detectedChartPanel, toIntegerMap(detections));
        sendToDrone(detections);
    }
    
    private void sendToDrone(Map<ActionCommand, Float> detections) {
        for (Entry<ActionCommand, Float> e : detections.entrySet()) {
            final boolean v = e.getValue() > 0;
            if (e.getValue() > 0) {
                this.enableAction(e.getKey(), COMMAND_DURATION);
            }
            //this.updateDroneAction(e.getKey(), v);
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
