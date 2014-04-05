package org.thoughtcrime.securesms.test;

import android.test.InstrumentationTestCase;

/**
 * Created by corbett on 4/5/14.
 */
public class DemoTest extends InstrumentationTestCase {
    public void testFail() throws Exception {
        final int expected =1;
        final int reality = 5;
        assertNotSame(expected,reality);
    }

    public void testSucceed() throws Exception {
        final int expected =5;
        final int reality = 5;
        assertEquals(expected,reality);
    }
}
