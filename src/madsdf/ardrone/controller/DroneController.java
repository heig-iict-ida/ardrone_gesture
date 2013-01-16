/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;

import com.google.common.collect.ImmutableSet;

/**
 *
 * @author julien
 */
public class DroneController {
    private final ImmutableSet actionMask;
    private final ARDrone drone;
    
    /**
     * @param actionMask a set of ActionCommand which this controller is allowed
     * to send to the drone
     */
    public DroneController(ImmutableSet<ActionCommand> actionMask, ARDrone drone) {
        this.actionMask = actionMask;
        this.drone = drone;
    }
    
    protected void updateDroneAction(ActionCommand cmd, boolean newState) {
        if (actionMask.contains(cmd)) {
            drone.updateActionMap(cmd, newState);
        }
    }
}
