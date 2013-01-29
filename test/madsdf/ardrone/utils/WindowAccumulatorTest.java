package madsdf.ardrone.utils;

import com.google.common.collect.Lists;
import java.util.List;
import madsdf.shimmer.gui.AccelGyro;
import madsdf.shimmer.gui.AccelGyro.Sample;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.core.Is.*;

public class WindowAccumulatorTest {
    /**
     * Test of add method, of class WindowAccumulator.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        // Windows of size 3 with step of 2
        WindowAccumulator instance = new WindowAccumulator<AccelGyro.UncalibratedSample>(3, 2);
        float[] dummy = new float[]{1, 2, 3};
        AccelGyro.UncalibratedSample s1 = new AccelGyro.UncalibratedSample(0, dummy, dummy);
        AccelGyro.UncalibratedSample s2 = new AccelGyro.UncalibratedSample(1, dummy, dummy);
        AccelGyro.UncalibratedSample s3 = new AccelGyro.UncalibratedSample(2, dummy, dummy);
        AccelGyro.UncalibratedSample s4 = new AccelGyro.UncalibratedSample(3, dummy, dummy);
        AccelGyro.UncalibratedSample s5 = new AccelGyro.UncalibratedSample(4, dummy, dummy);
        List<AccelGyro.UncalibratedSample> w1 = Lists.newArrayList(s1, s2, s3);
        List<AccelGyro.UncalibratedSample> w2 = Lists.newArrayList(s3, s4, s5);
        
        assertNull(instance.add(s1));
        assertNull(instance.add(s2));
        assertEquals(w1, instance.add(s3));
        assertNull(instance.add(s4));
        assertEquals(w2, instance.add(s5));
    }
}
