/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;

import com.google.common.collect.ImmutableSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.ActionCommand;

/**
 * Testing controller. Can be used to test DroneController timing
 */
public class DummyController extends DroneController {
    private final Random random = new Random();
    
    private final static int MIN_DURATION = 200;
    private final static int MAX_DURATION = 1500;
    
    private final static int MIN_DELAY = 1000;
    private final static int MAX_DELAY = 2000;
    
    private int randLong(int min, int max) {
        return min + random.nextInt(max - min);
    }
    
    public DummyController(ImmutableSet<ActionCommand> actionMask,
                           ARDrone drone) {
        super(actionMask, drone);
        
        new Thread(){
            @Override
            public void run() {
                while (true) {
                    final int duration = randLong(MIN_DURATION, MAX_DURATION);
                    final int delay = randLong(MIN_DELAY, MAX_DELAY);
                    System.out.println("command with duration =" + duration + ", delay = " + delay);
                    DummyController.this.enableAction(ActionCommand.GOFORWARD, duration);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(DummyController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }.start();
    }
}
