package org.thoughtcrime.securesms.jobmanager.impl;

import org.junit.Test;
import org.signal.core.util.StreamUtil;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class JsonJobDataTest {

  private static final float FloatDelta = 0.00001f;

  @Test
  public void deserialize_dataMatchesExpected() throws IOException {
    JsonJobData data = JsonJobData.deserialize(StreamUtil.readFully(ClassLoader.getSystemClassLoader().getResourceAsStream("data/data_serialized.json")));

    assertEquals("s1 value", data.getString("s1"));
    assertEquals("s2 value", data.getString("s2"));
    assertArrayEquals(new String[]{ "a", "b", "c" }, data.getStringArray("s_array_1"));

    assertEquals(1, data.getInt("i1"));
    assertEquals(2, data.getInt("i2"));
    assertEquals(Integer.MAX_VALUE, data.getInt("max"));
    assertEquals(Integer.MIN_VALUE, data.getInt("min"));
    assertArrayEquals(new int[]{ 1, 2, 3, Integer.MAX_VALUE, Integer.MIN_VALUE }, data.getIntegerArray("i_array_1"));

    assertEquals(10, data.getLong("l1"));
    assertEquals(20, data.getLong("l2"));
    assertEquals(Long.MAX_VALUE, data.getLong("max"));
    assertEquals(Long.MIN_VALUE, data.getLong("min"));
    assertArrayEquals(new long[]{ 1, 2, 3, Long.MAX_VALUE, Long.MIN_VALUE }, data.getLongArray("l_array_1"));

    assertEquals(1.2f, data.getFloat("f1"), FloatDelta);
    assertEquals(3.4f, data.getFloat("f2"), FloatDelta);
    assertArrayEquals(new float[]{ 5.6f, 7.8f }, data.getFloatArray("f_array_1"), FloatDelta);

    assertEquals(10.2, data.getDouble("d1"), FloatDelta);
    assertEquals(30.4, data.getDouble("d2"), FloatDelta);
    assertArrayEquals(new double[]{ 50.6, 70.8 }, data.getDoubleArray("d_array_1"), FloatDelta);

    assertTrue(data.getBoolean("b1"));
    assertFalse(data.getBoolean("b2"));
    assertArrayEquals(new boolean[]{ false, true }, data.getBooleanArray("b_array_1"));
  }
}
