/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.ActionCommand;

/**
 *
 */
public class DroneController {
    private final ImmutableSet actionMask;
    private final ARDrone drone;
    
    private final Timer commandTimer = new Timer();
    
    private final Map<ActionCommand, TimerTask> commandTasks = Maps.newHashMap();
    
    /**
     * @param actionMask a set of ActionCommand which this controller is allowed
     * to send to the drone
     */
    public DroneController(ImmutableSet<ActionCommand> actionMask, ARDrone adrone) {
        this.actionMask = actionMask;
        this.drone = adrone;
    }
    
    // Enable the given action for the given duration
    public synchronized void enableAction(final ActionCommand cmd, long durationMS) {
        if (actionMask.contains(cmd)) {
            drone.updateActionMap(cmd, true);
            TimerTask t = commandTasks.get(cmd);
            // newDuration is computed to avoid replacing a very long lasting
            // task with a new, shorter-lasting task
            final long now = System.currentTimeMillis();
            long newDuration = 0;
            if (t != null) {
                newDuration = Math.max(t.scheduledExecutionTime() - now, durationMS);
                t.cancel();
            } else {
                newDuration = durationMS;
            }
            t = new TimerTask() {
                @Override
                public void run() {
                    synchronized(DroneController.this) {
                        drone.updateActionMap(cmd, false);
                    }
                }
            };
            commandTimer.purge();
            commandTimer.schedule(t, newDuration);
            commandTasks.put(cmd, t);
        }
    }
    
    @Deprecated
    public void updateDroneAction(ActionCommand cmd, boolean newState) {
        if (actionMask.contains(cmd)) {
            drone.updateActionMap(cmd, newState);
        }
    }
}
