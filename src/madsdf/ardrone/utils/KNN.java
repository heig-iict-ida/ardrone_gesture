/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.utils;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Floats;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import madsdf.ardrone.gesture.DTW;
import madsdf.ardrone.utils.DataFileReader.Gesture;

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
            Multimap<Integer, DataFileReader.Gesture> gestureTemplates) {
               // Contains (distance, gesture)
        TreeMap<Float, DataFileReader.Gesture> gestureDistances = Maps.newTreeMap();
        for (DataFileReader.Gesture g : gestureTemplates.values()) {
            //final float dist = DTW.allAxisEuclidean(windowAccel, g.accel);
            /*final float dist = DTW.allAxisEuclidean(
                    MathUtils.medianFilter(windowAccel, 10), 
                    MathUtils.medianFilter(g.accel, 10));*/
            //final float dist = DTW.allAxisDTW(windowAccel, g.accel);
            final float dist = DTW.allAxisDTW(
                    MathUtils.medianFilter(windowAccel, 10), 
                    MathUtils.medianFilter(g.accel, 10));
            gestureDistances.put(dist, g);
        }
        
        FluentIterable<Entry<Float, Gesture>> closest = FluentIterable
                .from(gestureDistances.entrySet())
                .limit(k);
        
        return new KNN(gestureTemplates.keySet(), closest);
    }
    
    // Return a copy of 'source' where, when iterating using entrySet, the
    // entries will be sorted by the VALUE of the entry
    private static ImmutableMap<Integer, Float> valueSortedMap(
            Map<Integer, Float> source) {
        // Ordering that sort Entry<Integer, Float> by decreasing e.value
        Ordering<Entry<Integer, Float>> entryOrdering = Ordering.natural()
                .onResultOf(new Function<Entry<Integer, Float>, Float>() {
                    @Override
                    public Float apply(Entry<Integer, Float> e) {
                       return e.getValue();
                    }
                }).reverse();
        // ImmutableMap guarantee that the iteration order over entrySet is
        // the same as the order used to construct the map
        ImmutableMap.Builder<Integer, Float> builder = ImmutableMap.builder();
        for (Entry<Integer, Float> e : entryOrdering.sortedCopy(source.entrySet())) {
            builder.put(e.getKey(), e.getValue());
        }
        return builder.build();
    }
    
    public final ImmutableList<Entry<Float, Gesture>> nearest;
    // For each class, contains the number of nearest neighbors of this class
    // The iteration order over this map is fixed and in decreasing order of
    // class popularity
    public final ImmutableMap<Integer, Float> votesPerClass;
    public final ImmutableMap<Integer, Float> distPerClass;
    
    private KNN(Iterable<Integer> allClasses,
                Iterable<Entry<Float, Gesture>> closest) {
        this.nearest = ImmutableList.copyOf(closest);
        
        // Compute votes per class and average distance to class
        Map<Integer, Float> _votesPerClass = Maps.newHashMap();
        zeroInit(_votesPerClass, allClasses);
        // Note that we only compute dist per class for classes that have at
        // least one instance in the nearest neighbors. Classes that aren't
        // represented should be considered with a distance of infinity
        Map<Integer, Float> _distPerClass = Maps.newHashMap();
        
        for (Entry<Float, Gesture> e: closest) {
            final float dist = e.getKey();
            final Gesture g = e.getValue();
            mapIncr(_votesPerClass, g.command, 1);
            mapIncr(_distPerClass, g.command, dist);
        }
        
        // Average dist
        for (Entry<Integer, Float> e: _distPerClass.entrySet()) {
            final int cmd = e.getKey();
            _distPerClass.put(cmd, e.getValue() / (float) _votesPerClass.get(cmd));
        }
        
        this.votesPerClass = valueSortedMap(_votesPerClass);
        this.distPerClass = valueSortedMap(_distPerClass);
    }
    
    public float getNeighborDist(int neighbor) {
        return nearest.get(neighbor).getKey();
    }
    
    public int getNeighborClass(int neighbor) {
        return nearest.get(neighbor).getValue().command;
    }
    
    public int numNeighbors() {
        return nearest.size();
    }
}
