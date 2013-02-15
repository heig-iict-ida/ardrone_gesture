/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.templates;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.controller.templates.KNNGestureController.GestureTemplate;
import madsdf.ardrone.utils.DTW;
import madsdf.ardrone.utils.DataFileReader.Gesture;
import madsdf.ardrone.utils.MathUtils;

public class KNN {
    // Increment the value of 'key' in 'map' by 'incr'. Create new entry
    // if needed 
    private static <K> void mapIncr (Map<K, Float> m, K key, float incr) {
        Float v = m.get(key);
        // TODO: If we initialize our maps using zeroInit, we should never have
        // null => Add precondition ?
        if (v == null) {
            m.put(key, incr);
        } else {
            m.put(key, v + incr);
        }
    }
    
    private static <K> void zeroInit(Map<K, Float> m, Iterable<K> iter) {
        for (K key: iter) {
            m.put(key, 0.0f);
        }
    }
    
    public static KNN classify(int k, float[][] windowAccel,
        Multimap<ActionCommand, GestureTemplate> gestureTemplates) {
        // Contains (distance, gesture)
        TreeMultimap<Float, GestureTemplate> gestureDistances = TreeMultimap.create();
        for (GestureTemplate g : gestureTemplates.values()) {
            //final float dist = DTW.allAxisEuclidean(windowAccel, g.accel);
            /*final float dist = DTW.allAxisEuclidean(
                    MathUtils.medianFilter(windowAccel, 10), 
                    MathUtils.medianFilter(g.gesture.accel, 10));*/
            /*final float dist = DTW.multiDTWDistance(
                    MathUtils.medianFilter(windowAccel, 10), 
                    MathUtils.medianFilter(g.gesture.accel, 10));*/
            //final float dist = DTW.allAxisDTW(windowAccel, g.accel);
            final float dist = DTW.allAxisDTW(
                    MathUtils.medianFilter(windowAccel, 10), 
                    MathUtils.medianFilter(g.gesture.accel, 10));
            gestureDistances.put(dist, g);
        }
        
        ImmutableMultimap.Builder<ActionCommand, Float> distsPerClassBuilder =
                ImmutableMultimap.builder();
        for (Entry<Float, GestureTemplate> e: gestureDistances.entries()) {
            final float dist = e.getKey();
            final GestureTemplate g = e.getValue();
            distsPerClassBuilder.put(g.command, dist);
        }
        
        FluentIterable<Entry<Float, GestureTemplate>> closest = FluentIterable
                .from(gestureDistances.entries())
                .limit(k);
        
        return new KNN(gestureTemplates.keySet(), closest, distsPerClassBuilder.build());
    }
    
    // Return a copy of 'source' where, when iterating using entrySet, the
    // entries will be sorted by the VALUE of the entry
    private static ImmutableMap<ActionCommand, Float> valueSortedMap(
            Map<ActionCommand, Float> source) {
        // Ordering that sort Entry<Integer, Float> by decreasing e.value
        Ordering<Entry<ActionCommand, Float>> entryOrdering = Ordering.natural()
                .onResultOf(new Function<Entry<ActionCommand, Float>, Float>() {
                    @Override
                    public Float apply(Entry<ActionCommand, Float> e) {
                       return e.getValue();
                    }
                }).reverse();
        // ImmutableMap guarantee that the iteration order over entrySet is
        // the same as the order used to construct the map
        ImmutableMap.Builder<ActionCommand, Float> builder = ImmutableMap.builder();
        for (Entry<ActionCommand, Float> e : entryOrdering.sortedCopy(source.entrySet())) {
            builder.put(e.getKey(), e.getValue());
        }
        return builder.build();
    }
    
    public final ImmutableList<Entry<Float, GestureTemplate>> nearest;
    // For each class, contains the number of nearest neighbors of this class
    // The iteration order over this map is fixed and in decreasing order of
    // class popularity
    public final ImmutableMap<ActionCommand, Float> votesPerClass;
    // TODO: Could use ImmutableTable with row = ActionCommand,
    // column = GestureTemplate sample
    public final ImmutableMultimap<ActionCommand, Float> distsPerClass;
    
    private KNN(Iterable<ActionCommand> allClasses,
                Iterable<Entry<Float, GestureTemplate>> closest,
                ImmutableMultimap<ActionCommand, Float> distsPerClass) {
        this.nearest = ImmutableList.copyOf(closest);
        this.distsPerClass = distsPerClass;
        
        // Compute votes per class and average distance to class
        Map<ActionCommand, Float> _votesPerClass = Maps.newHashMap();
        zeroInit(_votesPerClass, allClasses);
        
        for (Entry<Float, GestureTemplate> e : closest) {
            final float dist = e.getKey();
            final GestureTemplate g = e.getValue();
            mapIncr(_votesPerClass, g.command, 1);
        }
        
        this.votesPerClass = valueSortedMap(_votesPerClass);
    }
    
    public float getNeighborDist(int neighbor) {
        return nearest.get(neighbor).getKey();
    }
    
    public ActionCommand getNeighborClass(int neighbor) {
        return nearest.get(neighbor).getValue().command;
    }
    
    public int numNeighbors() {
        return nearest.size();
    }
}
