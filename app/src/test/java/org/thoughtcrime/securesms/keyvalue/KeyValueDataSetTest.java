package org.thoughtcrime.securesms.keyvalue;

import org.junit.Test;

import java.util.Collections;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class KeyValueDataSetTest {

  @Test
  public void containsKey_generic() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putBlob("a", new byte[0]);
    subject.putBoolean("b", true);
    subject.putFloat("c", 1.1f);
    subject.putInteger("d", 3);
    subject.putLong("e", 7L);
    subject.putString("f", "spiderman");

    assertTrue(subject.containsKey("a"));
    assertTrue(subject.containsKey("b"));
    assertTrue(subject.containsKey("c"));
    assertTrue(subject.containsKey("d"));
    assertTrue(subject.containsKey("e"));
    assertTrue(subject.containsKey("f"));
    assertFalse(subject.containsKey("venom"));
  }

  @Test
  public void getBlob_positive_nonNull() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putBlob("key", new byte[] { 1 });

    assertEquals(1, subject.getBlob("key", null).length);
    assertEquals(1, subject.getBlob("key", null)[0]);
    assertEquals(byte[].class, subject.getType("key"));
  }

  @Test
  public void getBlob_positive_null() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putBlob("key", null);

    assertNull(subject.getBlob("key", new byte[0]));
    assertEquals(byte[].class, subject.getType("key"));
  }

  @Test
  public void getBlob_default() {
    KeyValueDataSet subject = new KeyValueDataSet();
    byte[]          value   = subject.getBlob("key", new byte[]{1});

    assertEquals(1, value.length);
    assertEquals(1, value[0]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getBlob_negative() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putBlob("key", new byte[0]);

    subject.getInteger("key", 0);
  }

  @Test
  public void getBoolean_positive() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putBoolean("key", true);

    assertTrue(subject.getBoolean("key", false));
    assertEquals(Boolean.class, subject.getType("key"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getBoolean_negative() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putBoolean("key", true);

    subject.getInteger("key", 0);
  }

  @Test
  public void getBoolean_default() {
    KeyValueDataSet subject = new KeyValueDataSet();
    assertTrue(subject.getBoolean("key", true));
  }

  @Test
  public void getFloat_positive() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putFloat("key", 1.1f);

    assertEquals(1.1f, subject.getFloat("key", 0), 0.1f);
    assertEquals(Float.class, subject.getType("key"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFloat_negative() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putFloat("key", 1.1f);

    subject.getInteger("key", 0);
  }

  @Test
  public void getFloat_default() {
    KeyValueDataSet subject = new KeyValueDataSet();
    assertEquals(1.1f, subject.getFloat("key", 1.1f), 0.1f);
  }

  @Test
  public void getInteger_positive() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putInteger("key", 1);

    assertEquals(1, subject.getInteger("key", 0));
    assertEquals(Integer.class, subject.getType("key"));
  }

  @Test
  public void getInteger_default() {
    KeyValueDataSet subject = new KeyValueDataSet();
    assertEquals(1, subject.getInteger("key", 1));
  }

  @Test
  public void getLong_positive() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putLong("key", 1);

    assertEquals(1, subject.getLong("key", 0));
    assertEquals(Long.class, subject.getType("key"));
  }

  @Test
  public void getLong_default() {
    KeyValueDataSet subject = new KeyValueDataSet();
    assertEquals(1, subject.getLong("key", 1));
  }

  @Test
  public void getInteger_storedAsLong() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putLong("key", 1);
    assertEquals(1, subject.getInteger("key", 1));
  }

  @Test(expected = ArithmeticException.class)
  public void getInteger_storedAsLongAndTooLargeForInt() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putLong("key", Long.MAX_VALUE);
    subject.getInteger("key", 1);
  }

  @Test
  public void getLong_storedAsInt() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putInteger("key", 1);
    assertEquals(1, subject.getLong("key", 1));
  }

  @Test
  public void getString_positive_nonNull() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putString("key", "spiderman");

    assertEquals("spiderman", subject.getString("key", null));
    assertEquals(String.class, subject.getType("key"));
  }

  @Test
  public void getString_positive_null() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putString("key", null);

    assertNull(subject.getString("key", "something"));
    assertEquals(String.class, subject.getType("key"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getString_negative() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putString("key", "spiderman");

    subject.getInteger("key", 0);
  }

  @Test
  public void getString_default() {
    KeyValueDataSet subject = new KeyValueDataSet();
    assertEquals("spiderman", subject.getString("key", "spiderman"));
  }

  @Test
  public void remove_generic() {
    KeyValueDataSet subject = new KeyValueDataSet();
    subject.putInteger("key", 1);

    assertTrue(subject.containsKey("key"));

    subject.removeAll(Collections.singletonList("key"));

    assertFalse(subject.containsKey("key"));
  }
}
