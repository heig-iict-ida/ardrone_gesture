/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.templates;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import madsdf.ardrone.ActionCommand;

/**
 *
 * @author julien
 */
public class RepetitiveGestureDetector implements GestureDetector {

    private final static float STDDEV_THRESHOLD = 2000;
    private final static float NEAREST_DIST_THRESHOLD = 100000;
    private final static int NN_MIN_AGREE = (int) (KNNGestureController.KNN_K * 2. / 3.);
    private KNN knn;
    private float stddev;

    public void addVotation(KNN knn, float stddev) {
        this.knn = knn;
        this.stddev = stddev;
    }

    public ActionCommand decide() {
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
        return bestClass;
    }
}
