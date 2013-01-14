/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import madsdf.shimmer.glview.ShimmerAngleConverter.AngleEvent;
import madsdf.shimmer.gui.ShimmerMoveAnalyzerFrame;

/**
 *
 * @author julien
 */
public class ShimmerAngleController {
    ARDrone drone;
    
    private HashMap<ActionCommand, Boolean> activeActions = Maps.newHashMap();
    
    public ShimmerAngleController(ARDrone drone) {
        this.drone = drone;
        activeActions.put(ActionCommand.GOLEFT, false);
        activeActions.put(ActionCommand.GORIGHT, false);
        activeActions.put(ActionCommand.GOFORWARD, false);
        activeActions.put(ActionCommand.GOBACKWARD, false);
    }
    
    @Subscribe
    public void anglesUpdated(AngleEvent event) {
        actionsFromAngles(event);
        SwingUtilities.invokeLater(new Runnable() {
         public void run() {
             for (Map.Entry<ActionCommand, Boolean> e : activeActions.entrySet()) {
                 drone.updateActionMap(e.getKey(), e.getValue());
             }
         }
      });
    }
    
    private void actionsFromAngles(AngleEvent event) {
        activeActions.put(ActionCommand.GOLEFT, 
                          event.pitch > -60 && event.pitch < -20 );
        activeActions.put(ActionCommand.GORIGHT,
                          event.pitch > 20 && event.pitch < 60);
        activeActions.put(ActionCommand.GOFORWARD,
                          event.roll > -60 && event.roll < -20);
        activeActions.put(ActionCommand.GOBACKWARD,
                          event.roll > 20 && event.roll < 60);
    }
}
