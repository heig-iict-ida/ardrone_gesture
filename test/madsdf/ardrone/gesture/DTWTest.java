/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.gesture;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class DTWTest {
    /**
     * Test of dtwDistance method, of class DTW.
     */
    @Test
    public void testDtwDistance() {
        System.out.println("dtwDistance");
        float[] serie1 = {0,0,0,0,1,1,2,2,3,2,1,1,0,0,0,0};
        float[] serie2 = {0,0,1,1,2,2,3,3,3,3,2,2,1,1,0,0};
        float expResult = 0.0f;
        float result = DTW.dtwDistance(serie1, serie2);
        assertEquals(expResult, result, 0.0);
    }
}
