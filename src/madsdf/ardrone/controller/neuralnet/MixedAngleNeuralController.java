/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.neuralnet;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.controller.DroneController;
import madsdf.ardrone.controller.ShimmerAngleController;
import madsdf.ardrone.utils.PropertiesReader;

/**
 *
 * @author julien
 */
public class MixedAngleNeuralController {
    private List<DroneController> controllers = new ArrayList<DroneController>();
    private ARDrone drone;
    
    public MixedAngleNeuralController(ARDrone drone, List<DroneController> controllers) {
        this.drone = drone;
        this.controllers = controllers;
    }
    
    public static void FromProperties(ARDrone drone, EventBus ebus, Properties properties) {
        final PropertiesReader reader = new PropertiesReader(properties);
        List<DroneController> controllers = new ArrayList<DroneController>();
        controllers.add(ShimmerAngleController.FromProperties(reader.getCommandMask("angle_left_cmdmask"), drone, ebus, reader.getString("angle_left_props")));
        controllers.add(ShimmerAngleController.FromProperties(reader.getCommandMask("angle_right_cmdmask"), drone, ebus, reader.getString("angle_right_props")));
        controllers.add(NeuralController.FromProperties(reader.getCommandMask("neural_left_cmdmask"), drone, ebus, reader.getString("neural_left_props")));
        controllers.add(NeuralController.FromProperties(reader.getCommandMask("neural_right_cmdmask"), drone, ebus, reader.getString("neural_right_props")));
    }
}
