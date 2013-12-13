package org.thoughtcrime.securesms.tests;

import junit.framework.TestCase;

import org.thoughtcrime.securesms.util.Util;

/**
 * Created by kyle on 12/13/13.
 */
public class UtilTest extends TestCase {

    public void testSplitString() {
        String[] expected = new String[]{"1", "2", "3"};
        assertEquals(expected, Util.splitString("123", 1));
    }
}


