/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.utils;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Maps;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import madsdf.ardrone.controller.ActionCommand;

/**
 * An thin wrapper around properties
 */
public class PropertiesReader {
    final Properties props;
    public PropertiesReader(Properties props) {
        this.props = props;
    }
    
    public PropertiesReader(String filename) {
        this.props = new Properties();
        try {
            this.props.load(new FileInputStream(filename));
        } catch (Exception ex) {
            Logger.getLogger(PropertiesReader.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex.toString());
        }
    }
    
    public Map<String, String> getMap(String key) {
        final String str = getString(key);
        final String[] kvpairs = str.split(",");
        Map<String, String> outMap = Maps.newHashMap();
        for (String kv : kvpairs) {
            final String[] keyvalue = kv.split(":");
            outMap.put(keyvalue[0], keyvalue[1]);
        }
        return outMap;
    }
    
    public boolean hasKey(String key) {
        String res = props.getProperty(key);
        return res != null;
    }
    
    public String getString(String key) {
        String res = props.getProperty(key);
        checkNotNull(res);
        return res;
    }
    
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getString(key));
    }
    
    public int getInteger(String key) {
        return Integer.parseInt(getString(key));
    }
    
    public double getDouble(String key) {
        return Double.parseDouble(getString(key));
    }
    
    public <T extends Enum> T getEnum(String key, Class<T> enumType) {
        return enumType.cast(Enum.valueOf(enumType, getString(key)));
    }
    
    public ImmutableSet<ActionCommand> getCommandMask(String key) {
        final String[] pieces = getString(key).split(";");
        List<ActionCommand> cmds = new ArrayList<ActionCommand>();
        for (String cmd: pieces) {
            cmds.add(ActionCommand.valueOf(cmd));
        }
        return new ImmutableSet.Builder<ActionCommand>().addAll(cmds).build();
    }
}
