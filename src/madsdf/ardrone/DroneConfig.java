/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone;

import com.google.common.collect.ImmutableMap;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import madsdf.ardrone.utils.PropertiesReader;

/**
 * Drone configuration
 */
public class DroneConfig extends PropertiesReader {
    static final String CONFIG_DRONE_FILE = "ardrone.properties";
    
    private static final ImmutableMap<String, String> defaultProperties =
            new ImmutableMap.Builder<String, String>()
            .put("ip", "192.168.1.1")
            .put("euler_max", "0.22")  // max angle
            .put("altitude_max", "2000")  // max altitude
            .put("altitude_min", "20") // min altitude
            .put("vz_max", "1300") // drone max up/down speed
            .put("yaw_max", "4.0")  // drone max yaw speed
            .put("left_shimmer", "9EDB")
            .put("right_shimmer", "BDCD")
            .build();
    
    private static Properties loadProperties() {
        Properties defaults = new Properties();
        for (Map.Entry<String, String> e : defaultProperties.entrySet()) {
            defaults.setProperty(e.getKey(), e.getValue());
        }
        Properties config = new Properties(defaults);
        try {
            config.load(new FileReader(CONFIG_DRONE_FILE));
        } catch (IOException ex) {
            Logger.getLogger(DroneConfig.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Couldn't load property file : " + CONFIG_DRONE_FILE);
        }
        return config;
    }
    
    private static DroneConfig instance = null;
    
    public static DroneConfig get() {
        if (instance == null) {
            instance = new DroneConfig();
        }
        return instance;
    }
    
    private DroneConfig() {
        super(loadProperties());
    }
}
