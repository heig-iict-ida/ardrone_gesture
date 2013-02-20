/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.utils.MathUtils;

/**
 *
 */
public class DroneController {
    // Event type that indicate this controller should update the ARdrone
    public static class TickMessage {}
    
    private final ImmutableSet<ActionCommand> actionMask;
    private final ARDrone drone;
    
    private final Timer commandTimer = new Timer();
    
    private final Map<ActionCommand, TimerTask> commandTasks = Maps.newHashMap();
    
    private final Stopwatch stopwatch = new Stopwatch();
    
    // For each action, contains remaining activation time in milliseconds
    private final long[] actionRemaining = new long[ActionCommand.values().length];
    
    /**
     * @param actionMask a set of ActionCommand which this controller is allowed
     * to send to the drone
     */
    public DroneController(ImmutableSet<ActionCommand> actionMask, ARDrone adrone) {
        this.actionMask = actionMask;
        this.drone = adrone;
        Arrays.fill(actionRemaining, 0);
        
        stopwatch.start();
    }
    
    public void enableAction(final ActionCommand cmd, long durationMS) {
        if (actionMask.contains(cmd)) {
            synchronized(actionRemaining) {
                actionRemaining[cmd.ordinal()] += durationMS;
            }
        }
    }
    
    // Enable the given action for the given duration
    /*public synchronized void enableAction(final ActionCommand cmd, long durationMS) {
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
    }*/
    
    @Deprecated
    public void updateDroneAction(ActionCommand cmd, boolean newState) {
        if (actionMask.contains(cmd)) {
            drone.updateActionMap(cmd, newState);
        }
    }
    
    @Subscribe
    public void onTick(TickMessage msg) {
        stopwatch.stop();
        final long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        
        for (ActionCommand a : actionMask) {
            long remaining = actionRemaining[a.ordinal()];
            // If nothing has changed, don't re-update actionMap with false
            // This is to avoid conflict with another controller
            if (remaining == 0) {
                continue;
            }
            
            remaining = Math.max(0, remaining - elapsed);
            actionRemaining[a.ordinal()] = remaining;
            if (remaining == 0) {
                drone.updateActionMap(a, false);
            } else {
                drone.updateActionMap(a, true);
            }
        }
        
        stopwatch.reset();
        stopwatch.start();
    }
}
