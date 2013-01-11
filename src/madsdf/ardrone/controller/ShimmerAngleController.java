/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller;
import com.google.common.eventbus.Subscribe;
import madsdf.shimmer.glview.ShimmerAngleConverter.AngleEvent;
import madsdf.shimmer.gui.ShimmerMoveAnalyzerFrame;

/**
 *
 * @author julien
 */
public class ShimmerAngleController {
    ARDrone drone;
    ShimmerMoveAnalyzerFrame moveAnalyzer;
    
    public ShimmerAngleController(ARDrone drone) {
        this.drone = drone;
         java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                moveAnalyzer = new ShimmerMoveAnalyzerFrame(new String[]{});
                moveAnalyzer.setVisible(true);
            }
        });
    }
    
    @Subscribe
    public void anglesUpdated(AngleEvent event) {
        System.out.println("Angles update received : " + event.roll);
        // TODO : Update ardrone action map
    }
}
