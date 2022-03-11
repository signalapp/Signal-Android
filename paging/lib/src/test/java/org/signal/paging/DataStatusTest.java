package org.signal.paging;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DataStatusTest {

  @Test
  public void insertState_initiallyEmpty_InsertAtZero() {
    DataStatus subject = DataStatus.obtain(0);
    subject.insertState(0, true);

    assertEquals(1, subject.size());
    assertTrue(subject.get(0));
  }

  @Test
  public void insertState_someData_InsertAtZero() {
    DataStatus subject = DataStatus.obtain(2);
    subject.mark(1);

    subject.insertState(0, true);

    assertEquals(3, subject.size());
    assertTrue(subject.get(0));
    assertFalse(subject.get(1));
    assertTrue(subject.get(2));
  }

  @Test
  public void insertState_someData_InsertAtOne() {
    DataStatus subject = DataStatus.obtain(3);
    subject.mark(1);

    subject.insertState(1, true);

    assertEquals(4, subject.size());
    assertFalse(subject.get(0));
    assertTrue(subject.get(1));
    assertTrue(subject.get(2));
    assertFalse(subject.get(3));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void insertState_negativeThrows() {
    DataStatus subject = DataStatus.obtain(0);
    subject.insertState(-1, true);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void insertState_largerThanSizePlusOneThrows() {
    DataStatus subject = DataStatus.obtain(0);
    subject.insertState(2, true);
  }
}
