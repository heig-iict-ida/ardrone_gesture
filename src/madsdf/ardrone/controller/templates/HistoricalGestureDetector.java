/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.templates;

// Gesture detector that makes a decision based on current and histori
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import madsdf.ardrone.ActionCommand;
    

public class HistoricalGestureDetector implements GestureDetector {

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
    private final static int NN_MIN_AGREE = (int) (KNNGestureController.KNN_K * 2. / 3.);
    private final static int HISTORY_SIZE = 2;
    private Deque<Entry> history = new ArrayDeque<>();
    // For each action, store the last time we decided it. This is to
    // avoid having noisy consecutive actions
    private final Map<ActionCommand, Long> prevTimestampMS = Maps.newHashMap();
    private long INTER_ACTION_DELAY = 2000;

    public HistoricalGestureDetector() {
        for (ActionCommand a : ActionCommand.values()) {
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
        for (Entry e : history) {
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
        if (prevBest != ActionCommand.NOTHING
                && (now - prevTimestampMS.get(prevBest)) > INTER_ACTION_DELAY) {
            prevTimestampMS.put(prevBest, now);
            return prevBest;
        } else {
            System.out.println("Prevented by INTER_ACTION_DELAY");
            return ActionCommand.NOTHING;
        }
    }
}
