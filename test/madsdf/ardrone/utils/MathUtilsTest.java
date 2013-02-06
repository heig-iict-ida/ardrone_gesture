/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author julien
 */
public class MathUtilsTest {
    
    public MathUtilsTest() {
    }

    @Test
    public void testMedian() {
        System.out.println("median");
        // 2 elements
        float[] array = {3, 75};
        float expResult = 39;
        float result = MathUtils.median(array);
        assertEquals(expResult, result, 0.0);
        
        // all same elements
        array = new float[]{5, 5, 5, 5, 5};
        expResult = 5;
        result = MathUtils.median(array);
        assertEquals(expResult, result, 0.0);
        
        // odd number of elements
        array = new float[]{9, 3, 44, 17, 15};
        expResult = 15;
        result = MathUtils.median(array);
        assertEquals(expResult, result, 0.0);
        
        // even number of elements
        array = new float[]{8, 3, 44, 17, 12, 6};
        expResult = 10;
        result = MathUtils.median(array);
        assertEquals(expResult, result, 0.0);
    }
    
    @Test
    public void testMedianFilter() {
        System.out.println("test median");
        // Random array, expected result obtained using bottleneck's move_median
        float[] array = {
            0.03029153f,  0.27734206f,  0.67504752f,  0.05576997f,  0.91579534f,
            0.40687893f,  0.49626695f,  0.59964632f,  0.11148912f,  0.42862261f
        };
        float[] expResult = {
            0.1538168f ,  0.47619479f,  0.36540874f, 0.48578265f,  0.66133714f,
            0.45157294f,  0.54795664f,  0.35556772f, 0.27005586f,  0.42862261f
        };
        float[] result = MathUtils.medianFilter(array, 2);
        assertArrayEquals(expResult, result, 1e-5f);
    }
}
