/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;

import static com.google.common.base.Preconditions.*;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.ARDrone;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.utils.MathUtils;

/**
 *
 */
public class DroneController {
    private final ImmutableSet actionMask;
    private final ARDrone drone;
    
    // Maps action to their remaining duration
    private final ConcurrentMap<ActionCommand, Float> actionDuration = Maps.newConcurrentMap();
    private final Timer updateTimer = new Timer();
    
    private int UPDATE_PERIOD_MS = 200;
    
    /**
     * @param actionMask a set of ActionCommand which this controller is allowed
     * to send to the drone
     */
    public DroneController(ImmutableSet<ActionCommand> actionMask, ARDrone adrone) {
        this.actionMask = actionMask;
        this.drone = adrone;
        
        for (ActionCommand a: actionMask) {
            actionDuration.put(a, 0.f);
        }
        
        // Update every 200 milliseconds
        updateTimer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run() {
                for (ActionCommand a: actionDuration.keySet()) {
                    final float current = actionDuration.get(a);
                    final float remaining = Math.max(0, current - UPDATE_PERIOD_MS);
                    actionDuration.put(a, remaining);
                    if (remaining == 0) {
                        drone.updateActionMap(a, false);
                    } else {
                        drone.updateActionMap(a, true);
                    }
                }
            }
        }, 0, UPDATE_PERIOD_MS);
    }
    
    // Enable the given action for the given duration
    public void enableAction(final ActionCommand cmd, long durationMS) {
        /*if (actionMask.contains(cmd)) {
            drone.updateActionMap(cmd, true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    drone.updateActionMap(cmd, false);
                }
            }, durationMS);
        }*/
        if (actionMask.contains(cmd)) {
            MathUtils.mapIncr(actionDuration, cmd, durationMS);
            drone.updateActionMap(cmd, true);
        }
    }
    
    public void updateDroneAction(ActionCommand cmd, boolean newState) {
        if (actionMask.contains(cmd)) {
            drone.updateActionMap(cmd, newState);
        }
    }
}
