package madsdf.ardrone.controller;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import madsdf.ardrone.utils.PropertiesReader;

/**
 * Controller based on matching incoming measurements with gesture templates
 */
public class DTWGestureController {
    /*public static DTWGestureController FromProperties(
            ImmutableSet<ActionCommand> actionMask, ARDrone drone,
            EventBus ebus, String configSensor) {
        PropertiesReader reader = new PropertiesReader(configSensor);
        checkState(reader.getString("class_name").equals(ShimmerAngleController.class.getName()));
        ShimmerAngleController ctrl = new ShimmerAngleController(actionMask, drone, reader.getEnum("cmdmap", ShimmerAngleController.CmdMap.class));
        ebus.register(ctrl);
        return ctrl;
    }
    
    public DTWGestureController()*/
}
