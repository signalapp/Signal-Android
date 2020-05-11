package org.whispersystems.signalservice.api.kbs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class MasterKeyTest {

  @Test(expected = AssertionError.class)
  public void wrong_length_too_short() {
    new MasterKey(new byte[31]);
  }

  @Test(expected = AssertionError.class)
  public void wrong_length_too_long() {
    new MasterKey(new byte[33]);
  }

  @Test(expected = NullPointerException.class)
  public void invalid_input_null() {
    //noinspection ConstantConditions
    new MasterKey(null);
  }

  @Test
  public void equality() {
    byte[]    masterKeyBytes1 = new byte[32];
    byte[]    masterKeyBytes2 = new byte[32];
    MasterKey masterKey1      = new MasterKey(masterKeyBytes1);
    MasterKey masterKey2      = new MasterKey(masterKeyBytes2);

    assertEquals(masterKey1, masterKey2);
    assertEquals(masterKey1.hashCode(), masterKey2.hashCode());
  }

  @Test
  public void in_equality() {
    byte[] masterKeyBytes1 = new byte[32];
    byte[] masterKeyBytes2 = new byte[32];

    masterKeyBytes1[0] = 1;

    MasterKey masterKey1 = new MasterKey(masterKeyBytes1);
    MasterKey masterKey2 = new MasterKey(masterKeyBytes2);

    assertNotEquals(masterKey1, masterKey2);
    assertNotEquals(masterKey1.hashCode(), masterKey2.hashCode());
  }
}
