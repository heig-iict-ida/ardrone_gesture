/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.gesture;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.Maps;
import java.awt.Color;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

/**
 *
 * @author julien
 */
public class DTWDistsChartDrawer {
    private Map<Integer, TimeSeries> series;
    private JFreeChart chart;
    
    private final int NUM_VISIBLE = 100;
    
    private int counter = Integer.MIN_VALUE;
    
    public DTWDistsChartDrawer(ChartPanel panel, Integer[] gestureIDs,
                               String[] gestureNames) {
        checkState(gestureIDs.length == gestureNames.length);
        series = Maps.newHashMap();
        TimeSeriesCollection accelCol = new TimeSeriesCollection();
        for (int i = 0; i < gestureNames.length; ++i) {
            final TimeSeries s = new TimeSeries(gestureNames[i]);
            series.put(gestureIDs[i], s);
            accelCol.addSeries(s);
        }
        
        chart = ChartFactory.createTimeSeriesChart(
                "Distance to gesture templates",
                "Windows",
                "DTW distance",
                accelCol,
                true,
                false,
                false);
        
        XYPlot plot = chart.getXYPlot();
        plot.setRangeGridlinesVisible(false);     // Hide the grid in the graph
        plot.setDomainGridlinesVisible(false);
        plot.setBackgroundPaint(Color.WHITE);
        ValueAxis axisAcc = plot.getDomainAxis();
        axisAcc.setTickMarksVisible(true);    // Define the tick count
        axisAcc.setMinorTickCount(10);
        axisAcc.setAutoRange(true);
        axisAcc.setFixedAutoRange(NUM_VISIBLE);     // Define the number of visible value
        axisAcc.setTickLabelsVisible(false);  // Hide the axis labels
    }
    
    public void addToChart(Map<Integer, Float> commandDists) {
        for (Map.Entry<Integer, Float> e : commandDists.entrySet()) {
            series.get(e.getKey()).add(new FixedMillisecond(counter), e.getValue());
        }
        counter++;
    }
}
