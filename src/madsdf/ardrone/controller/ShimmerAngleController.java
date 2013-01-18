/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;
import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.swing.SwingUtilities;
import madsdf.ardrone.utils.PropertiesReader;
import madsdf.shimmer.glview.ShimmerAngleConverter.AngleEvent;
import madsdf.shimmer.gui.ShimmerMoveAnalyzerFrame;

/**
 *
 * @author julien
 */
public class ShimmerAngleController extends DroneController {
    static enum CmdMap {
        LEFT,
        RIGHT
    }
    private HashMap<ActionCommand, Boolean> activeActions = Maps.newHashMap();
    private final CmdMap cmdmap;
    
    public static ShimmerAngleController FromProperties(ImmutableSet<ActionCommand> actionMask, ARDrone drone, EventBus ebus, String configSensor) {
        PropertiesReader reader = new PropertiesReader(configSensor);
        checkState(reader.getString("class_name").equals(ShimmerAngleController.class.getName()));
        ShimmerAngleController ctrl = new ShimmerAngleController(actionMask, drone, reader.getEnum("cmdmap", CmdMap.class));
        ebus.register(ctrl);
        return ctrl;
    }
    
    public ShimmerAngleController(ImmutableSet<ActionCommand> actionMask, ARDrone drone, CmdMap cmdmap) {
        super(actionMask, drone);
        activeActions.put(ActionCommand.GOLEFT, false);
        activeActions.put(ActionCommand.GORIGHT, false);
        activeActions.put(ActionCommand.GOFORWARD, false);
        activeActions.put(ActionCommand.GOBACKWARD, false);
        this.cmdmap = cmdmap;
    }
    
    @Subscribe
    public void anglesUpdated(AngleEvent event) {
        actionsFromAngles(event);
        SwingUtilities.invokeLater(new Runnable() {
         public void run() {
             for (Map.Entry<ActionCommand, Boolean> e : activeActions.entrySet()) {
                 updateDroneAction(e.getKey(), e.getValue());
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
